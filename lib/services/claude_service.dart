import 'dart:async';
import 'dart:convert';
import 'dart:io';

import '../core/constants.dart';
import '../core/ndjson_parser.dart';
import 'proot_service.dart';
import 'version_manager.dart';

/// Event emitted when the Claude Code process produces output.
class ClaudeEvent {
  final NdjsonChunk chunk;
  final DateTime timestamp;

  const ClaudeEvent({required this.chunk, required this.timestamp});

  factory ClaudeEvent.fromChunk(NdjsonChunk chunk) =>
      ClaudeEvent(chunk: chunk, timestamp: DateTime.now());
}

/// Manages the lifecycle of a Claude Code process running inside proot.
///
/// Responsibilities:
/// - Start Claude Code via proot with the full set of required arguments.
/// - NDJSON stream parsing with proper buffer management.
/// - stdin/stdout pipe management (send input, close streams).
/// - Session resume (`--resume <id>`) and continue (`--continue`).
/// - Hot-swap model by killing the process and restarting with new env vars.
/// - Environment variable injection (`ANTHROPIC_BASE_URL`, `API_KEY`, `MODEL`).
class ClaudeService {
  final PRootService _proot;
  final VersionManager _versionManager;

  // ---------------------------------------------------------------------------
  // Process state
  // ---------------------------------------------------------------------------

  Process? _process;
  NdjsonParser? _parser;
  StreamSubscription<String>? _stdoutSub;
  StreamSubscription<String>? _stderrSub;

  String? _activeSessionId;
  String? _activeProjectId;
  Map<String, String>? _lastEnvironment;

  /// True when a Claude Code child process is alive.
  bool get isRunning => _process != null;

  /// The session ID returned by the most recent "result" event, if any.
  String? get activeSessionId => _activeSessionId;

  /// The project ID this instance was last started for.
  String? get activeProjectId => _activeProjectId;

  // ---------------------------------------------------------------------------
  // Events
  // ---------------------------------------------------------------------------

  final StreamController<ClaudeEvent> _eventController =
      StreamController<ClaudeEvent>.broadcast();

  /// Broadcast stream of all NDJSON events from the running process.
  ///
  /// Listeners receive a [ClaudeEvent] wrapping the raw [NdjsonChunk] plus a
  /// timestamp.  Safe to listen from multiple locations.
  Stream<ClaudeEvent> get events => _eventController.stream;

  /// Convenience: stream of only "assistant" content events.
  Stream<String> get assistantContent => events
      .where((e) => e.chunk.type == 'assistant')
      .map((e) => e.chunk.content ?? '')
      .where((s) => s.isNotEmpty);

  /// Convenience: stream of "result" events (sent when a turn finishes).
  Stream<NdjsonChunk> get results =>
      events.where((e) => e.chunk.type == 'result');

  /// Convenience: stream of "error" events.
  Stream<NdjsonChunk> get errors =>
      events.where((e) => e.chunk.type == 'error' || e.chunk.error != null);

  // ---------------------------------------------------------------------------
  // Constructor
  // ---------------------------------------------------------------------------

  ClaudeService({
    PRootService? proot,
    VersionManager? versionManager,
  })  : _proot = proot ?? PRootService(),
        _versionManager = versionManager ?? VersionManager();

  // ---------------------------------------------------------------------------
  // Start session
  // ---------------------------------------------------------------------------

  /// Starts a new Claude Code session via proot.
  ///
  /// [projectId] selects the working directory under the workspace.
  /// [resumeId] resumes a previous session (mutually exclusive with --continue).
  /// [prompt] sends an initial message via `--print`.
  /// [env] merges extra environment variables (e.g. API key, base URL, model).
  Future<void> startSession({
    required String projectId,
    String? resumeId,
    bool continueSession = true,
    String? prompt,
    Map<String, String>? env,
  }) async {
    // Kill any existing process first.
    await kill();

    final proot = await _proot.findProot();
    final rootfs = await TccPaths.rootfs;
    final cwd = await TccPaths.projectDir(projectId);

    // Ensure the project directory exists.
    await Directory(cwd).create(recursive: true);

    // Build proot arguments.
    final args = _buildProotArgs(
      rootfs: rootfs,
      projectId: projectId,
      resumeId: resumeId,
      continueSession: continueSession,
      prompt: prompt,
    );

    // Build environment.
    final environment = await _buildEnvironment(extra: env);

    // Start the child process.
    _process = await Process.start(
      proot,
      args,
      workingDirectory: cwd,
      environment: environment,
    );

    _activeProjectId = projectId;
    _lastEnvironment = environment;

    // Set up parsers and stream listeners.
    _parser = NdjsonParser();

    _stdoutSub = _process!.stdout
        .transform(utf8.decoder)
        .listen(_onStdoutData);

    _stderrSub = _process!.stderr
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .listen(_onStderrLine);

    // Watch for process exit.
    _process!.exitCode.then((code) {
      if (code != 0 && isRunning) {
        _eventController.add(ClaudeEvent.fromChunk(NdjsonChunk(
          type: 'process_exit',
          data: {'exitCode': code},
        )));
      }
      _process = null;
    });
  }

  // ---------------------------------------------------------------------------
  // stdin management
  // ---------------------------------------------------------------------------

  /// Sends a line of text to the running Claude Code process via stdin.
  ///
  /// Throws a [StateError] if no process is running.
  Future<void> sendInput(String text) async {
    if (_process == null) {
      throw StateError('No Claude Code process is running.');
    }
    _process!.stdin.writeln(text);
  }

  /// Closes stdin of the running process, signaling EOF.
  ///
  /// This is useful when the process is waiting for more input and you want to
  /// let it finish processing whatever it has.
  Future<void> closeStdin() async {
    await _process?.stdin.close();
  }

  // ---------------------------------------------------------------------------
  // Kill / cleanup
  // ---------------------------------------------------------------------------

  /// Terminates the running process and cleans up streams.
  Future<void> kill() async {
    // Cancel stream subscriptions.
    await _stdoutSub?.cancel();
    await _stderrSub?.cancel();
    _stdoutSub = null;
    _stderrSub = null;

    // Terminate process.
    if (_process != null) {
      _process!.kill(ProcessSignal.sigterm);

      // Give it a moment to exit gracefully, then force-kill.
      await Future.delayed(const Duration(milliseconds: 200));
      if (_process != null) {
        _process!.kill(ProcessSignal.sigkill);
      }
    }

    // Close parser.
    _parser?.close();
    _parser = null;

    _process = null;
  }

  /// Kills the process and resets all state including session ID.
  Future<void> reset() async {
    await kill();
    _activeSessionId = null;
    _activeProjectId = null;
    _lastEnvironment = null;
  }

  // ---------------------------------------------------------------------------
  // Hot-swap
  // ---------------------------------------------------------------------------

  /// Hot-swaps the model by killing the current process and restarting with
  /// new environment variables.
  ///
  /// The current session is resumed if available.
  ///
  /// [baseUrl] overrides `ANTHROPIC_BASE_URL`.
  /// [apiKey] overrides `ANTHROPIC_API_KEY`.
  /// [modelId] overrides `CLAUDE_MODEL`.
  Future<void> hotSwapModel({
    required String projectId,
    required String cwd,
    required String baseUrl,
    required String apiKey,
    required String modelId,
  }) async {
    final savedSessionId = _activeSessionId;
    await kill();

    final env = <String, String>{
      if (baseUrl.isNotEmpty) 'ANTHROPIC_BASE_URL': baseUrl,
      if (apiKey.isNotEmpty) 'ANTHROPIC_API_KEY': apiKey,
      if (apiKey.isNotEmpty) 'ANTHROPIC_AUTH_TOKEN': apiKey,
      if (modelId.isNotEmpty) 'CLAUDE_MODEL': modelId,
    };

    await startSession(
      projectId: projectId,
      resumeId: savedSessionId,
      continueSession: savedSessionId == null,
      env: env,
    );
  }

  // ---------------------------------------------------------------------------
  // Process argument building
  // ---------------------------------------------------------------------------

  List<String> _buildProotArgs({
    required String rootfs,
    required String projectId,
    String? resumeId,
    bool continueSession = true,
    String? prompt,
  }) {
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

    if (resumeId != null && resumeId.isNotEmpty) {
      args.addAll(['--resume', resumeId]);
    } else if (continueSession) {
      args.add('--continue');
    }

    if (prompt != null && prompt.isNotEmpty) {
      args.addAll(['--print', prompt]);
    }

    return args;
  }

  // ---------------------------------------------------------------------------
  // Environment variable building
  // ---------------------------------------------------------------------------

  /// Builds the environment map for the child process.
  ///
  /// Starts from `Platform.environment`, overlays TCC-required variables, then
  /// merges [extra] on top (allowing callers to inject API keys, model
  /// overrides, etc.).
  Future<Map<String, String>> _buildEnvironment({
    Map<String, String>? extra,
  }) async {
    final env = Map<String, String>.from(Platform.environment);

    // Core proot/Alpine environment.
    env['HOME'] = '/root';
    env['USER'] = 'root';
    env['TERM'] = 'xterm-256color';
    env['SHELL'] = '/bin/bash';
    env['PATH'] =
        '/root/.tcc/versions/current/bin:/usr/bin:/bin:/usr/local/bin';
    env['LD_LIBRARY_PATH'] = '/usr/lib:/lib';
    env['NODE_PATH'] =
        '/root/.tcc/versions/current/lib/node_modules';

    // TCC metadata.
    env['TCC_VERSION'] = TccPaths.tccVersion;
    env['TCC_WORKSPACE'] = '/root/workspace';

    // Merge caller-supplied overrides last (they take precedence).
    if (extra != null) {
      env.addAll(extra);
    }

    return env;
  }

  // ---------------------------------------------------------------------------
  // Stream handlers
  // ---------------------------------------------------------------------------

  /// Called whenever raw bytes arrive on the child process stdout.
  ///
  /// We feed the decoded text into the NDJSON parser, which handles line
  /// splitting, buffering, and JSON decoding.
  void _onStdoutData(String data) {
    _parser?.feed(data);

    // Also emit directly as ClaudeEvents for real-time consumption.
    // We parse the raw line here to avoid double-decoding.
    final lines = data.split('\n');
    for (final line in lines) {
      final trimmed = line.trim();
      if (trimmed.isEmpty) continue;

      try {
        final json = jsonDecode(trimmed) as Map<String, dynamic>;
        final chunk = NdjsonChunk.fromJson(json, raw: trimmed);
        _eventController.add(ClaudeEvent.fromChunk(chunk));

        // Track session ID from result events.
        if (chunk.type == 'result' && chunk.sessionId != null) {
          _activeSessionId = chunk.sessionId;
        }
      } on FormatException {
        // Incomplete line or non-JSON; the parser handles buffering.
      } catch (_) {
        // Silently ignore unexpected parse errors in real-time handler.
      }
    }
  }

  /// Called for each stderr line.  We currently swallow stderr but keep the
  /// hook for future logging/debugging.
  void _onStderrLine(String line) {
    // Stderr from Claude Code is typically informational (e.g. "Thinking...
    //").  We could emit these as events if needed.
  }

  // ---------------------------------------------------------------------------
  // Convenience
  // ---------------------------------------------------------------------------

  /// Returns the raw NDJSON parser stream (if you need lower-level access than
  /// [events]).
  Stream<NdjsonChunk> get parserStream => _parser?.stream ?? const Stream.empty();

  /// Builds and returns the environment that would be used for the next
  /// [startSession] call.  Useful for UI display of current config.
  Future<Map<String, String>> previewEnvironment({
    Map<String, String>? extra,
  }) async =>
      _buildEnvironment(extra: extra);
}
