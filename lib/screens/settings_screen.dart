import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/localizations.dart';
import '../core/mcp_manager.dart';
import '../core/theme.dart';
import '../models/workspace_state.dart';
import '../providers/model_provider.dart';
import '../providers/process_provider.dart';
import '../providers/settings_provider.dart';
import '../providers/workspace_provider.dart';
import '../services/version_manager.dart';

// ---------------------------------------------------------------------------
// Providers for version management
// ---------------------------------------------------------------------------

/// Singleton instance of VersionManager (PRootService is itself a singleton).
final versionManagerProvider = Provider<VersionManager>((ref) {
  return VersionManager();
});

/// List of all installed versions, refreshed via [refreshVersionProviders].
final installedVersionsProvider = FutureProvider<List<InstalledVersion>>((ref) {
  final vm = ref.watch(versionManagerProvider);
  return vm.listInstalledVersions();
});

/// Current active version name, refreshed via [refreshVersionProviders].
final currentVersionNameProvider = FutureProvider<String?>((ref) {
  final vm = ref.watch(versionManagerProvider);
  return vm.getCurrentVersionName();
});

/// Helper to refresh both version providers after a mutation.
void refreshVersionProviders(WidgetRef ref) {
  ref.invalidate(installedVersionsProvider);
  ref.invalidate(currentVersionNameProvider);
}

// ---------------------------------------------------------------------------
// Settings screen
// ---------------------------------------------------------------------------

class SettingsScreen extends ConsumerStatefulWidget {
  final ScrollController? scrollController;

  const SettingsScreen({super.key, this.scrollController});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late final TextEditingController _globalPromptController;
  final _mcpManager = McpManager();
  List<McpServer> _mcpServers = [];
  bool _mcpLoading = true;

  // Version management UI state
  bool _installing = false;
  bool _switching = false;
  final _versionInputController = TextEditingController();

  @override
  void initState() {
    super.initState();
    final settings = ref.read(settingsProvider);
    _globalPromptController = TextEditingController(text: settings.globalPrompt);
    _loadMcpServers();
  }

  @override
  void dispose() {
    _globalPromptController.dispose();
    _versionInputController.dispose();
    super.dispose();
  }

  Future<void> _loadMcpServers() async {
    final servers = await _mcpManager.listServers();
    if (mounted) {
      setState(() {
        _mcpServers = servers;
        _mcpLoading = false;
      });
    }
  }

  // ---------------------------------------------------------------------------
  // Version management actions
  // ---------------------------------------------------------------------------

  Future<void> _installVersion(String? constraint) async {
    if (_installing) return;
    setState(() => _installing = true);
    try {
      final vm = ref.read(versionManagerProvider);
      await vm.downloadVersion(
        versionConstraint: constraint?.isEmpty == true ? null : constraint,
      );
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text(AppStrings.versionInstalled)),
        );
        refreshVersionProviders(ref);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${AppStrings.downloadFailed}: $e'),
            backgroundColor: TccColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _installing = false);
    }
  }

  Future<void> _switchVersion(String versionPath) async {
    if (_switching) return;
    setState(() => _switching = true);
    try {
      final vm = ref.read(versionManagerProvider);
      await vm.switchTo(versionPath);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text(AppStrings.versionSwitched)),
        );
        refreshVersionProviders(ref);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${AppStrings.switchFailed}: $e'),
            backgroundColor: TccColors.error,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _switching = false);
    }
  }

  Future<void> _rollback() async {
    final versionsAsync = ref.read(installedVersionsProvider);
    final versions = versionsAsync.valueOrNull;
    if (versions == null || versions.length < 2) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(AppStrings.needTwoVersions),
            backgroundColor: TccColors.error,
          ),
        );
      }
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text(AppStrings.rollback),
        content: Text(
          '${AppStrings.rollbackToPrevious}\n'
          '${versions[0].name} → ${versions[1].name}',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(AppStrings.rollback),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    await _switchVersion(versions[1].path);
  }

  Future<void> _deleteVersion(String versionName) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text(AppStrings.confirmDelete),
        content: Text(AppStrings.confirmDeleteVersion(versionName)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: TccColors.error),
            child: const Text(AppStrings.delete),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      final vm = ref.read(versionManagerProvider);
      await vm.removeVersion(versionName);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text(AppStrings.versionDeleted)),
        );
        refreshVersionProviders(ref);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e'), backgroundColor: TccColors.error),
        );
      }
    }
  }

  Future<void> _pruneVersions() async {
    final versionsAsync = ref.read(installedVersionsProvider);
    final versions = versionsAsync.valueOrNull;
    if (versions == null || versions.length <= 1) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text(AppStrings.noVersionsToPrune)),
        );
      }
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text(AppStrings.pruneOldVersions),
        content: Text(AppStrings.confirmPrune),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: TccColors.error),
            child: const Text(AppStrings.pruneOldVersions),
          ),
        ],
      ),
    );
    if (confirmed != true) return;

    try {
      final vm = ref.read(versionManagerProvider);
      await vm.pruneOldVersions();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text(AppStrings.versionsPruned)),
        );
        refreshVersionProviders(ref);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('$e'), backgroundColor: TccColors.error),
        );
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Build
  // ---------------------------------------------------------------------------

  @override
  Widget build(BuildContext context) {
    final models = ref.watch(modelProvider);
    final settings = ref.watch(settingsProvider);

    // Sync controller text when settings change externally (e.g. reset).
    if (_globalPromptController.text != settings.globalPrompt) {
      _globalPromptController.text = settings.globalPrompt;
    }

    return Container(
      decoration: const BoxDecoration(
        color: TccColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Column(
        children: [
          _buildHandle(),
          _buildHeader(context),
          Expanded(
            child: ListView(
              controller: widget.scrollController,
              children: [
                _buildSection(AppStrings.models, [
                  for (final model in models)
                    _buildModelTile(context, ref, model),
                  _buildAddModelButton(context, ref),
                ]),
                const Divider(),
                _buildSection(AppStrings.globalPrompt, [
                  Padding(
                    padding: const EdgeInsets.all(16),
                    child: TextField(
                      maxLines: 4,
                      decoration: const InputDecoration(
                        hintText: AppStrings.globalPromptHint,
                      ),
                      controller: _globalPromptController,
                      onChanged: (value) {
                        ref.read(settingsProvider.notifier).updateGlobalPrompt(value);
                      },
                    ),
                  ),
                ]),
                const Divider(),
                _buildVersionManagementSection(),
                const Divider(),
                _buildMcpSection(),
                const Divider(),
                _buildSection(AppStrings.about, [
                  ListTile(
                    title: const Text(AppStrings.tccVersion),
                    subtitle: const Text('1.0.0'),
                  ),
                  ListTile(
                    title: const Text(AppStrings.claudeCodeVersion),
                    subtitle: const Text('2.1.153 (bundled)'),
                  ),
                ]),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Handle & Header
  // ---------------------------------------------------------------------------

  Widget _buildHandle() {
    return Center(
      child: Container(
        margin: const EdgeInsets.only(top: 12),
        width: 40,
        height: 4,
        decoration: BoxDecoration(
          color: TccColors.onSurfaceVariant,
          borderRadius: BorderRadius.circular(2),
        ),
      ),
    );
  }

  Widget _buildHeader(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      child: Row(
        children: [
          const Text(AppStrings.settings, style: TccTextStyles.titleLarge),
          const Spacer(),
          IconButton(
            icon: const Icon(Icons.close),
            onPressed: () => Navigator.pop(context),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Section builder
  // ---------------------------------------------------------------------------

  Widget _buildSection(String title, List<Widget> children) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(
            title.toUpperCase(),
            style: TccTextStyles.caption.copyWith(color: TccColors.primary),
          ),
        ),
        ...children,
      ],
    );
  }

  // ---------------------------------------------------------------------------
  // Version Management section
  // ---------------------------------------------------------------------------

  Widget _buildVersionManagementSection() {
    final versionsAsync = ref.watch(installedVersionsProvider);
    final currentNameAsync = ref.watch(currentVersionNameProvider);

    return _buildSection(AppStrings.versionManagement, [
      // Current version indicator
      currentNameAsync.when(
        loading: () => const ListTile(
          leading: SizedBox(
            width: 20,
            height: 20,
            child: CircularProgressIndicator(strokeWidth: 2),
          ),
          title: Text(AppStrings.activeVersion),
        ),
        error: (e, _) => ListTile(
          leading: const Icon(Icons.error_outline, color: TccColors.error),
          title: const Text(AppStrings.activeVersion),
          subtitle: Text('$e', style: const TextStyle(color: TccColors.error)),
        ),
        data: (currentName) => ListTile(
          leading: const Icon(Icons.check_circle, color: TccColors.success),
          title: const Text(AppStrings.activeVersion),
          subtitle: Text(
            currentName ?? AppStrings.noVersionsInstalled,
            style: TccTextStyles.bodyMedium.copyWith(fontFamily: 'monospace'),
          ),
        ),
      ),

      // Install new version
      Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        child: Row(
          children: [
            Expanded(
              child: TextField(
                controller: _versionInputController,
                decoration: InputDecoration(
                  hintText: AppStrings.versionNumberHint,
                  isDense: true,
                  contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 10,
                  ),
                ),
                enabled: !_installing,
              ),
            ),
            const SizedBox(width: 8),
            _installing
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : IconButton(
                    icon: const Icon(Icons.download, color: TccColors.primary),
                    tooltip: AppStrings.installNewVersion,
                    onPressed: () {
                      _installVersion(_versionInputController.text);
                    },
                  ),
          ],
        ),
      ),

      // Installed versions list
      versionsAsync.when(
        loading: () => const Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        ),
        error: (e, _) => ListTile(
          leading: const Icon(Icons.error_outline, color: TccColors.error),
          title: Text('$e'),
        ),
        data: (versions) {
          if (versions.isEmpty) {
            return const ListTile(
              title: Text(AppStrings.noVersionsInstalled),
            );
          }

          return Column(
            children: [
              for (final v in versions) _buildVersionTile(v),
              // Rollback button (only if 2+ versions)
              if (versions.length >= 2)
                ListTile(
                  leading: const Icon(Icons.undo, color: TccColors.primary),
                  title: const Text(AppStrings.rollback),
                  subtitle: const Text(AppStrings.rollbackToPrevious),
                  onTap: _switching ? null : _rollback,
                ),
              // Prune button (only if 2+ versions)
              if (versions.length >= 2)
                ListTile(
                  leading:
                      const Icon(Icons.delete_sweep, color: TccColors.error),
                  title: const Text(AppStrings.pruneOldVersions),
                  onTap: _switching ? null : _pruneVersions,
                ),
            ],
          );
        },
      ),
    ]);
  }

  Widget _buildVersionTile(InstalledVersion version) {
    final isActive = version.isActive;

    return ListTile(
      leading: isActive
          ? const Icon(Icons.check_circle, color: TccColors.success)
          : const Icon(Icons.circle_outlined,
              color: TccColors.onSurfaceVariant),
      title: Row(
        children: [
          Flexible(
            child: Text(
              version.name,
              style: TccTextStyles.bodyMedium.copyWith(fontFamily: 'monospace'),
              overflow: TextOverflow.ellipsis,
            ),
          ),
          if (isActive) ...[
            const SizedBox(width: 8),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
              decoration: BoxDecoration(
                color: TccColors.success.withAlpha(30),
                borderRadius: BorderRadius.circular(4),
                border: Border.all(color: TccColors.success.withAlpha(80)),
              ),
              child: const Text(
                AppStrings.active,
                style: TextStyle(
                  fontSize: 10,
                  color: TccColors.success,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ],
      ),
      subtitle: Text(
        version.path,
        style: TccTextStyles.caption.copyWith(fontFamily: 'monospace'),
        overflow: TextOverflow.ellipsis,
      ),
      trailing: isActive
          ? null
          : Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                if (_switching)
                  const SizedBox(
                    width: 16,
                    height: 16,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                else
                  IconButton(
                    icon: const Icon(Icons.swap_horiz, size: 20),
                    tooltip: AppStrings.switchToVersion,
                    onPressed: () => _switchVersion(version.path),
                  ),
                IconButton(
                  icon: const Icon(Icons.close, size: 20),
                  tooltip: AppStrings.deleteVersion,
                  onPressed: () => _deleteVersion(version.name),
                ),
              ],
            ),
    );
  }

  // ---------------------------------------------------------------------------
  // MCP Servers
  // ---------------------------------------------------------------------------

  Widget _buildMcpSection() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(
            AppStrings.mcpServers.toUpperCase(),
            style: TccTextStyles.caption.copyWith(color: TccColors.primary),
          ),
        ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Text(
            AppStrings.mcpDescription,
            style: TccTextStyles.bodyMedium,
          ),
        ),
        const SizedBox(height: 8),
        if (_mcpLoading)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          )
        else ...[
          if (_mcpServers.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Text(
                AppStrings.noMcpServers,
                style: TccTextStyles.caption,
              ),
            )
          else
            for (final server in _mcpServers)
              _buildMcpServerTile(server),
          _buildAddMcpButton(),
        ],
      ],
    );
  }

  Widget _buildMcpServerTile(McpServer server) {
    return ListTile(
      leading: const Icon(Icons.dns, color: TccColors.primary),
      title: Text(server.name),
      subtitle: Text(
        '${server.command} ${server.args.join(' ')}',
        style: TccTextStyles.caption,
      ),
      trailing: PopupMenuButton(
        itemBuilder: (context) => [
          PopupMenuItem(value: 'edit', child: Text(AppStrings.edit)),
          PopupMenuItem(value: 'delete', child: Text(AppStrings.delete)),
        ],
        onSelected: (value) {
          if (value == 'edit') {
            _showEditMcpDialog(server);
          } else if (value == 'delete') {
            _showDeleteMcpConfirm(server.name);
          }
        },
      ),
    );
  }

  Widget _buildAddMcpButton() {
    return ListTile(
      leading: const Icon(Icons.add, color: TccColors.primary),
      title: const Text(AppStrings.addMcpServer),
      onTap: () => _showAddMcpDialog(),
    );
  }

  void _showAddMcpDialog() {
    final nameController = TextEditingController();
    final commandController = TextEditingController();
    final argsController = TextEditingController();

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text(AppStrings.addMcpServer),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: const InputDecoration(hintText: AppStrings.serverName),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: commandController,
              decoration: const InputDecoration(hintText: AppStrings.commandHint),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: argsController,
              decoration: const InputDecoration(hintText: AppStrings.argsHint),
              maxLines: 3,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () async {
              if (nameController.text.isNotEmpty &&
                  commandController.text.isNotEmpty) {
                final args = argsController.text
                    .split('\n')
                    .map((line) => line.trim())
                    .where((line) => line.isNotEmpty)
                    .toList();
                try {
                  await _mcpManager.addServer(McpServer(
                    name: nameController.text,
                    command: commandController.text,
                    args: args,
                  ));
                  if (mounted) Navigator.pop(context);
                  await _loadMcpServers();
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(e.toString())),
                    );
                  }
                }
              }
            },
            child: const Text(AppStrings.add),
          ),
        ],
      ),
    );
  }

  void _showEditMcpDialog(McpServer server) {
    final nameController = TextEditingController(text: server.name);
    final commandController = TextEditingController(text: server.command);
    final argsController = TextEditingController(
      text: server.args.join('\n'),
    );

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text(AppStrings.editMcpServer),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: const InputDecoration(hintText: AppStrings.serverName),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: commandController,
              decoration: const InputDecoration(hintText: AppStrings.commandHint),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: argsController,
              decoration: const InputDecoration(hintText: AppStrings.argsHint),
              maxLines: 3,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () async {
              if (nameController.text.isNotEmpty &&
                  commandController.text.isNotEmpty) {
                final args = argsController.text
                    .split('\n')
                    .map((line) => line.trim())
                    .where((line) => line.isNotEmpty)
                    .toList();
                try {
                  // If the name changed, remove the old entry first.
                  if (nameController.text != server.name) {
                    await _mcpManager.removeServer(server.name);
                  }
                  await _mcpManager.updateServer(McpServer(
                    name: nameController.text,
                    command: commandController.text,
                    args: args,
                  ));
                  if (mounted) Navigator.pop(context);
                  await _loadMcpServers();
                } catch (e) {
                  if (mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(e.toString())),
                    );
                  }
                }
              }
            },
            child: const Text(AppStrings.save),
          ),
        ],
      ),
    );
  }

  void _showDeleteMcpConfirm(String serverName) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text(AppStrings.deleteMcpServer),
        content: Text(AppStrings.deleteMcpServerConfirm(serverName)),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () async {
              await _mcpManager.removeServer(serverName);
              if (mounted) Navigator.pop(context);
              await _loadMcpServers();
            },
            child: const Text(AppStrings.delete),
          ),
        ],
      ),
    );
  }

  // ---------------------------------------------------------------------------
  // Model management
  // ---------------------------------------------------------------------------

  Widget _buildModelTile(BuildContext context, WidgetRef ref, ModelConfig model) {
    return ListTile(
      leading: Radio<String>(
        value: model.id,
        groupValue: model.isActive ? model.id : null,
        onChanged: (_) {
          ref.read(modelProvider.notifier).setActive(model.id);
          // Hot-switch the running process to the newly selected model.
          final processState = ref.read(processProvider);
          if (processState.isRunning) {
            final workspace = ref.read(workspaceProvider);
            ref.read(processProvider.notifier).hotSwitch(
              projectId: workspace.projectId,
              cwd: workspace.cwd,
              baseUrl: model.baseUrl,
              apiKey: model.apiKey,
              modelId: model.modelId,
            );
          }
        },
        activeColor: TccColors.primary,
      ),
      title: Text(model.name),
      subtitle: Text(
        '${model.modelId}\n${model.baseUrl}',
        style: TccTextStyles.caption,
      ),
      isThreeLine: true,
      trailing: PopupMenuButton(
        itemBuilder: (context) => [
          PopupMenuItem(
            value: 'edit',
            child: Text(AppStrings.edit),
          ),
          PopupMenuItem(
            value: 'delete',
            child: Text(AppStrings.delete),
          ),
        ],
        onSelected: (value) {
          if (value == 'edit') {
            _showEditModelDialog(context, ref, model);
          } else if (value == 'delete') {
            ref.read(modelProvider.notifier).deleteModel(model.id);
          }
        },
      ),
    );
  }

  Widget _buildAddModelButton(BuildContext context, WidgetRef ref) {
    return ListTile(
      leading: const Icon(Icons.add, color: TccColors.primary),
      title: const Text(AppStrings.addModel),
      onTap: () => _showAddModelDialog(context, ref),
    );
  }

  void _showAddModelDialog(BuildContext context, WidgetRef ref) {
    final nameController = TextEditingController();
    final baseUrlController = TextEditingController();
    final apiKeyController = TextEditingController();
    final modelIdController = TextEditingController();

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text(AppStrings.addModel),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: const InputDecoration(hintText: AppStrings.name),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: baseUrlController,
              decoration: const InputDecoration(hintText: AppStrings.baseUrl),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: apiKeyController,
              decoration: const InputDecoration(hintText: AppStrings.apiKey),
              obscureText: true,
            ),
            const SizedBox(height: 8),
            TextField(
              controller: modelIdController,
              decoration: const InputDecoration(hintText: AppStrings.modelId),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () {
              if (nameController.text.isNotEmpty &&
                  baseUrlController.text.isNotEmpty &&
                  modelIdController.text.isNotEmpty) {
                ref.read(modelProvider.notifier).addModel(ModelConfig(
                  id: DateTime.now().millisecondsSinceEpoch.toString(),
                  name: nameController.text,
                  baseUrl: baseUrlController.text,
                  apiKey: apiKeyController.text,
                  modelId: modelIdController.text,
                ));
                Navigator.pop(context);
              }
            },
            child: const Text(AppStrings.add),
          ),
        ],
      ),
    );
  }

  void _showEditModelDialog(BuildContext context, WidgetRef ref, ModelConfig model) {
    final nameController = TextEditingController(text: model.name);
    final baseUrlController = TextEditingController(text: model.baseUrl);
    final apiKeyController = TextEditingController(text: model.apiKey);
    final modelIdController = TextEditingController(text: model.modelId);

    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text(AppStrings.editModel),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: nameController,
              decoration: const InputDecoration(hintText: AppStrings.name),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: baseUrlController,
              decoration: const InputDecoration(hintText: AppStrings.baseUrl),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: apiKeyController,
              decoration: const InputDecoration(hintText: AppStrings.apiKey),
              obscureText: true,
            ),
            const SizedBox(height: 8),
            TextField(
              controller: modelIdController,
              decoration: const InputDecoration(hintText: AppStrings.modelId),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text(AppStrings.cancel),
          ),
          TextButton(
            onPressed: () {
              ref.read(modelProvider.notifier).updateModel(model.copyWith(
                name: nameController.text,
                baseUrl: baseUrlController.text,
                apiKey: apiKeyController.text,
                modelId: modelIdController.text,
              ));
              Navigator.pop(context);
            },
            child: const Text(AppStrings.save),
          ),
        ],
      ),
    );
  }
}
