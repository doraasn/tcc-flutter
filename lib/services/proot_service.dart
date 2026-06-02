import 'dart:io';
import 'package:path/path.dart' as p;
import '../core/constants.dart';

class PRootService {
  static const _alpineVersion = '3.19';
  static const _alpineArch = 'aarch64';
  static const _alpineUrl = 'https://dl-cdn.alpinelinux.org/alpine/v$_alpineVersion/releases/$_alpineArch/alpine-minirootfs-$_alpineVersion.0-$_alpineArch.tar.gz';

  Future<void> initializeRootfs() async {
    final rootfs = await TccPaths.rootfs;
    final rootfsDir = Directory(rootfs);

    if (await rootfsDir.exists()) {
      return;
    }

    await _downloadAndExtractRootfs(rootfs);
    await _installNodeJs(rootfs);
    await _installClaudeCode(rootfs);
  }

  Future<void> _downloadAndExtractRootfs(String rootfs) async {
    final tmpDir = Directory.systemTemp.createTempSync('tcc_rootfs_');
    final tarball = File(p.join(tmpDir.path, 'rootfs.tar.gz'));

    try {
      final client = HttpClient();
      final request = await client.getUrl(Uri.parse(_alpineUrl));
      final response = await request.close();
      await response.pipe(tarball.openWrite());

      final result = await Process.run('tar', [
        'xzf', tarball.path,
        '-C', rootfs,
        '--strip-components=0',
      ]);

      if (result.exitCode != 0) {
        throw Exception('Failed to extract rootfs: ${result.stderr}');
      }
    } finally {
      tmpDir.deleteSync(recursive: true);
    }
  }

  Future<void> _installNodeJs(String rootfs) async {
    final result = await _runInRootfs('apk add --no-cache nodejs npm');
    if (result.exitCode != 0) {
      throw Exception('Failed to install Node.js: ${result.stderr}');
    }
  }

  Future<void> _installClaudeCode(String rootfs) async {
    final versionDir = await TccPaths.currentVersion;
    final claudeDir = Directory(versionDir);
    if (!await claudeDir.exists()) {
      await claudeDir.create(recursive: true);
    }

    final result = await _runInRootfs(
      'npm install -g @anthropic-ai/claude-code@2.1.153 --prefix $versionDir',
    );
    if (result.exitCode != 0) {
      throw Exception('Failed to install Claude Code: ${result.stderr}');
    }
  }

  Future<ProcessResult> _runInRootfs(String command) async {
    final proot = await _findProot();
    final rootfs = await TccPaths.rootfs;

    return await Process.run(proot, [
      '-r', rootfs,
      '-b', '/dev',
      '-b', '/proc',
      '-b', '/sys',
      '-w', '/root',
      '/bin/sh', '-c', command,
    ]);
  }

  Future<String> _findProot() async {
    final prootPaths = [
      '/data/data/com.termux/files/usr/bin/proot',
      '/system/bin/proot',
    ];

    for (final path in prootPaths) {
      if (await File(path).exists()) {
        return path;
      }
    }

    throw Exception('proot not found. Install with: pkg install proot');
  }

  Future<void> cleanup() async {
    final rootfs = await TccPaths.rootfs;
    final dir = Directory(rootfs);
    if (await dir.exists()) {
      await dir.delete(recursive: true);
    }
  }
}
