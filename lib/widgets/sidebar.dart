import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme.dart';
import '../providers/workspace_provider.dart';
import '../providers/session_provider.dart';
import '../models/workspace_state.dart';

class Sidebar extends ConsumerWidget {
  final VoidCallback onClose;
  final Function(String) onProjectSelected;

  const Sidebar({
    super.key,
    required this.onClose,
    required this.onProjectSelected,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final workspace = ref.watch(workspaceProvider);
    final sessionsAsync = ref.watch(sessionProvider);

    return Container(
      width: 280,
      decoration: const BoxDecoration(
        color: TccColors.surface,
        border: Border(
          right: BorderSide(color: TccColors.border),
        ),
      ),
      child: Column(
        children: [
          _buildHeader(),
          _buildNewProjectButton(ref),
          const Divider(),
          Expanded(
            child: ListView(
              children: [
                _buildProjectsSection(ref, workspace),
                const Divider(),
                _buildSessionsSection(sessionsAsync),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHeader() {
    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          const Icon(Icons.terminal, color: TccColors.primary, size: 20),
          const SizedBox(width: 8),
          const Text('TCC', style: TccTextStyles.titleMedium),
          const Spacer(),
          IconButton(
            icon: const Icon(Icons.close, size: 20),
            onPressed: onClose,
          ),
        ],
      ),
    );
  }

  Widget _buildNewProjectButton(WidgetRef ref) {
    return Padding(
      padding: const EdgeInsets.all(8),
      child: OutlinedButton.icon(
        onPressed: () => _showNewProjectDialog(ref),
        icon: const Icon(Icons.add, size: 18),
        label: const Text('New Project'),
        style: OutlinedButton.styleFrom(
          foregroundColor: TccColors.primary,
          side: const BorderSide(color: TccColors.primary),
        ),
      ),
    );
  }

  Widget _buildProjectsSection(WidgetRef ref, WorkspaceState workspace) {
    return FutureBuilder<List<String>>(
      future: ref.read(workspaceProvider.notifier).projects,
      builder: (context, snapshot) {
        final projects = snapshot.data ?? [];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Padding(
              padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Text('PROJECTS', style: TccTextStyles.caption),
            ),
            for (final project in projects)
              _buildProjectItem(ref, project, workspace.projectId),
          ],
        );
      },
    );
  }

  Widget _buildProjectItem(WidgetRef ref, String projectId, String activeId) {
    final isActive = projectId == activeId;
    return ListTile(
      dense: true,
      selected: isActive,
      selectedTileColor: TccColors.primary.withOpacity(0.1),
      leading: Icon(
        Icons.folder,
        color: isActive ? TccColors.primary : TccColors.onSurfaceVariant,
        size: 18,
      ),
      title: Text(
        projectId,
        style: TextStyle(
          color: isActive ? TccColors.primary : TccColors.onSurface,
          fontSize: 13,
        ),
      ),
      trailing: IconButton(
        icon: const Icon(Icons.delete_outline, size: 16),
        onPressed: () => ref.read(workspaceProvider.notifier).deleteProject(projectId),
      ),
      onTap: () => onProjectSelected(projectId),
    );
  }

  Widget _buildSessionsSection(AsyncValue<List<SessionInfo>> sessionsAsync) {
    return sessionsAsync.when(
      data: (sessions) => Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: Text('SESSIONS', style: TccTextStyles.caption),
          ),
          if (sessions.isEmpty)
            const Padding(
              padding: EdgeInsets.all(16),
              child: Text('No sessions yet', style: TccTextStyles.caption),
            ),
          for (final session in sessions)
            _buildSessionItem(session),
        ],
      ),
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(16),
          child: CircularProgressIndicator(),
        ),
      ),
      error: (e, _) => Padding(
        padding: const EdgeInsets.all(16),
        child: Text('Error: $e', style: TccTextStyles.caption),
      ),
    );
  }

  Widget _buildSessionItem(SessionInfo session) {
    return ListTile(
      dense: true,
      leading: const Icon(Icons.chat_bubble_outline, size: 18),
      title: Text(
        session.title,
        style: TccTextStyles.bodyMedium,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        '${session.projectId} · ${_formatDate(session.createdAt)}',
        style: TccTextStyles.caption,
      ),
      onTap: () {
        // TODO: Resume session
      },
    );
  }

  String _formatDate(DateTime date) {
    final now = DateTime.now();
    if (date.day == now.day) return 'Today ${date.hour}:${date.minute.toString().padLeft(2, '0')}';
    if (date.day == now.day - 1) return 'Yesterday';
    return '${date.month}/${date.day}';
  }

  void _showNewProjectDialog(WidgetRef ref) {
    final controller = TextEditingController();
    showDialog(
      context: ref.context,
      builder: (context) => AlertDialog(
        title: const Text('New Project'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(
            hintText: 'Project name',
          ),
          autofocus: true,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              if (controller.text.isNotEmpty) {
                ref.read(workspaceProvider.notifier).createProject(controller.text);
                Navigator.pop(context);
              }
            },
            child: const Text('Create'),
          ),
        ],
      ),
    );
  }
}
