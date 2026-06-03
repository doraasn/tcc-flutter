import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/localizations.dart';
import '../core/theme.dart';
import '../models/workspace_state.dart';
import '../providers/model_provider.dart';
import '../providers/settings_provider.dart';

class SettingsScreen extends ConsumerStatefulWidget {
  final ScrollController? scrollController;

  const SettingsScreen({super.key, this.scrollController});

  @override
  ConsumerState<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends ConsumerState<SettingsScreen> {
  late final TextEditingController _globalPromptController;

  @override
  void initState() {
    super.initState();
    final settings = ref.read(settingsProvider);
    _globalPromptController = TextEditingController(text: settings.globalPrompt);
  }

  @override
  void dispose() {
    _globalPromptController.dispose();
    super.dispose();
  }

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

  Widget _buildModelTile(BuildContext context, WidgetRef ref, ModelConfig model) {
    return ListTile(
      leading: Radio<String>(
        value: model.id,
        groupValue: model.isActive ? model.id : null,
        onChanged: (_) => ref.read(modelProvider.notifier).setActive(model.id),
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
