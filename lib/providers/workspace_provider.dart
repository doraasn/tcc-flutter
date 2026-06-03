import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import '../core/constants.dart';
import '../models/workspace_state.dart';

/// State: current workspace state.
final workspaceProvider =
    StateNotifierProvider<WorkspaceController, WorkspaceState>((ref) {
  return WorkspaceController();
});

/// Read-only list of project names persisted in Hive.
final projectListProvider = FutureProvider<List<String>>((ref) async {
  final box = Hive.box('projects');
  return (box.get('list', defaultValue: []) as List).cast<String>();
});

class WorkspaceController extends StateNotifier<WorkspaceState> {
  WorkspaceController() : super(const WorkspaceState()) {
    _loadProjects();
  }

  Box get _box => Hive.box('projects');

  // ---------------------------------------------------------------------------
  // Initialization
  // ---------------------------------------------------------------------------

  Future<void> _loadProjects() async {
    final projects = await this.projects;
    if (projects.isNotEmpty) {
      final lastProject = _box.get('lastProject') as String?;
      if (lastProject != null && projects.contains(lastProject)) {
        await switchProject(lastProject);
      } else {
        await switchProject(projects.first);
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Query helpers
  // ---------------------------------------------------------------------------

  Future<List<String>> get projects async {
    final box = Hive.box('projects');
    return (box.get('list', defaultValue: []) as List).cast<String>();
  }

  /// Whether the current project has an openspec directory.
  bool get hasOpenspec => state.openSpecSkills.isNotEmpty;

  /// The directory that contains session JSONL files.
  Future<String> get sessionsDir => TccPaths.sessionsDir;

  // ---------------------------------------------------------------------------
  // CRUD
  // ---------------------------------------------------------------------------

  /// Create a new project with the given [name].
  Future<void> createProject(String name) async {
    final trimmed = name.trim();
    if (trimmed.isEmpty) return;

    final projectPath = await TccPaths.projectDir(trimmed);
    final dir = Directory(projectPath);
    if (!await dir.exists()) {
      await dir.create(recursive: true);
      final claudeDir = Directory(p.join(dir.path, '.claude'));
      await claudeDir.create(recursive: true);
    }

    final box = Hive.box('projects');
    final list = (box.get('list', defaultValue: []) as List).cast<String>();
    if (!list.contains(trimmed)) {
      list.add(trimmed);
      await box.put('list', list);
    }
    await switchProject(trimmed);
  }

  /// Delete the project with [projectId] and its files on disk.
  Future<void> deleteProject(String projectId) async {
    final dir = Directory(await TccPaths.projectDir(projectId));
    if (await dir.exists()) {
      await dir.delete(recursive: true);
    }

    final box = Hive.box('projects');
    final list = (box.get('list', defaultValue: []) as List).cast<String>();
    list.remove(projectId);
    await box.put('list', list);

    if (state.projectId == projectId) {
      if (list.isNotEmpty) {
        await switchProject(list.first);
      } else {
        state = const WorkspaceState();
      }
    }
  }

  /// Rename an existing project.
  Future<void> renameProject(String oldId, String newId) async {
    final trimmed = newId.trim();
    if (trimmed.isEmpty || trimmed == oldId) return;

    final oldDir = Directory(await TccPaths.projectDir(oldId));
    final newDirPath = await TccPaths.projectDir(trimmed);

    if (await oldDir.exists()) {
      await oldDir.rename(newDirPath);
    }

    final box = Hive.box('projects');
    final list = (box.get('list', defaultValue: []) as List).cast<String>();
    final idx = list.indexOf(oldId);
    if (idx != -1) {
      list[idx] = trimmed;
      await box.put('list', list);
    }

    if (state.projectId == oldId) {
      await switchProject(trimmed);
    }
  }

  // ---------------------------------------------------------------------------
  // Switch project
  // ---------------------------------------------------------------------------

  /// Switch to [projectId]. Updates cwd, detects openspec, persists choice.
  Future<void> switchProject(String projectId) async {
    final cwd = await TccPaths.projectDir(projectId);
    final openspecDir = await TccPaths.openspecDir(projectId);
    final hasOpenspec = await Directory(openspecDir).exists();

    state = WorkspaceState(
      projectId: projectId,
      projectName: projectId,
      cwd: cwd,
      openSpecSkills: hasOpenspec
          ? ['/opsx:explore', '/opsx:propose', '/opsx:apply']
          : [],
      createdAt: DateTime.now().millisecondsSinceEpoch,
    );

    await _box.put('lastProject', projectId);
  }
}
