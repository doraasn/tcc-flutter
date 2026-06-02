import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/constants.dart';
import '../core/ndjson_parser.dart';
import '../models/chat_message.dart';
import '../providers/process_provider.dart';
import 'proot_service.dart';

class ClaudeService {
  final PRootService _prootService;
  Process? _process;
  NdjsonParser? _parser;

  ClaudeService(this._prootService);

  Future<void> startSession({
    required String projectId,
    String? resumeId,
    String? prompt,
    Map<String, String>? env,
  }) async {
    await kill();

    final rootfs = await TccPaths.rootfs;
    final proot = await _prootService._findProot();

    final args = [
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
      workingDirectory: await TccPaths.projectDir(projectId),
      environment: environment,
    );

    _parser = NdjsonParser();

    _process!.stdout
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .listen((line) => _parser?.feed(line + '\n'));

    _process!.stderr
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .listen((_) {});
  }

  Future<void> sendInput(String text) async {
    _process?.stdin.writeln(text);
  }

  void kill() {
    _process?.kill(ProcessSignal.sigterm);
    _process = null;
    _parser?.close();
    _parser = null;
  }

  Stream<NdjsonChunk> get stream => _parser?.stream ?? const Stream.empty();

  Future<Map<String, String>> _buildEnvironment(Map<String, String>? extra) async {
    final env = Map<String, String>.from(Platform.environment);

    final rootfs = await TccPaths.rootfs;
    env['HOME'] = '$rootfs/root';
    env['USER'] = 'root';
    env['TERM'] = 'xterm-256color';
    env['SHELL'] = '/bin/bash';
    env['PATH'] = '$rootfs/root/.tcc/versions/current/bin:$rootfs/usr/bin:$rootfs/bin:/usr/local/bin:/usr/bin:/bin';
    env['LD_LIBRARY_PATH'] = '$rootfs/usr/lib:$rootfs/lib';
    env['NODE_PATH'] = '$rootfs/root/.tcc/versions/current/lib/node_modules';

    if (extra != null) {
      env.addAll(extra);
    }

    return env;
  }
}

final claudeServiceProvider = Provider<ClaudeService>((ref) {
  return ClaudeService(PRootService());
});
