import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme.dart';
import '../providers/workspace_provider.dart';
import '../providers/process_provider.dart';

class InputBar extends ConsumerStatefulWidget {
  final Function(String) onSend;

  const InputBar({super.key, required this.onSend});

  @override
  ConsumerState<InputBar> createState() => _InputBarState();
}

class _InputBarState extends ConsumerState<InputBar> {
  final _controller = TextEditingController();
  final _focusNode = FocusNode();
  bool _isComposing = false;

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

    return Container(
      padding: const EdgeInsets.all(16),
      decoration: const BoxDecoration(
        color: TccColors.background,
        border: Border(
          top: BorderSide(color: TccColors.border),
        ),
      ),
      child: Column(
        children: [
          if (workspace.openSpecSkills.isNotEmpty) ...[
            _buildOpsxBar(workspace.openSpecSkills),
            const SizedBox(height: 8),
          ],
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
                        ? 'Type your message...'
                        : 'Start a conversation...',
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
                            color: _isComposing ? TccColors.primary : TccColors.onSurfaceVariant,
                          ),
                          onPressed: _isComposing ? _handleSubmit : null,
                        ),
                      ],
                    ),
                  ),
                  onChanged: (text) {
                    setState(() => _isComposing = text.trim().isNotEmpty);
                  },
                  onSubmitted: (_) => _handleSubmit(),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          _buildStatusBar(processState),
        ],
      ),
    );
  }

  Widget _buildOpsxBar(List<String> skills) {
    return SizedBox(
      height: 32,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: skills.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final skill = skills[index];
          return ActionChip(
            label: Text(skill, style: TccTextStyles.caption.copyWith(color: TccColors.primary)),
            backgroundColor: TccColors.primary.withOpacity(0.1),
            side: BorderSide(color: TccColors.primary.withOpacity(0.3)),
            onPressed: () {
              _controller.text = skill;
              _handleSubmit();
            },
          );
        },
      ),
    );
  }

  Widget _buildStatusBar(ProcessState processState) {
    return Row(
      children: [
        Icon(
          processState.isRunning ? Icons.circle : Icons.circle_outlined,
          size: 8,
          color: processState.isRunning ? TccColors.success : TccColors.onSurfaceVariant,
        ),
        const SizedBox(width: 6),
        Text(
          processState.isRunning ? 'Connected' : 'Disconnected',
          style: TccTextStyles.caption,
        ),
        const Spacer(),
        if (processState.sessionId != null)
          Text(
            'Session: ${processState.sessionId!.substring(0, 8)}...',
            style: TccTextStyles.caption,
          ),
      ],
    );
  }

  void _clearInput() {
    _controller.clear();
    setState(() => _isComposing = false);
  }

  void _handleSubmit() {
    final text = _controller.text.trim();
    if (text.isEmpty) return;

    widget.onSend(text);
    _controller.clear();
    setState(() => _isComposing = false);
    _focusNode.requestFocus();
  }
}
