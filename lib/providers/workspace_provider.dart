import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';
import 'package:path/path.dart' as p;
import '../core/constants.dart';
import '../models/workspace_state.dart';

final workspaceProvider = StateNotifierProvider<WorkspaceController, WorkspaceState>((ref) {
  return WorkspaceController();
});

class WorkspaceController extends StateNotifier<WorkspaceState> {
  WorkspaceController() : super(const WorkspaceState()) {
    _loadProjects();
  }

  Box get _box => Hive.box('projects');

  Future<void> _loadProjects() async {
    final projects = _box.get('list', defaultValue: []) as List;
    if (projects.isNotEmpty) {
      final lastProject = _box.get('lastProject') as String?;
      if (lastProject != null && projects.contains(lastProject)) {
        await switchProject(lastProject);
      } else {
        await switchProject(projects.first);
      }
    }
  }

  Future<List<String>> get projects async {
    final box = Hive.box('projects');
    return (box.get('list', defaultValue: []) as List).cast<String>();
  }

  Future<void> createProject(String name) async {
    final dir = Directory(await TccPaths.projectDir(name));
    if (!await dir.exists()) {
      await dir.create(recursive: true);
      final claudeDir = Directory(p.join(dir.path, '.claude'));
      await claudeDir.create(recursive: true);
    }
    final box = Hive.box('projects');
    final list = (box.get('list', defaultValue: []) as List).cast<String>();
    if (!list.contains(name)) {
      list.add(name);
      await box.put('list', list);
    }
    await switchProject(name);
  }

  Future<void> deleteProject(String name) async {
    final dir = Directory(await TccPaths.projectDir(name));
    if (await dir.exists()) {
      await dir.delete(recursive: true);
    }
    final box = Hive.box('projects');
    final list = (box.get('list', defaultValue: []) as List).cast<String>();
    list.remove(name);
    await box.put('list', list);
    if (state.projectId == name) {
      if (list.isNotEmpty) {
        await switchProject(list.first);
      } else {
        state = const WorkspaceState();
      }
    }
  }

  Future<void> switchProject(String projectId) async {
    final cwd = await TccPaths.projectDir(projectId);
    final openspecDir = await TccPaths.openspecDir(projectId);
    final hasOpenspec = await Directory(openspecDir).exists();

    state = WorkspaceState(
      projectId: projectId,
      projectName: projectId,
      cwd: cwd,
      openSpecSkills: hasOpenspec ? ['/opsx:explore', '/opsx:propose', '/opsx:apply'] : [],
    );

    await _box.put('lastProject', projectId);
  }
}
