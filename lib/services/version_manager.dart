import 'dart:io';

import 'package:path/path.dart' as p;

import '../core/constants.dart';
import 'proot_service.dart';

/// Represents a single installed Claude Code version on disk.
class InstalledVersion {
  /// Directory name, e.g. `claude-1717363200000`.
  final String name;

  /// Absolute path to the version directory.
  final String path;

  /// Whether this version is currently active (the `current` symlink points
  /// here).
  final bool isActive;

  const InstalledVersion({
    required this.name,
    required this.path,
    required this.isActive,
  });

  @override
  String toString() =>
      'InstalledVersion(name=$name, isActive=$isActive)';
}

/// Manages Claude Code npm package versions inside the proot rootfs.
///
/// Responsibilities:
/// - Download and install new versions.
/// - Switch the `current` symlink atomically (supports rollback).
/// - List all installed versions.
/// - Query the current active version.
class VersionManager {
  final PRootService _proot;

  VersionManager({PRootService? proot}) : _proot = proot ?? PRootService();

  // ---------------------------------------------------------------------------
  // Current version
  // ---------------------------------------------------------------------------

  /// Returns the absolute path of the currently active version directory, or
  /// `null` if no version has been installed yet.
  Future<String?> getCurrentVersionPath() async {
    final currentDir = Directory(await TccPaths.currentVersion);
    if (!await currentDir.exists()) return null;

    try {
      // Resolve symlink to get the real path.
      final target = await currentDir.resolveSymbolicLinks();
      return target;
    } on FileSystemException {
      return null;
    }
  }

  /// Returns the version directory name of the current version, or `null`.
  Future<String?> getCurrentVersionName() async {
    final path = await getCurrentVersionPath();
    if (path == null) return null;
    return p.basename(path);
  }

  // ---------------------------------------------------------------------------
  // List installed versions
  // ---------------------------------------------------------------------------

  /// Returns all installed version directories, sorted newest-first.
  ///
  /// The list includes both the currently active version and any older versions
  /// that have not been cleaned up.
  Future<List<InstalledVersion>> listInstalledVersions() async {
    final versionsDir = await TccPaths.versionsDir;
    final dir = Directory(versionsDir);
    if (!await dir.exists()) return [];

    final currentPath = await getCurrentVersionPath();
    final entries = <InstalledVersion>[];

    await for (final entity in dir.list()) {
      if (entity is! Directory) continue;
      final name = p.basename(entity.path);

      // Skip the 'current' symlink directory and any hidden/temp directories.
      if (name == 'current' || name.startsWith('.')) continue;

      entries.add(InstalledVersion(
        name: name,
        path: entity.path,
        isActive: entity.path == currentPath,
      ));
    }

    // Sort by directory name descending (newest timestamp first).
    entries.sort((a, b) => b.name.compareTo(a.name));
    return entries;
  }

  // ---------------------------------------------------------------------------
  // Download & install
  // ---------------------------------------------------------------------------

  /// Downloads and installs a new version of the Claude Code npm package.
  ///
  /// Returns the [InstalledVersion] for the newly created version directory.
  /// Does NOT switch the `current` symlink -- call [switchTo] for that.
  Future<InstalledVersion> downloadVersion({
    String? versionConstraint,
  }) async {
    final versionsDir = await TccPaths.versionsDir;
    await Directory(versionsDir).create(recursive: true);

    // Create a timestamped directory for this version.
    final timestamp = DateTime.now().millisecondsSinceEpoch;
    final versionDirName = 'claude-$timestamp';
    final versionDir = p.join(versionsDir, versionDirName);
    await Directory(versionDir).create(recursive: true);

    // Build the npm install command.
    final packageName = versionConstraint != null
        ? '@anthropic-ai/claude-code@$versionConstraint'
        : '@anthropic-ai/claude-code';

    final result = await _proot.runInRootfs(
      'npm install --prefix $versionDir -g $packageName',
    );

    if (result.exitCode != 0) {
      // Clean up the partially created directory.
      final dir = Directory(versionDir);
      if (await dir.exists()) {
        await dir.delete(recursive: true);
      }
      throw ProcessException(
        'npm',
        ['install', '--prefix', versionDir, '-g', packageName],
        'Failed to install version: ${result.stderr}',
      );
    }

    // Run the Claude Code native postinstall script if present.
    final installScript = p.join(
      versionDir,
      'lib',
      'node_modules',
      '@anthropic-ai',
      'claude-code',
      'install.cjs',
    );
    if (await File(installScript).exists()) {
      final postResult = await _proot.runInRootfs('node $installScript');
      if (postResult.exitCode != 0) {
        // Non-fatal -- log but continue.  The binary may still work for most
        // use cases even without the native binding.
        stderr.writeln(
          'Warning: Claude Code postinstall failed: ${postResult.stderr}',
        );
      }
    }

    return InstalledVersion(
      name: versionDirName,
      path: versionDir,
      isActive: false,
    );
  }

  /// Downloads a version AND switches to it in one step.
  ///
  /// This is the most common flow for upgrading.
  Future<InstalledVersion> downloadAndSwitch({
    String? versionConstraint,
  }) async {
    final version =
        await downloadVersion(versionConstraint: versionConstraint);
    await switchTo(version.path);
    return InstalledVersion(
      name: version.name,
      path: version.path,
      isActive: true,
    );
  }

  // ---------------------------------------------------------------------------
  // Switching
  // ---------------------------------------------------------------------------

  /// Atomically switches the `current` symlink to point at [versionPath].
  ///
  /// Supports rollback: call this again with the previous version path.
  ///
  /// Throws if the target directory does not exist.
  Future<void> switchTo(String versionPath) async {
    final targetDir = Directory(versionPath);
    if (!await targetDir.exists()) {
      throw FileSystemException(
        'Version directory does not exist',
        versionPath,
      );
    }

    final versionsDir = await TccPaths.versionsDir;
    final currentDir = p.join(versionsDir, 'current');
    final tempLink = p.join(versionsDir, 'current_new');

    // Remove any leftover temp symlink.
    final tempLinkDir = Directory(tempLink);
    if (await tempLinkDir.exists()) {
      await tempLinkDir.delete();
    }

    // Create a temporary symlink via `ln -sfn`.
    final lnResult =
        await Process.run('ln', ['-sfn', versionPath, tempLink]);
    if (lnResult.exitCode != 0) {
      throw ProcessException(
        'ln',
        ['-sfn', versionPath, tempLink],
        'Failed to create symlink: ${lnResult.stderr}',
      );
    }

    // Atomically rename into place.
    final mvResult = await Process.run('mv', ['-Tf', tempLink, currentDir]);
    if (mvResult.exitCode != 0) {
      throw ProcessException(
        'mv',
        ['-Tf', tempLink, currentDir],
        'Failed to activate version: ${mvResult.stderr}',
      );
    }
  }

  /// Convenience: switch to a version by its directory name (e.g.
  /// `claude-1717363200000`).
  Future<void> switchByName(String versionName) async {
    final versionsDir = await TccPaths.versionsDir;
    final versionPath = p.join(versionsDir, versionName);
    await switchTo(versionPath);
  }

  /// Rolls back to the previous version by switching the symlink to the
  /// second-most-recent installed version.
  ///
  /// Throws if fewer than two versions are installed.
  Future<void> rollback() async {
    final versions = await listInstalledVersions();
    if (versions.length < 2) {
      throw StateError(
        'Need at least two installed versions to rollback, '
        'but found ${versions.length}.',
      );
    }

    // versions[0] is current (newest), versions[1] is the previous one.
    await switchTo(versions[1].path);
  }

  // ---------------------------------------------------------------------------
  // Cleanup
  // ---------------------------------------------------------------------------

  /// Removes all versions except the currently active one.
  Future<void> pruneOldVersions() async {
    final versions = await listInstalledVersions();
    for (final v in versions) {
      if (v.isActive) continue;
      final dir = Directory(v.path);
      if (await dir.exists()) {
        await dir.delete(recursive: true);
      }
    }
  }

  /// Removes a specific version by name.  Refuses to remove the active version.
  Future<void> removeVersion(String versionName) async {
    final versions = await listInstalledVersions();
    final target = versions.firstWhere(
      (v) => v.name == versionName,
      orElse: () => throw StateError('Version $versionName not found'),
    );

    if (target.isActive) {
      throw StateError(
        'Cannot remove the active version.  Switch to another version first.',
      );
    }

    final dir = Directory(target.path);
    if (await dir.exists()) {
      await dir.delete(recursive: true);
    }
  }
}
