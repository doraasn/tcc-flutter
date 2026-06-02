import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:path/path.dart' as p;

class TccPaths {
  static String? _rootDir;

  static Future<String> get root async {
    if (_rootDir != null) return _rootDir!;
    final appDir = await getApplicationDocumentsDirectory();
    _rootDir = appDir.path;
    return _rootDir!;
  }

  static Future<String> get rootfs async => p.join(await root, 'rootfs');
  static Future<String> get workspace async => p.join(await rootfs, 'root', 'workspace');
  static Future<String> get claudeHome async => p.join(await rootfs, 'root', '.claude');
  static Future<String> get versionsDir async => p.join(await rootfs, 'root', '.tcc', 'versions');
  static Future<String> get currentVersion async => p.join(await versionsDir, 'current');
  static Future<String> get mcpConfig async => p.join(await rootfs, 'root', '.config', 'Claude', 'claude_desktop_config.json');

  static Future<String> projectDir(String projectId) async =>
      p.join(await workspace, projectId);

  static Future<String> projectPrompt(String projectId) async =>
      p.join(await projectDir(projectId), '.claude', 'prompt.md');

  static Future<String> openspecDir(String projectId) async =>
      p.join(await projectDir(projectId), 'openspec');

  static Future<String> sessionsDir() async =>
      p.join(await claudeHome, 'projects');

  static Future<String> get currentCcBinary async =>
      p.join(await currentVersion, 'bin', 'claude');
}
