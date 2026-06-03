import 'dart:io';

import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../core/constants.dart';

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

    if (await rootfsDir.exists()) {
      // Validate the rootfs is functional by checking for /bin/sh.
      final binSh = File('$rootfs/bin/sh');
      if (await binSh.exists()) return;

      // Broken rootfs (e.g. empty placeholder directory), delete and
      // re-extract from assets.
      await rootfsDir.delete(recursive: true);
    }

    await _extractRootfsFromAssets();

    // Validate extraction succeeded.
    final binSh = File('$rootfs/bin/sh');
    if (!await binSh.exists()) {
      throw StateError(
        'Rootfs extraction failed: /bin/sh is missing. '
        'The bundled rootfs.tgz asset may be empty or corrupted. '
        'Try clearing app data and restarting, or reinstall the APK.',
      );
    }
  }

  /// Extracts `assets/core/rootfs.tgz` into the rootfs directory.
  Future<void> _extractRootfsFromAssets() async {
    final rootfs = await TccPaths.rootfs;

    final tmpDir = Directory.systemTemp.createTempSync('tcc_rootfs_');
    final tarball = File(p.join(tmpDir.path, 'rootfs.tgz'));

    try {
      // Load archive from Flutter asset bundle.
      final byteData = await rootBundle.load(TccPaths.rootfsAsset);
      final bytes = byteData.buffer.asUint8List();

      await tarball.writeAsBytes(bytes, flush: true);

      // Create target directory.
      final rootfsDir = Directory(rootfs);
      await rootfsDir.create(recursive: true);

      final result = await Process.run('tar', [
        'xzf',
        tarball.path,
        '-C',
        rootfs,
        '--strip-components=0',
      ]);

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
