import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme.dart';
import '../core/localizations.dart';
import '../models/chat_message.dart';
import '../models/workspace_state.dart';
import '../providers/workspace_provider.dart';
import '../providers/session_provider.dart';
import '../providers/process_provider.dart';
import '../providers/settings_provider.dart';
import '../widgets/sidebar.dart';
import '../widgets/chat_area.dart';
import '../widgets/project_picker.dart';
import '../widgets/command_palette.dart';
import '../widgets/opsx_bar.dart';
import 'settings_screen.dart';

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  bool _sidebarOpen = false;
  bool _initialized = false;
  bool _commandPaletteOpen = false;
  String _commandQuery = '';

  @override
  void initState() {
    super.initState();
    _initialize();
    // Register keyboard shortcuts after frame renders
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _registerShortcuts();
    });
  }

  Future<void> _initialize() async {
    if (_initialized) return;
    _initialized = true;
    await ref.read(processProvider.notifier).initialize();
  }

  void _registerShortcuts() {
    HardwareKeyboard.instance.addHandler(_handleKeyEvent);
  }

  bool _handleKeyEvent(KeyEvent event) {
    if (event is! KeyDownEvent) return false;

    final isCtrl = HardwareKeyboard.instance.isControlPressed ||
        HardwareKeyboard.instance.isMetaPressed;

    if (isCtrl && event.logicalKey == LogicalKeyboardKey.keyN) {
      _showNewProjectDialog();
      return true;
    }

    if (isCtrl && event.logicalKey == LogicalKeyboardKey.keyK) {
      _toggleCommandPalette();
      return true;
    }

    if (event.logicalKey == LogicalKeyboardKey.escape) {
      if (_commandPaletteOpen) {
        setState(() => _commandPaletteOpen = false);
        return true;
      }
      if (_sidebarOpen) {
        setState(() => _sidebarOpen = false);
        return true;
      }
    }

    return false;
  }

  @override
  Widget build(BuildContext context) {
    final workspace = ref.watch(workspaceProvider);
    final processState = ref.watch(processProvider);

    return Scaffold(
      body: Stack(
        children: [
          Row(
            children: [
              if (_sidebarOpen)
                Sidebar(
                  onClose: () => setState(() => _sidebarOpen = false),
                  onProjectSelected: (projectId) {
                    ref.read(workspaceProvider.notifier).switchProject(projectId);
                    setState(() => _sidebarOpen = false);
                  },
                ),
              Expanded(
                child: Column(
                  children: [
                    _buildAppBar(workspace, processState),
                    if (workspace.openSpecSkills.isNotEmpty)
                      OpsxBar(
                        skills: workspace.openSpecSkills,
                        onSkillSelected: _handleSendMessage,
                      ),
                    Expanded(
                      child: ChatArea(
                        onSendMessage: _handleSendMessage,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (_commandPaletteOpen)
            _buildCommandPaletteOverlay(workspace),
        ],
      ),
    );
  }

  Widget _buildAppBar(WorkspaceState workspace, ProcessState processState) {
    return Container(
      height: 56,
      padding: const EdgeInsets.symmetric(horizontal: 16),
      decoration: const BoxDecoration(
        color: TccColors.surface,
        border: Border(
          bottom: BorderSide(color: TccColors.border),
        ),
      ),
      child: Row(
        children: [
          IconButton(
            icon: Icon(
              _sidebarOpen ? Icons.menu_open : Icons.menu,
              color: TccColors.onSurface,
            ),
            onPressed: () => setState(() => _sidebarOpen = !_sidebarOpen),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  workspace.projectName.isNotEmpty ? workspace.projectName : AppStrings.appName,
                  style: TccTextStyles.titleMedium,
                ),
                if (workspace.openSpecSkills.isNotEmpty)
                  Text(
                    AppStrings.opsxEnabled,
                    style: TccTextStyles.caption.copyWith(color: TccColors.primary),
                  ),
              ],
            ),
          ),
          if (processState.isStarting)
            const SizedBox(
              width: 16,
              height: 16,
              child: CircularProgressIndicator(strokeWidth: 2),
            ),
          if (processState.isRunning)
            const SizedBox(
              width: 8,
              height: 8,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  color: TccColors.success,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          const SizedBox(width: 8),
          IconButton(
            icon: const Icon(Icons.stop_circle, color: TccColors.error),
            onPressed: processState.isRunning
                ? () => ref.read(processProvider.notifier).kill()
                : null,
            tooltip: AppStrings.stopProcess,
          ),
          IconButton(
            icon: const Icon(Icons.folder_open, color: TccColors.onSurfaceVariant),
            onPressed: () => ProjectPicker.show(context),
            tooltip: AppStrings.projects,
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            onPressed: () => _openSettings(),
            tooltip: AppStrings.settings,
          ),
        ],
      ),
    );
  }

  Widget _buildCommandPaletteOverlay(WorkspaceState workspace) {
    return GestureDetector(
      onTap: () => setState(() => _commandPaletteOpen = false),
      child: Container(
        color: Colors.black.withValues(alpha: 0.3),
        child: Center(
          child: GestureDetector(
            onTap: () {}, // Prevent tap from closing
            child: CommandPalette(
              commands: CommandPalette.defaultCommands,
              query: _commandQuery,
              onSelected: (command) {
                setState(() => _commandPaletteOpen = false);
                _handleSendMessage(command);
              },
              onDismiss: () {
                setState(() => _commandPaletteOpen = false);
              },
            ),
          ),
        ),
      ),
    );
  }

  void _handleSendMessage(String text) {
    if (text.startsWith('/clear')) {
      ref.read(processProvider.notifier).clearSession();
      return;
    }

    final workspace = ref.read(workspaceProvider);
    if (workspace.projectId.isEmpty) return;

    final process = ref.read(processProvider.notifier);
    if (!process.isRunning) {
      process.start(
        projectId: workspace.projectId,
        cwd: workspace.cwd,
        prompt: text,
      );
    } else {
      process.sendInput(text);
    }
  }

  void _toggleCommandPalette() {
    setState(() {
      _commandPaletteOpen = !_commandPaletteOpen;
      _commandQuery = '';
    });
  }

  void _showNewProjectDialog() {
    ProjectPicker.show(context);
  }

  void _openSettings() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.9,
        maxChildSize: 0.95,
        minChildSize: 0.5,
        builder: (context, scrollController) => SettingsScreen(
          scrollController: scrollController,
        ),
      ),
    );
  }
}
