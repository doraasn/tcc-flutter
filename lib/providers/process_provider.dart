import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/constants.dart';
import '../core/ndjson_parser.dart';
import '../models/workspace_state.dart';
import '../models/chat_message.dart';
import '../services/proot_service.dart';

/// ---------------------------------------------------------------------------
/// Providers
/// ---------------------------------------------------------------------------

/// Process lifecycle state.
final processProvider =
    StateNotifierProvider<ProcessController, ProcessState>((ref) {
  return ProcessController(ref);
});

/// Convenience getter: is the Claude process alive?
final processRunningProvider = Provider<bool>((ref) {
  return ref.watch(processProvider).isRunning;
});

/// ---------------------------------------------------------------------------
/// Controller
/// ---------------------------------------------------------------------------

class ProcessController extends StateNotifier<ProcessState> {
  ProcessController(this._ref) : super(const ProcessState());

  final Ref _ref;

  final PRootService _prootService = PRootService();
  NdjsonParser? _parser;
  StreamSubscription<NdjsonChunk>? _parserSub;
  Process? _process;
  StreamSubscription<String>? _stdoutSub;
  StreamSubscription<String>? _stderrSub;
  IOSink? _stdinSink;

  bool get isRunning => state.isRunning;

  /// One-time initialisation (extracts rootfs if needed).
  Future<void> initialize() async {
    await _prootService.initialize();
  }

  // ---------------------------------------------------------------------------
  // Start
  // ---------------------------------------------------------------------------

  /// Start a Claude Code session via proot.
  ///
  /// [projectId]  – the workspace project identifier.
  /// [cwd]        – working directory inside the host.
  /// [resumeId]   – optional session id to resume.
  /// [prompt]     – optional initial user prompt (--print).
  /// [env]        – extra environment variables injected into the process.
  Future<void> start({
    required String projectId,
    required String cwd,
    String? resumeId,
    String? prompt,
    Map<String, String>? env,
  }) async {
    if (isRunning) kill();

    state = const ProcessState(isStarting: true);
    _parser = NdjsonParser();

    try {
      await _prootService.initialize();
      final proot = await _prootService.findProot();
      final rootfs = await TccPaths.rootfs;

      final args = <String>[
        '-r', rootfs,
        '-b', '/dev',
        '-b', '/proc',
        '-b', '/sys',
        '-w', '/root',
        '/root/.tcc/versions/current/bin/claude',
        '--output-format', 'stream-json',
        '--dangerously-skip-permissions',
      ];

      if (resumeId != null) {
        args.addAll(['--resume', resumeId]);
      } else {
        args.addAll(['--continue']);
      }

      if (prompt != null && prompt.isNotEmpty) {
        args.addAll(['--print', prompt]);
      }

      final environment = await _buildEnvironment(env);

      _process = await Process.start(
        proot,
        args,
        workingDirectory: cwd,
        environment: environment,
      );

      _stdinSink = _process!.stdin;

      _stdoutSub = _process!.stdout
          .transform(utf8.decoder)
          .listen(_onData);

      _parserSub = _parser!.stream.listen(_onChunk);

      _stderrSub = _process!.stderr
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(_onStderr);

      _process!.exitCode.then(_onExit);

      state = ProcessState(
        isRunning: true,
        sessionId: resumeId,
      );
    } catch (e) {
      state = ProcessState(error: e.toString());
    }
  }

  // ---------------------------------------------------------------------------
  // Stream parsing
  // ---------------------------------------------------------------------------

  /// Feeds raw UTF-8 text into the NDJSON parser which handles partial lines.
  void _onData(String data) {
    _parser?.feed(data);
  }

  /// Dispatches fully-parsed chunks from the [NdjsonParser] stream.
  void _onChunk(NdjsonChunk chunk) {
    switch (chunk.type) {
      case 'assistant':
        _handleAssistantChunk(chunk);
        break;
      case 'result':
        _handleResult(chunk);
        break;
      case 'error':
        _handleError(chunk);
        break;
      case 'system':
        _handleSystem(chunk);
        break;
      default:
        break;
    }
  }

  void _onStderr(String line) {
    // Handle stderr output if needed
  }

  void _handleAssistantChunk(NdjsonChunk chunk) {
    final content = chunk.content ?? '';
    if (content.isEmpty) return;

    final messages = _readMessages();

    if (messages.isNotEmpty &&
        messages.last.role == 'assistant' &&
        messages.last.isStreaming) {
      final last = messages.last;
      messages[messages.length - 1] =
          last.copyWith(content: last.content + content);
    } else {
      messages.add(ChatMessage(
        id: DateTime.now().microsecondsSinceEpoch.toString(),
        role: 'assistant',
        content: content,
        timestamp: DateTime.now(),
        isStreaming: true,
      ));
    }
    _writeMessages(messages);
  }

  void _handleResult(NdjsonChunk chunk) {
    final messages = _readMessages();
    if (messages.isNotEmpty && messages.last.isStreaming) {
      messages[messages.length - 1] =
          messages.last.copyWith(isStreaming: false);
    }
    _writeMessages(messages);

    if (chunk.sessionId != null) {
      state = state.copyWith(sessionId: chunk.sessionId);
    }
  }

  void _handleError(NdjsonChunk chunk) {
    final error = chunk.error ?? 'Unknown error';
    final messages = _readMessages();
    messages.add(ChatMessage(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      role: 'error',
      content: error,
      timestamp: DateTime.now(),
      error: error,
    ));
    _writeMessages(messages);
    state = state.copyWith(error: error);
  }

  void _handleSystem(NdjsonChunk chunk) {
    // Extract the message text from the chunk payload.
    String? message;
    if (chunk.data is Map<String, dynamic>) {
      message = (chunk.data as Map<String, dynamic>)['message'] as String?;
    } else if (chunk.data is String) {
      message = chunk.data as String;
    }
    if (message == null || message.isEmpty) return;

    final metadata = <String, dynamic>{'type': chunk.type};
    if (chunk.data is Map<String, dynamic>) {
      metadata.addAll(chunk.data as Map<String, dynamic>);
    }
    if (chunk.sessionId != null) {
      metadata['session_id'] = chunk.sessionId;
    }

    final messages = _readMessages();
    messages.add(ChatMessage(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      role: 'system',
      content: message,
      timestamp: DateTime.now(),
      metadata: metadata,
    ));
    _writeMessages(messages);
  }

  void _onExit(int code) {
    if (isRunning) {
      // Mark the last streaming message as finalised.
      final messages = _readMessages();
      if (messages.isNotEmpty && messages.last.isStreaming) {
        messages[messages.length - 1] =
            messages.last.copyWith(isStreaming: false);
        _writeMessages(messages);
      }
      state = state.copyWith(isRunning: false, sessionId: null);
    }

    _parser?.close();
    _cleanupStreams();
  }

  // ---------------------------------------------------------------------------
  // stdin
  // ---------------------------------------------------------------------------

  /// Send a line of text to the running Claude process via stdin.
  Future<void> sendInput(String text) async {
    if (!isRunning || _stdinSink == null) return;

    // Record the user message in chat.
    final messages = _readMessages();
    messages.add(ChatMessage(
      id: DateTime.now().microsecondsSinceEpoch.toString(),
      role: 'user',
      content: text,
      timestamp: DateTime.now(),
    ));
    _writeMessages(messages);

    _stdinSink!.writeln(text);
  }

  // ---------------------------------------------------------------------------
  // Kill / cleanup
  // ---------------------------------------------------------------------------

  void kill() {
    _process?.kill(ProcessSignal.sigterm);
    _cleanupStreams();
    _parser?.close();
    _process = null;
    _stdinSink = null;
    _parser = null;
    state = const ProcessState();
  }

  void clearSession() {
    kill();
    _writeMessages([]);
  }

  void _cleanupStreams() {
    _stdoutSub?.cancel();
    _stderrSub?.cancel();
    _parserSub?.cancel();
    _stdoutSub = null;
    _stderrSub = null;
    _parserSub = null;
  }

  // ---------------------------------------------------------------------------
  // Hot-switch model
  // ---------------------------------------------------------------------------

  /// Kill the current process and restart with different model env vars,
  /// resuming the same session if one existed.
  Future<void> hotSwitch({
    required String projectId,
    required String cwd,
    required String baseUrl,
    required String apiKey,
    required String modelId,
  }) async {
    final resumeId = state.sessionId;
    kill();

    final env = <String, String>{
      'ANTHROPIC_BASE_URL': baseUrl,
      'ANTHROPIC_AUTH_TOKEN': apiKey,
      'ANTHROPIC_MODEL': modelId,
      'ANTHROPIC_DEFAULT_HAIKU_MODEL': modelId,
      'ANTHROPIC_DEFAULT_SONNET_MODEL': modelId,
      'ANTHROPIC_DEFAULT_OPUS_MODEL': modelId,
      'ANTHROPIC_DEFAULT_SONNET_MODEL_NAME': modelId,
      'ANTHROPIC_DEFAULT_OPUS_MODEL_NAME': modelId,
      'CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC': '1',
      'DISABLE_AUTOUPDATER': '1',
    };

    await start(
      projectId: projectId,
      cwd: cwd,
      resumeId: resumeId,
      env: env,
    );
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  List<ChatMessage> _readMessages() {
    return List<ChatMessage>.from(
      _ref.read(chatMessagesProvider),
    );
  }

  void _writeMessages(List<ChatMessage> messages) {
    _ref.read(chatMessagesProvider.notifier).state = messages;
  }

  Future<Map<String, String>> _buildEnvironment(Map<String, String>? extra) async {
    final env = Map<String, String>.from(Platform.environment);

    env['HOME'] = '/root';
    env['USER'] = 'root';
    env['TERM'] = 'xterm-256color';
    env['SHELL'] = '/bin/bash';
    env['PATH'] =
        '/root/.tcc/versions/current/bin:/usr/bin:/bin:/usr/local/bin';
    env['LD_LIBRARY_PATH'] = '/usr/lib:/lib';
    env['NODE_PATH'] =
        '/root/.tcc/versions/current/lib/node_modules';
    env['TCC_VERSION'] = '1.0.0';
    env['TCC_WORKSPACE'] = '/root/workspace';

    if (extra != null) {
      env.addAll(extra);
    }

    return env;
  }
}

/// The raw chat message list that the process writes into.
final chatMessagesProvider = StateProvider<List<ChatMessage>>((ref) => []);
