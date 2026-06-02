import 'dart:io';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;
import '../core/constants.dart';

class PRootService {
  static const _alpineVersion = '3.19';
  static const _alpineArch = 'aarch64';
  static const _alpineUrl = 'https://dl-cdn.alpinelinux.org/alpine/v$_alpineVersion/releases/$_alpineArch/alpine-minirootfs-$_alpineVersion.0-$_alpineArch.tar.gz';

  bool _initialized = false;

  Future<void> initialize() async {
    if (_initialized) return;

    final rootfs = await TccPaths.rootfs;
    final rootfsDir = Directory(rootfs);

    if (!await rootfsDir.exists()) {
      await _extractRootfsFromAssets(rootfs);
    }

    _initialized = true;
  }

  Future<void> _extractRootfsFromAssets(String rootfs) async {
    try {
      final byteData = await rootBundle.load('assets/core/rootfs.tgz');
      final bytes = byteData.buffer.asUint8List();

      final tmpDir = Directory.systemTemp.createTempSync('tcc_rootfs_');
      final tarball = File(p.join(tmpDir.path, 'rootfs.tgz'));

      await tarball.writeAsBytes(bytes);

      final result = await Process.run('tar', [
        'xzf', tarball.path,
        '-C', rootfs,
        '--strip-components=0',
      ]);

      await tmpDir.delete(recursive: true);

      if (result.exitCode != 0) {
        throw Exception('Failed to extract rootfs: ${result.stderr}');
      }
    } catch (e) {
      throw Exception('Failed to initialize rootfs: $e');
    }
  }

  Future<void> downloadAndExtractRootfs() async {
    final rootfs = await TccPaths.rootfs;
    final rootfsDir = Directory(rootfs);

    if (await rootfsDir.exists()) {
      return;
    }

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
      await tmpDir.delete(recursive: true);
    }
  }

  Future<ProcessResult> runInRootfs(String command) async {
    final proot = await findProot();
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

  Future<String> findProot() async {
    // Check app's internal storage first (extracted from APK)
    final appProot = File('${await TccPaths.root}/../proot');
    if (await appProot.exists()) {
      return appProot.path;
    }

    // Check Termux's proot
    const termuxProot = '/data/data/com.termux/files/usr/bin/proot';
    if (await File(termuxProot).exists()) {
      return termuxProot;
    }

    // Check system proot
    const systemProot = '/system/bin/proot';
    if (await File(systemProot).exists()) {
      return systemProot;
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
