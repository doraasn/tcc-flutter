import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../core/theme.dart';
import '../core/localizations.dart';
import '../models/chat_message.dart';

/// Slash command autocomplete popup that appears when user types "/".
/// Provides filtered suggestions based on typed text.
class CommandPalette extends StatefulWidget {
  final List<SlashCommand> commands;
  final String query;
  final ValueChanged<String> onSelected;
  final VoidCallback onDismiss;

  const CommandPalette({
    super.key,
    required this.commands,
    required this.query,
    required this.onSelected,
    required this.onDismiss,
  });

  /// Default slash commands available in the app.
  static const defaultCommands = [
    SlashCommand(
      name: '/clear',
      description: AppStrings.cmdClear,
      category: AppStrings.generalCategory,
    ),
    SlashCommand(
      name: '/help',
      description: AppStrings.cmdHelp,
      category: AppStrings.generalCategory,
    ),
    SlashCommand(
      name: '/compact',
      description: AppStrings.cmdCompact,
      category: AppStrings.generalCategory,
    ),
    SlashCommand(
      name: '/model',
      description: AppStrings.cmdModel,
      category: AppStrings.settings,
    ),
    SlashCommand(
      name: '/status',
      description: AppStrings.cmdStatus,
      category: AppStrings.generalCategory,
    ),
    SlashCommand(
      name: '/stop',
      description: AppStrings.cmdStop,
      category: AppStrings.generalCategory,
    ),
    SlashCommand(
      name: '/cost',
      description: AppStrings.cmdCost,
      category: AppStrings.generalCategory,
    ),
    SlashCommand(
      name: '/project',
      description: AppStrings.cmdProject,
      category: AppStrings.settings,
    ),
  ];

  @override
  State<CommandPalette> createState() => _CommandPaletteState();
}

class _CommandPaletteState extends State<CommandPalette> {
  int _selectedIndex = 0;
  final _scrollController = ScrollController();

  List<SlashCommand> get _filteredCommands {
    if (widget.query.isEmpty) return widget.commands;
    final lower = widget.query.toLowerCase();
    return widget.commands
        .where((c) => c.name.toLowerCase().contains(lower))
        .toList();
  }

  @override
  void didUpdateWidget(CommandPalette oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.query != oldWidget.query) {
      _selectedIndex = 0;
    }
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _handleKey(KeyEvent event) {
    if (event is! KeyDownEvent && event is! KeyRepeatEvent) return;

    final commands = _filteredCommands;
    if (commands.isEmpty) return;

    if (event.logicalKey == LogicalKeyboardKey.arrowDown) {
      setState(() {
        _selectedIndex = (_selectedIndex + 1) % commands.length;
      });
      _scrollToSelected();
    } else if (event.logicalKey == LogicalKeyboardKey.arrowUp) {
      setState(() {
        _selectedIndex =
            (_selectedIndex - 1 + commands.length) % commands.length;
      });
      _scrollToSelected();
    } else if (event.logicalKey == LogicalKeyboardKey.enter) {
      widget.onSelected(commands[_selectedIndex].name);
    } else if (event.logicalKey == LogicalKeyboardKey.escape) {
      widget.onDismiss();
    }
  }

  void _scrollToSelected() {
    final offset = _selectedIndex * 52.0;
    _scrollController.animateTo(
      offset,
      duration: const Duration(milliseconds: 150),
      curve: Curves.easeOut,
    );
  }

  @override
  Widget build(BuildContext context) {
    final commands = _filteredCommands;

    if (commands.isEmpty) {
      return const SizedBox.shrink();
    }

    return KeyboardListener(
      focusNode: FocusNode()..requestFocus(),
      onKeyEvent: _handleKey,
      child: Container(
        constraints: BoxConstraints(
          maxHeight: 280,
          maxWidth: 320,
        ),
        decoration: BoxDecoration(
          color: TccColors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: TccColors.border),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.3),
              blurRadius: 12,
              offset: const Offset(0, 4),
            ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            _buildHeader(),
            const Divider(height: 1),
            Flexible(
              child: ListView.builder(
                controller: _scrollController,
                shrinkWrap: true,
                padding: const EdgeInsets.symmetric(vertical: 4),
                itemCount: commands.length,
                itemBuilder: (context, index) {
                  return _buildCommandItem(commands[index], index);
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildHeader() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
      child: Row(
        children: [
          const Icon(Icons.terminal, size: 14, color: TccColors.primary),
          const SizedBox(width: 6),
          Text(
            AppStrings.commands,
            style: TccTextStyles.caption.copyWith(
              color: TccColors.onSurface,
              fontWeight: FontWeight.w600,
            ),
          ),
          const Spacer(),
          Text(
            '${_filteredCommands.length} ${AppStrings.available}',
            style: TccTextStyles.caption.copyWith(fontSize: 10),
          ),
        ],
      ),
    );
  }

  Widget _buildCommandItem(SlashCommand command, int index) {
    final isSelected = index == _selectedIndex;

    return InkWell(
      onTap: () => widget.onSelected(command.name),
      onHover: (hovering) {
        if (hovering) {
          setState(() => _selectedIndex = index);
        }
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        color: isSelected ? TccColors.primary.withOpacity(0.1) : null,
        child: Row(
          children: [
            Text(
              command.name,
              style: TccTextStyles.bodyMedium.copyWith(
                color: TccColors.primary,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                command.description,
                style: TccTextStyles.caption,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (command.category != null)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: TccColors.surfaceLight,
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  command.category!,
                  style: TccTextStyles.caption.copyWith(fontSize: 9),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
