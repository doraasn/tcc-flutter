import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import '../core/theme.dart';
import '../models/chat_message.dart';
import '../providers/process_provider.dart';
import 'input_bar.dart';

class ChatArea extends ConsumerStatefulWidget {
  final Function(String) onSendMessage;

  const ChatArea({super.key, required this.onSendMessage});

  @override
  ConsumerState<ChatArea> createState() => _ChatAreaState();
}

class _ChatAreaState extends ConsumerState<ChatArea> {
  final _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: const Duration(milliseconds: 200),
          curve: Curves.easeOut,
        );
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final messages = ref.watch(chatMessagesProvider);

    ref.listen<List<ChatMessage>>(chatMessagesProvider, (prev, next) {
      if (next.length > (prev?.length ?? 0)) {
        _scrollToBottom();
      }
    });

    return Column(
      children: [
        Expanded(
          child: messages.isEmpty
              ? _buildWelcomeScreen()
              : _buildMessageList(messages),
        ),
        InputBar(
          onSend: widget.onSendMessage,
        ),
      ],
    );
  }

  Widget _buildWelcomeScreen() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            Icons.auto_awesome,
            size: 64,
            color: TccColors.primary.withOpacity(0.3),
          ),
          const SizedBox(height: 24),
          Text(
            'TCC',
            style: TccTextStyles.titleLarge.copyWith(
              color: TccColors.onSurface,
              fontSize: 24,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            'AI-Native Mobile IDE Workspace',
            style: TccTextStyles.caption.copyWith(fontSize: 14),
          ),
          const SizedBox(height: 32),
          _buildQuickActions(),
        ],
      ),
    );
  }

  Widget _buildQuickActions() {
    return Wrap(
      spacing: 8,
      runSpacing: 8,
      children: [
        _buildQuickAction('New Project', Icons.add, () {
          // TODO: Show new project dialog
        }),
        _buildQuickAction('Open Project', Icons.folder_open, () {
          // TODO: Show project picker
        }),
        _buildQuickAction('Settings', Icons.settings, () {
          // TODO: Show settings
        }),
      ],
    );
  }

  Widget _buildQuickAction(String label, IconData icon, VoidCallback onTap) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        decoration: BoxDecoration(
          color: TccColors.surface,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: TccColors.border),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 18, color: TccColors.primary),
            const SizedBox(width: 8),
            Text(label, style: TccTextStyles.bodyMedium),
          ],
        ),
      ),
    );
  }

  Widget _buildMessageList(List<ChatMessage> messages) {
    return ListView.builder(
      controller: _scrollController,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      itemCount: messages.length,
      itemBuilder: (context, index) {
        return _buildMessageBubble(messages[index]);
      },
    );
  }

  Widget _buildMessageBubble(ChatMessage message) {
    final isUser = message.role == 'user';
    final isError = message.role == 'error';

    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (!isUser) ...[
            CircleAvatar(
              radius: 16,
              backgroundColor: isError ? TccColors.error : TccColors.primary,
              child: Icon(
                isError ? Icons.error : Icons.auto_awesome,
                size: 16,
                color: Colors.white,
              ),
            ),
            const SizedBox(width: 12),
          ],
          Expanded(
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: isUser ? TccColors.primary.withOpacity(0.1) : TccColors.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isUser ? TccColors.primary.withOpacity(0.3) : TccColors.border,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (isError)
                    Text(
                      message.error ?? 'Error',
                      style: TccTextStyles.bodyMedium.copyWith(color: TccColors.error),
                    )
                  else
                    MarkdownBody(
                      data: message.content,
                      styleSheet: MarkdownStyleSheet(
                        p: TccTextStyles.bodyLarge,
                        code: TccTextStyles.code.copyWith(
                          backgroundColor: TccColors.surfaceLight,
                        ),
                        codeblockDecoration: BoxDecoration(
                          color: TccColors.surfaceLight,
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                    ),
                  if (message.isStreaming) ...[
                    const SizedBox(height: 8),
                    const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    ),
                  ],
                ],
              ),
            ),
          ),
          if (isUser) ...[
            const SizedBox(width: 12),
            CircleAvatar(
              radius: 16,
              backgroundColor: TccColors.surfaceLight,
              child: const Icon(Icons.person, size: 16),
            ),
          ],
        ],
      ),
    );
  }
}
