import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../core/constants.dart';

const _nativeChannel = MethodChannel('com.tcc.app/native');

/// Manages the proot-based Alpine Linux environment that runs Claude Code
/// inside the Android app sandbox.
class PRootService {
  PRootService._();
  static final PRootService _instance = PRootService._();
  factory PRootService() => _instance;

  bool _initialized = false;
  String? _prootPath;

  /// Optional logging callback.
  void Function(String level, String msg)? log;
  void _info(String msg) => log?.call('INFO', msg);
  void _error(String msg) => log?.call('ERROR', msg);

  bool get isInitialized => _initialized;

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------

  Future<void> initialize() async {
    if (_initialized) return;

    await _ensureProotBinary();
    await _ensureRootfs();
    _setupDns();
    // Node.js and Claude Code are bundled in rootfs by CI — no runtime install needed.

    _initialized = true;
    _info('PRoot service initialized successfully');
  }

  // ---------------------------------------------------------------------------
  // proot binary
  // ---------------------------------------------------------------------------

  Future<void> _ensureProotBinary() async {
    // 1. Termux proot (most reliable).
    const termuxProot = '/data/data/com.termux/files/usr/bin/proot';
    if (await File(termuxProot).exists()) {
      _prootPath = termuxProot;
      _info('Using Termux proot');
      return;
    }

    // 2. Copy proot to /data/local/tmp/tcc/ via Kotlin (allows execution).
    try {
      // First, extract proot from assets to app cache.
      final byteData = await rootBundle.load('assets/core/proot-arm64');
      final bytes = byteData.buffer.asUint8List();
      final cacheFile = File('${Directory.systemTemp.path}/proot_extract');
      await cacheFile.writeAsBytes(bytes, flush: true);
      _info('Extracted proot to cache: ${cacheFile.path} (${bytes.length} bytes)');

      // Use Kotlin to copy to /data/local/tmp/tcc/ and chmod +x.
      final execPath = await _nativeChannel.invokeMethod<String>(
        'copyAndMakeExecutable',
        {'src': cacheFile.path, 'dst': 'proot'},
      );
      if (execPath != null && await File(execPath).exists()) {
        _prootPath = execPath;
        _info('Using executable proot: $execPath');
        return;
      }
    } catch (e) {
      _error('Kotlin copy failed: $e');
    }

    // 3. Fallback: extract to app directory (may fail on newer Android).
    _info('Falling back to app directory proot...');
    final cachedProot = File(await TccPaths.prootBinary);
    final byteData = await rootBundle.load('assets/core/proot-arm64');
    final bytes = byteData.buffer.asUint8List();
    await cachedProot.parent.create(recursive: true);
    await cachedProot.writeAsBytes(bytes, flush: true);
    await Process.run('chmod', ['755', cachedProot.path]);
    _prootPath = cachedProot.path;
    _info('Extracted proot to: ${cachedProot.path}');
  }

  Future<String> findProot() async {
    if (_prootPath != null && await File(_prootPath!).exists()) {
      return _prootPath!;
    }
    await _ensureProotBinary();
    if (_prootPath == null) {
      throw StateError('proot binary not found.');
    }
    return _prootPath!;
  }

  // ---------------------------------------------------------------------------
  // Rootfs
  // ---------------------------------------------------------------------------

  Future<void> _ensureRootfs() async {
    final rootfs = await TccPaths.rootfs;
    final rootfsDir = Directory(rootfs);

    if (await rootfsDir.exists()) {
      // Check for busybox (the actual binary, not a symlink).
      if (await File('$rootfs/bin/busybox').exists()) {
        _info('Rootfs valid (busybox exists)');
        return;
      }
      // Broken rootfs — delete and re-extract.
      _info('Rootfs broken, re-extracting...');
      await rootfsDir.delete(recursive: true);
    }

    await _extractRootfsFromAssets();

    if (!await File('$rootfs/bin/busybox').exists()) {
      throw StateError('Rootfs extraction failed: /bin/busybox missing.');
    }
    _info('Rootfs extracted and validated');
  }

  Future<void> _extractRootfsFromAssets() async {
    final rootfs = await TccPaths.rootfs;
    final tmpDir = Directory.systemTemp.createTempSync('tcc_rootfs_');
    final tarball = File(p.join(tmpDir.path, 'rootfs.tgz'));

    try {
      final byteData = await rootBundle.load(TccPaths.rootfsAsset);
      final bytes = byteData.buffer.asUint8List();
      _info('Rootfs asset: ${bytes.length} bytes');

      if (bytes.length < 1000) {
        throw StateError('rootfs.tgz is too small (${bytes.length} bytes)');
      }

      await tarball.writeAsBytes(bytes, flush: true);
      await Directory(rootfs).create(recursive: true);

      final result = await Process.run('tar', [
        'xzf', tarball.path, '-C', rootfs, '--strip-components=0',
      ]);

      if (result.exitCode != 0) {
        throw ProcessException('tar', [], 'Exit ${result.exitCode}: ${result.stderr}');
      }
    } finally {
      if (await tmpDir.exists()) await tmpDir.delete(recursive: true);
    }
  }

  // ---------------------------------------------------------------------------
  // DNS
  // ---------------------------------------------------------------------------

  void _setupDns() {
    // This runs synchronously using file I/O — no proot needed.
    // Called after rootfs is extracted.
    _setupDnsAsync();
  }

  Future<void> _setupDnsAsync() async {
    final rootfs = await TccPaths.rootfs;

    final resolvConf = File(p.join(rootfs, 'etc', 'resolv.conf'));
    await resolvConf.parent.create(recursive: true);
    await resolvConf.writeAsString(
      'nameserver 8.8.8.8\nnameserver 1.1.1.1\n',
    );

    final hosts = File(p.join(rootfs, 'etc', 'hosts'));
    await hosts.writeAsString('127.0.0.1 localhost\n::1 localhost\n');

    final hostname = File(p.join(rootfs, 'etc', 'hostname'));
    await hostname.writeAsString('tcc\n');
  }

  // ---------------------------------------------------------------------------
  // Running commands inside rootfs
  // ---------------------------------------------------------------------------

  Future<ProcessResult> runInRootfs(String command) async {
    final proot = await findProot();
    final rootfs = await TccPaths.rootfs;

    return Process.run(proot, [
      '-r', rootfs,
      '-b', '/dev',
      '-b', '/proc',
      '-b', '/sys',
      '-w', '/root',
      '/bin/sh', '-c', command,
    ]);
  }
}
