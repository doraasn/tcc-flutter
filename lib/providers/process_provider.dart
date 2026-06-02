import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/constants.dart';
import '../core/ndjson_parser.dart';
import '../models/workspace_state.dart';
import '../models/chat_message.dart';

final processProvider = StateNotifierProvider<ProcessController, ProcessState>((ref) {
  return ProcessController();
});

final chatMessagesProvider = StateProvider<List<ChatMessage>>((ref) => []);

class ProcessController extends StateNotifier<ProcessState> {
  ProcessController() : super(const ProcessState());

  Process? _process;
  NdjsonParser? _parser;
  StreamSubscription<String>? _stdoutSub;
  StreamSubscription<String>? _stderrSub;

  bool get isRunning => state.isRunning;

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
      final ccPath = await TccPaths.currentCcBinary;
      final args = [
        '--output-format', 'stream-json',
        '--dangerously-skip-permissions',
        '--max-turns', '1',
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
        ccPath,
        args,
        workingDirectory: cwd,
        environment: environment,
        mode: ProcessStartMode.inheritStdio,
      );

      state = ProcessState(
        isRunning: true,
        sessionId: resumeId,
      );

      _stdoutSub = _process!.stdout
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(_onStdout);

      _stderrSub = _process!.stderr
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .listen(_onStderr);

      _process!.exitCode.then((code) {
        if (state.isRunning) {
          state = state.copyWith(isRunning: false, sessionId: null);
        }
      });
    } catch (e) {
      state = ProcessState(error: e.toString());
    }
  }

  void _onStdout(String line) {
    _parser?.feed(line + '\n');
    try {
      final json = jsonDecode(line) as Map<String, dynamic>;
      final type = json['type'] as String?;

      if (type == 'assistant' && json['content'] != null) {
        final content = json['content'] as String;
        final messages = List<ChatMessage>.from(
          ref.read(chatMessagesProvider),
        );

        if (messages.isNotEmpty && messages.last.role == 'assistant' && messages.last.isStreaming) {
          final last = messages.last;
          messages[messages.length - 1] = last.copyWith(content: last.content + content);
        } else {
          messages.add(ChatMessage(
            id: DateTime.now().millisecondsSinceEpoch.toString(),
            role: 'assistant',
            content: content,
            timestamp: DateTime.now(),
            isStreaming: true,
          ));
        }
        ref.read(chatMessagesProvider.notifier).state = messages;
      } else if (type == 'result') {
        final messages = List<ChatMessage>.from(
          ref.read(chatMessagesProvider),
        );
        if (messages.isNotEmpty && messages.last.isStreaming) {
          messages[messages.length - 1] = messages.last.copyWith(isStreaming: false);
        }
        ref.read(chatMessagesProvider.notifier).state = messages;

        final sessionId = json['session_id'] as String?;
        if (sessionId != null) {
          state = state.copyWith(sessionId: sessionId);
        }
      } else if (type == 'error') {
        final error = json['error'] as String? ?? 'Unknown error';
        final messages = List<ChatMessage>.from(
          ref.read(chatMessagesProvider),
        );
        messages.add(ChatMessage(
          id: DateTime.now().millisecondsSinceEpoch.toString(),
          role: 'error',
          content: error,
          timestamp: DateTime.now(),
          error: error,
        ));
        ref.read(chatMessagesProvider.notifier).state = messages;
      }
    } catch (_) {}
  }

  void _onStderr(String line) {}

  Future<void> sendInput(String text) async {
    if (_process == null || !isRunning) return;

    final messages = List<ChatMessage>.from(
      ref.read(chatMessagesProvider),
    );
    messages.add(ChatMessage(
      id: DateTime.now().millisecondsSinceEpoch.toString(),
      role: 'user',
      content: text,
      timestamp: DateTime.now(),
    ));
    ref.read(chatMessagesProvider.notifier).state = messages;

    _process!.stdin.writeln(text);
  }

  void kill() {
    _stdoutSub?.cancel();
    _stderrSub?.cancel();
    _process?.kill(ProcessSignal.sigterm);
    _process = null;
    _parser?.close();
    _parser = null;
    state = const ProcessState();
  }

  void clearSession() {
    kill();
    ref.read(chatMessagesProvider.notifier).state = [];
  }

  Future<Map<String, String>> _buildEnvironment(Map<String, String>? extra) async {
    final env = Map<String, String>.from(Platform.environment);

    final rootfs = await TccPaths.rootfs;
    env['HOME'] = '$rootfs/root';
    env['USER'] = 'root';
    env['TERM'] = 'xterm-256color';
    env['SHELL'] = '/bin/bash';
    env['PATH'] = '$rootfs/usr/bin:$rootfs/bin:/usr/local/bin:/usr/bin:/bin';
    env['LD_LIBRARY_PATH'] = '$rootfs/usr/lib:$rootfs/lib';

    if (extra != null) {
      env.addAll(extra);
    }

    return env;
  }

  Future<void> hotSwitch({
    required String projectId,
    required String cwd,
    required String baseUrl,
    required String apiKey,
    required String modelId,
  }) async {
    final resumeId = state.sessionId;
    kill();

    final env = {
      'ANTHROPIC_BASE_URL': baseUrl,
      'ANTHROPIC_API_KEY': apiKey,
      'ANTHROPIC_AUTH_TOKEN': apiKey,
      'CLAUDE_MODEL': modelId,
    };

    await start(
      projectId: projectId,
      cwd: cwd,
      resumeId: resumeId,
      env: env,
    );
  }
}
