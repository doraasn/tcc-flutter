import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme.dart';
import '../models/workspace_state.dart';
import '../providers/workspace_provider.dart';

/// Modal dialog to select, create, or delete projects.
/// Shows a list of all projects with options to manage them.
class ProjectPicker extends ConsumerStatefulWidget {
  const ProjectPicker({super.key});

  /// Shows the project picker as a modal dialog.
  static Future<void> show(BuildContext context) {
    return showDialog(
      context: context,
      builder: (_) => const ProjectPicker(),
    );
  }

  @override
  ConsumerState<ProjectPicker> createState() => _ProjectPickerState();
}

class _ProjectPickerState extends ConsumerState<ProjectPicker> {
  final _createController = TextEditingController();
  bool _isCreating = false;

  @override
  void dispose() {
    _createController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final workspace = ref.watch(workspaceProvider);

    return Dialog(
      backgroundColor: TccColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: TccColors.border),
      ),
      child: Container(
        width: 360,
        constraints: BoxConstraints(
          maxHeight: MediaQuery.of(context).size.height * 0.6,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildHeader(),
            const Divider(height: 1),
            _buildCreateSection(),
            const Divider(height: 1),
            _buildProjectList(workspace),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 16, 8, 8),
      child: Row(
        children: [
          const Icon(Icons.folder, color: TccColors.primary, size: 20),
          const SizedBox(width: 10),
          const Text('Projects', style: TccTextStyles.titleMedium),
          const Spacer(),
          IconButton(
            icon: const Icon(Icons.close, size: 20),
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
    );
  }

  Widget _buildCreateSection() {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: _isCreating
          ? Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _createController,
                    autofocus: true,
                    decoration: const InputDecoration(
                      hintText: 'Project name',
                      isDense: true,
                      contentPadding:
                          EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                    ),
                    style: TccTextStyles.bodyMedium,
                    onSubmitted: (_) => _createProject(),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  icon: const Icon(Icons.check, size: 20),
                  color: TccColors.success,
                  onPressed: _createProject,
                ),
                IconButton(
                  icon: const Icon(Icons.close, size: 20),
                  onPressed: () {
                    setState(() {
                      _isCreating = false;
                      _createController.clear();
                    });
                  },
                ),
              ],
            )
          : SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: () => setState(() => _isCreating = true),
                icon: const Icon(Icons.add, size: 18),
                label: const Text('New Project'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: TccColors.primary,
                  side: const BorderSide(color: TccColors.primary),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
            ),
    );
  }

  Widget _buildProjectList(WorkspaceState workspace) {
    return FutureBuilder<List<String>>(
      future: ref.read(workspaceProvider.notifier).projects,
      builder: (context, snapshot) {
        final projects = snapshot.data ?? [];

        if (projects.isEmpty) {
          return const Padding(
            padding: EdgeInsets.all(24),
            child: Text('No projects yet', style: TccTextStyles.caption),
          );
        }

        return Flexible(
          child: ListView.separated(
            shrinkWrap: true,
            padding: const EdgeInsets.symmetric(vertical: 4),
            itemCount: projects.length,
            separatorBuilder: (_, __) => const SizedBox.shrink(),
            itemBuilder: (context, index) {
              final project = projects[index];
              final isActive = project == workspace.projectId;
              return _buildProjectTile(project, isActive);
            },
          ),
        );
      },
    );
  }

  Widget _buildProjectTile(String project, bool isActive) {
    return ListTile(
      dense: true,
      selected: isActive,
      selectedTileColor: TccColors.primary.withOpacity(0.1),
      leading: Icon(
        isActive ? Icons.folder : Icons.folder_outlined,
        color: isActive ? TccColors.primary : TccColors.onSurfaceVariant,
        size: 20,
      ),
      title: Text(
        project,
        style: TextStyle(
          color: isActive ? TccColors.primary : TccColors.onSurface,
          fontWeight: isActive ? FontWeight.w600 : FontWeight.w400,
          fontSize: 14,
        ),
      ),
      trailing: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (isActive)
            const Icon(Icons.check_circle, size: 16, color: TccColors.primary),
          IconButton(
            icon: const Icon(Icons.delete_outline, size: 18),
            color: TccColors.onSurfaceVariant,
            onPressed: () => _confirmDelete(project),
            padding: const EdgeInsets.all(4),
            constraints: const BoxConstraints(),
          ),
        ],
      ),
      onTap: () {
        ref.read(workspaceProvider.notifier).switchProject(project);
        Navigator.pop(context);
      },
    );
  }

  void _createProject() {
    final name = _createController.text.trim();
    if (name.isEmpty) return;

    ref.read(workspaceProvider.notifier).createProject(name);
    _createController.clear();
    setState(() => _isCreating = false);
    Navigator.pop(context);
  }

  void _confirmDelete(String project) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Delete Project'),
        content: Text('Delete "$project" and all its files?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              ref.read(workspaceProvider.notifier).deleteProject(project);
              Navigator.pop(ctx);
            },
            child: const Text('Delete', style: TextStyle(color: TccColors.error)),
          ),
        ],
      ),
    );
  }
}
