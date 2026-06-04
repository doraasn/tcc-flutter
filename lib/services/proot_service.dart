import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;

import '../core/constants.dart';
import '../providers/process_provider.dart';

/// Manages the proot-based Alpine Linux environment that runs Claude Code
/// inside the Android app sandbox.
///
/// Responsibilities:
/// - Extract the proot binary from APK assets on first run.
/// - Extract the Alpine rootfs from `assets/core/rootfs.tgz`.
/// - Configure DNS resolution (`resolv.conf`, `/etc/hosts`).
/// - Install Node.js and Claude Code inside the rootfs.
/// - Version management: symlink `current/` to the active version directory.
/// - Provide a [runInRootfs] helper for arbitrary commands.
class PRootService {
  PRootService._();

  static final PRootService _instance = PRootService._();
  factory PRootService() => _instance;

  // ---------------------------------------------------------------------------
  // State
  // ---------------------------------------------------------------------------

  bool _initialized = false;
  String? _prootPath;

  /// Optional logging callback (set by ProcessController).
  void Function(String level, String msg)? log;

  void _info(String msg) => log?.call('INFO', msg);
  void _error(String msg) => log?.call('ERROR', msg);

  bool get isInitialized => _initialized;

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------

  /// One-shot initialization.  Safe to call multiple times.
  ///
  /// 1. Extracts proot binary from APK assets (if not already on disk).
  /// 2. Extracts the rootfs archive (if not already on disk).
  /// 3. Sets up DNS resolution inside the rootfs.
  Future<void> initialize() async {
    if (_initialized) return;

    await _ensureProotBinary();
    await _ensureRootfs();
    await _setupDns();
    await _installNodeJs();
    await _installClaudeCode();

    _initialized = true;
  }

  // ---------------------------------------------------------------------------
  // proot binary
  // ---------------------------------------------------------------------------

  /// Ensures the proot binary is extracted from APK assets and is executable.
  Future<void> _ensureProotBinary() async {
    final prootFile = File(await TccPaths.prootBinary);
    if (await prootFile.exists() && await prootFile.length() > 0) {
      _prootPath = prootFile.path;
      return;
    }

    // Try Termux-installed proot first (more reliable on Android).
    const termuxProot = '/data/data/com.termux/files/usr/bin/proot';
    if (await File(termuxProot).exists()) {
      _prootPath = termuxProot;
      return;
    }

    // Fall back to extracting from APK assets.
    final tmpDir = Directory.systemTemp.createTempSync('tcc_proot_');
    try {
      final byteData = await rootBundle.load('assets/core/proot-arm64');
      final bytes = byteData.buffer.asUint8List();

      // Ensure parent directory exists.
      await prootFile.parent.create(recursive: true);
      await prootFile.writeAsBytes(bytes, flush: true);

      // chmod +x
      await Process.run('chmod', ['755', prootFile.path]);

      _prootPath = prootFile.path;
    } finally {
      if (await tmpDir.exists()) {
        await tmpDir.delete(recursive: true);
      }
    }
  }

  /// Returns the absolute path to the proot binary.
  Future<String> findProot() async {
    if (_prootPath != null && await File(_prootPath!).exists()) {
      return _prootPath!;
    }

    // Re-run extraction logic.
    await _ensureProotBinary();
    if (_prootPath == null) {
      throw StateError(
        'proot binary not found.  '
        'Ensure assets/core/proot exists in the APK or install via '
        '`pkg install proot` in Termux.',
      );
    }
    return _prootPath!;
  }

  // ---------------------------------------------------------------------------
  // Rootfs
  // ---------------------------------------------------------------------------

  /// Extracts the Alpine rootfs from bundled assets if it does not yet exist.
  Future<void> _ensureRootfs() async {
    final rootfs = await TccPaths.rootfs;
    final rootfsDir = Directory(rootfs);

    _info('Rootfs path: $rootfs');
    _info('Rootfs exists: ${await rootfsDir.exists()}');

    if (await rootfsDir.exists()) {
      // Validate the rootfs is functional by checking for /bin/sh.
      final binSh = File('$rootfs/bin/sh');
      if (await binSh.exists()) {
        _info('Rootfs already valid, skipping extraction');
        return;
      }

      // List what's in the rootfs
      _error('Rootfs exists but /bin/sh missing, listing contents:');
      try {
        await for (final entity in rootfsDir.list(recursive: false).take(20)) {
          _error('  ${entity.path}');
        }
      } catch (_) {}

      // Broken rootfs, delete and re-extract.
      await rootfsDir.delete(recursive: true);
    }

    await _extractRootfsFromAssets();

    // Validate extraction succeeded.
    final binSh = File('$rootfs/bin/sh');
    if (!await binSh.exists()) {
      _error('/bin/sh still missing after extraction!');
      // List /bin directory contents
      final binDir = Directory('$rootfs/bin');
      _error('Listing /bin contents:');
      try {
        await for (final entity in binDir.list(followLinks: false).take(30)) {
          final type = entity is Link ? 'LINK' : (entity is Directory ? 'DIR' : 'FILE');
          String target = '';
          if (entity is Link) {
            try { target = ' -> ${await entity.target()}'; } catch (_) {}
          }
          _error('  [$type] ${entity.path}$target');
        }
      } catch (e) {
        _error('  Error listing /bin: $e');
      }
      // Also check /usr/bin
      final usrBinDir = Directory('$rootfs/usr/bin');
      _error('Listing /usr/bin contents:');
      try {
        await for (final entity in usrBinDir.list(followLinks: false).take(30)) {
          final type = entity is Link ? 'LINK' : (entity is Directory ? 'DIR' : 'FILE');
          String target = '';
          if (entity is Link) {
            try { target = ' -> ${await entity.target()}'; } catch (_) {}
          }
          _error('  [$type] ${entity.path}$target');
        }
      } catch (e) {
        _error('  Error listing /usr/bin: $e');
      }
      throw StateError(
        'Rootfs extraction failed: /bin/sh is missing. '
        'The bundled rootfs.tgz asset may be empty or corrupted. '
        'Try clearing app data and restarting, or reinstall the APK.',
      );
    }
    _info('Rootfs validated: /bin/sh exists');
  }

  /// Extracts `assets/core/rootfs.tgz` into the rootfs directory.
  Future<void> _extractRootfsFromAssets() async {
    final rootfs = await TccPaths.rootfs;

    final tmpDir = Directory.systemTemp.createTempSync('tcc_rootfs_');
    final tarball = File(p.join(tmpDir.path, 'rootfs.tgz'));

    try {
      // Load archive from Flutter asset bundle.
      _info('Loading asset: ${TccPaths.rootfsAsset}');
      final byteData = await rootBundle.load(TccPaths.rootfsAsset);
      final bytes = byteData.buffer.asUint8List();
      _info('Asset loaded: ${bytes.length} bytes');

      if (bytes.length < 100) {
        _error('Asset is too small (${bytes.length} bytes), likely empty placeholder');
        throw StateError('rootfs.tgz asset is empty or corrupted');
      }

      await tarball.writeAsBytes(bytes, flush: true);
      _info('Tarball written to: ${tarball.path}');

      // Create target directory.
      final rootfsDir = Directory(rootfs);
      await rootfsDir.create(recursive: true);

      _info('Extracting tarball to: $rootfs');
      final result = await Process.run('tar', [
        'xzf',
        tarball.path,
        '-C',
        rootfs,
        '--strip-components=0',
      ]);

      _info('tar exit code: ${result.exitCode}');
      if (result.stdout.toString().isNotEmpty) {
        _info('tar stdout: ${result.stdout}');
      }
      if (result.stderr.toString().isNotEmpty) {
        _error('tar stderr: ${result.stderr}');
      }

      if (result.exitCode != 0) {
        throw ProcessException('tar', [
          'xzf', tarball.path, '-C', rootfs, '--strip-components=0',
        ], 'Exit code ${result.exitCode}: ${result.stderr}');
      }
    } finally {
      if (await tmpDir.exists()) {
        await tmpDir.delete(recursive: true);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // DNS setup
  // ---------------------------------------------------------------------------

  /// Writes `/etc/resolv.conf` and `/etc/hosts` inside the rootfs so that
  /// network access works inside proot.
  Future<void> _setupDns() async {
    final rootfs = await TccPaths.rootfs;

    // /etc/resolv.conf — use Google and Cloudflare public DNS.
    final resolvConf = File(p.join(rootfs, 'etc', 'resolv.conf'));
    await resolvConf.parent.create(recursive: true);
    await resolvConf.writeAsString(
      'nameserver 8.8.8.8\n'
      'nameserver 1.1.1.1\n'
      'nameserver 2001:4860:4860::8888\n',
    );

    // /etc/hosts
    final hosts = File(p.join(rootfs, 'etc', 'hosts'));
    await hosts.writeAsString(
      '127.0.0.1 localhost\n'
      '::1 localhost\n',
    );

    // /etc/hostname
    final hostname = File(p.join(rootfs, 'etc', 'hostname'));
    await hostname.writeAsString('tcc\n');
  }

  // ---------------------------------------------------------------------------
  // Node.js installation
  // ---------------------------------------------------------------------------

  /// Installs Node.js inside the rootfs via Alpine's apk package manager.
  ///
  /// Idempotent — skips if `node` is already on the PATH inside the rootfs.
  Future<void> _installNodeJs() async {
    // Quick check: does node exist inside the versioned bin?
    final nodeFile = File(await TccPaths.nodeBinary);
    if (await nodeFile.exists()) return;

    final rootfs = await TccPaths.rootfs;
    final apkCache = Directory(p.join(rootfs, 'var', 'cache', 'apk'));
    await apkCache.create(recursive: true);

    final result = await runInRootfs(
      'apk update && apk add --no-cache nodejs npm',
    );

    if (result.exitCode != 0) {
      throw ProcessException('apk', ['add', 'nodejs', 'npm'],
          'Failed to install Node.js: ${result.stderr}');
    }
  }

  // ---------------------------------------------------------------------------
  // Claude Code installation
  // ---------------------------------------------------------------------------

  /// Installs or updates the Claude Code npm package inside the versioned
  /// directory.  The `current/` symlink is updated to point at the new
  /// version directory.
  Future<void> _installClaudeCode() async {
    final versionsDir = await TccPaths.versionsDir;
    await Directory(versionsDir).create(recursive: true);

    final versionDir = Directory(p.join(
      versionsDir,
      'claude-${DateTime.now().millisecondsSinceEpoch}',
    ));
    await versionDir.create(recursive: true);

    // Create the bin directory that the symlink will eventually point to.
    final binDir = Directory(p.join(versionDir.path, 'bin'));
    await binDir.create(recursive: true);

    // Install @anthropic-ai/claude-code globally into versionDir.
    final result = await runInRootfs(
      'npm install '
      '--prefix ${versionDir.path} '
      '-g @anthropic-ai/claude-code',
    );

    if (result.exitCode != 0) {
      throw ProcessException('npm', ['install', '@anthropic-ai/claude-code'],
          'Failed to install Claude Code: ${result.stderr}');
    }

    // Run the native binary postinstall (required by Claude Code).
    final installScript = p.join(
      versionDir.path,
      'lib',
      'node_modules',
      '@anthropic-ai',
      'claude-code',
      'install.cjs',
    );
    if (await File(installScript).exists()) {
      await runInRootfs('node $installScript');
    }

    // Atomically update the 'current' symlink.
    await _switchCurrentSymlink(versionDir.path);
  }

  // ---------------------------------------------------------------------------
  // Version symlink management
  // ---------------------------------------------------------------------------

  /// Atomically switches the `current` symlink to point at [newVersionPath].
  ///
  /// Uses a temp-rename strategy to avoid partial updates:
  /// 1. Create `current_new` symlink.
  /// 2. Rename `current_new` -> `current` (atomic on Linux).
  Future<void> _switchCurrentSymlink(String newVersionPath) async {
    final versionsDir = await TccPaths.versionsDir;
    final currentDir = p.join(versionsDir, 'current');
    final tempLink = p.join(versionsDir, 'current_new');

    // Remove stale temp link if it exists.
    final tempLinkDir = Directory(tempLink);
    if (await tempLinkDir.exists()) {
      await tempLinkDir.delete();
    }

    // Create a temporary symlink.
    final result = await Process.run('ln', ['-sfn', newVersionPath, tempLink]);
    if (result.exitCode != 0) {
      throw ProcessException(
          'ln', ['-sfn', newVersionPath, tempLink], result.stderr);
    }

    // Atomically rename into place.
    final renameResult = await Process.run('mv', ['-Tf', tempLink, currentDir]);
    if (renameResult.exitCode != 0) {
      throw ProcessException(
          'mv', ['-Tf', tempLink, currentDir], renameResult.stderr);
    }
  }

  // ---------------------------------------------------------------------------
  // Running commands inside rootfs
  // ---------------------------------------------------------------------------

  /// Runs an arbitrary shell command inside the proot environment.
  ///
  /// Uses `/bin/sh -c` so pipelines and shell syntax work.
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

  // ---------------------------------------------------------------------------
  // Cleanup
  // ---------------------------------------------------------------------------

}
