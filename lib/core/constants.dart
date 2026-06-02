import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;

/// Canonical path constants and helpers for the TCC project.
///
/// All paths are resolved lazily via [TccPaths] so they work on any Android
/// device regardless of where the app data directory is mounted.
class TccPaths {
  TccPaths._();

  // ---------------------------------------------------------------------------
  // Root directories
  // ---------------------------------------------------------------------------

  static String? _rootDir;

  /// Application documents directory (e.g. /data/data/com.tcc.app/files).
  static Future<String> get root async {
    if (_rootDir != null) return _rootDir!;
    final appDir = await getApplicationDocumentsDirectory();
    _rootDir = appDir.path;
    return _rootDir!;
  }

  // ---------------------------------------------------------------------------
  // Rootfs / proot
  // ---------------------------------------------------------------------------

  /// The Alpine Linux rootfs extracted inside the app sandbox.
  static Future<String> get rootfs async => p.join(await root, 'rootfs');

  /// Home directory inside the rootfs.
  static Future<String> get rootHome async => p.join(await rootfs, 'root');

  /// proot binary extracted from APK assets.
  static Future<String> get prootBinary async =>
      p.join(await root, 'proot');

  /// Path to resolv.conf inside rootfs.
  static Future<String> get resolvConf async =>
      p.join(await rootfs, 'etc', 'resolv.conf');

  /// Path to /etc/hosts inside rootfs.
  static Future<String> get hostsFile async =>
      p.join(await rootfs, 'etc', 'hosts');

  /// Rootfs assets archive bundled in the APK.
  static const String rootfsAsset = 'assets/core/rootfs.tgz';

  // ---------------------------------------------------------------------------
  // Workspace
  // ---------------------------------------------------------------------------

  /// All project workspaces live under rootfs /root/workspace.
  static Future<String> get workspace async =>
      p.join(await rootfs, 'root', 'workspace');

  /// Returns the working directory for a specific project.
  static Future<String> projectDir(String projectId) async =>
      p.join(await workspace, projectId);

  /// Returns the .claude/prompt.md path for a project.
  static Future<String> projectPrompt(String projectId) async =>
      p.join(await projectDir(projectId), '.claude', 'prompt.md');

  /// Returns the openspec directory for a project.
  static Future<String> openspecDir(String projectId) async =>
      p.join(await projectDir(projectId), 'openspec');

  // ---------------------------------------------------------------------------
  // Claude Code versions
  // ---------------------------------------------------------------------------

  /// Root of the version management tree.
  static Future<String> get versionsDir async =>
      p.join(await rootHome, '.tcc', 'versions');

  /// The "current" symlink directory (points to the active version).
  static Future<String> get currentVersion async =>
      p.join(await versionsDir, 'current');

  /// The Node.js binary shipped with the active version.
  static Future<String> get nodeBinary async =>
      p.join(await currentVersion, 'bin', 'node');

  /// The npm binary shipped with the active version.
  static Future<String> get npmBinary async =>
      p.join(await currentVersion, 'bin', 'npm');

  /// The Claude Code entry point.
  static Future<String> get currentCcBinary async =>
      p.join(await currentVersion, 'bin', 'claude');

  // ---------------------------------------------------------------------------
  // Sessions
  // ---------------------------------------------------------------------------

  /// Directory that holds all session JSONL files.
  static Future<String> get claudeHome async =>
      p.join(await rootfs, 'root', '.claude');

  /// Directory containing per-project session logs.
  static Future<String> get sessionsDir async =>
      p.join(await claudeHome, 'projects');

  /// Returns the JSONL log path for a specific session.
  static Future<String> sessionFile(String sessionId) async =>
      p.join(await sessionsDir, '$sessionId.jsonl');

  // ---------------------------------------------------------------------------
  // MCP config
  // ---------------------------------------------------------------------------

  /// Path to the Claude Desktop / MCP config file inside the rootfs.
  static Future<String> get mcpConfig async => p.join(
        await rootfs,
        'root',
        '.config',
        'Claude',
        'claude_desktop_config.json',
      );

  // ---------------------------------------------------------------------------
  // Platform strings
  // ---------------------------------------------------------------------------

  /// Alpine architecture string used for package downloads.
  static String get alpineArch {
    switch (Platform.version) {
      case _ when Platform.version.contains('aarch64') || Platform.version.contains('arm64'):
        return 'aarch64';
      case _ when Platform.version.contains('x86_64'):
        return 'x86_64';
      default:
        return 'aarch64';
    }
  }

  /// Target architecture for proot binary extraction.
  static String get prootArch {
    if (Platform.isAndroid) {
      // Most Android phones are ARM.  For x86 emulators we still ship aarch64
      // because proot is installed via Termux which provides the correct arch.
      return 'aarch64';
    }
    return 'aarch64';
  }

  /// Version constant for the TCC runtime itself.
  static const String tccVersion = '1.0.0';
}
