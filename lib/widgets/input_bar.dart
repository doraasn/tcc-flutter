import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/localizations.dart';
import '../core/theme.dart';
import '../models/workspace_state.dart';
import '../providers/workspace_provider.dart';
import '../providers/process_provider.dart';
import 'command_palette.dart';

class InputBar extends ConsumerStatefulWidget {
  final Function(String) onSend;

  const InputBar({
    super.key,
    required this.onSend,
  });

  @override
  ConsumerState<InputBar> createState() => _InputBarState();
}

class _InputBarState extends ConsumerState<InputBar> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  bool _isComposing = false;
  bool _showCommandPalette = false;
  String _slashQuery = '';

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final workspace = ref.watch(workspaceProvider);
    final processState = ref.watch(processProvider);

    return Column(
      children: [
        if (_showCommandPalette)
          CommandPalette(
            commands: CommandPalette.defaultCommands,
            query: _slashQuery,
            onSelected: _handleCommandSelected,
            onDismiss: () {
              setState(() => _showCommandPalette = false);
            },
          ),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: const BoxDecoration(
            color: TccColors.background,
            border: Border(
              top: BorderSide(color: TccColors.border),
            ),
          ),
          child: Column(
            children: [
              Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _controller,
                      focusNode: _focusNode,
                      maxLines: 4,
                      minLines: 1,
                      decoration: InputDecoration(
                        hintText: processState.isRunning
                            ? AppStrings.inputMessageHint
                            : AppStrings.startConversationHint,
                        suffixIcon: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            if (_isComposing)
                              IconButton(
                                icon: const Icon(Icons.clear, size: 20),
                                onPressed: _clearInput,
                              ),
                            IconButton(
                              icon: Icon(
                                Icons.send,
                                color: _isComposing
                                    ? TccColors.primary
                                    : TccColors.onSurfaceVariant,
                              ),
                              onPressed: _isComposing ? _handleSubmit : null,
                            ),
                          ],
                        ),
                      ),
                      onChanged: _handleTextChanged,
                      onSubmitted: (_) => _handleSubmit(),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              _buildStatusBar(processState),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildStatusBar(ProcessState processState) {
    return Row(
      children: [
        Icon(
          processState.isRunning ? Icons.circle : Icons.circle_outlined,
          size: 8,
          color:
              processState.isRunning ? TccColors.success : TccColors.onSurfaceVariant,
        ),
        const SizedBox(width: 6),
        Text(
          processState.isRunning ? AppStrings.connected : AppStrings.disconnected,
          style: TccTextStyles.caption,
        ),
        const Spacer(),
        if (processState.sessionId != null)
          Text(
            '${AppStrings.sessionPrefix}${processState.sessionId!.length > 8 ? processState.sessionId!.substring(0, 8) : processState.sessionId!}...',
            style: TccTextStyles.caption,
          ),
      ],
    );
  }

  void _handleTextChanged(String text) {
    setState(() => _isComposing = text.trim().isNotEmpty);

    // Detect slash command trigger
    if (text == '/') {
      setState(() {
        _showCommandPalette = true;
        _slashQuery = '';
      });
    } else if (text.startsWith('/') && _showCommandPalette) {
      setState(() => _slashQuery = text);
    } else if (!text.startsWith('/') && _showCommandPalette) {
      setState(() => _showCommandPalette = false);
    }
  }

  void _handleCommandSelected(String command) {
    _controller.clear();
    setState(() {
      _showCommandPalette = false;
      _isComposing = false;
    });
    widget.onSend(command);
    _focusNode.requestFocus();
  }

  void _clearInput() {
    _controller.clear();
    setState(() {
      _isComposing = false;
      _showCommandPalette = false;
    });
  }

  void _handleSubmit() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;

    widget.onSend(text);
    _controller.clear();
    setState(() {
      _isComposing = false;
      _showCommandPalette = false;
    });
    _focusNode.requestFocus();
  }
}
