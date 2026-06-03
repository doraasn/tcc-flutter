import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_markdown/flutter_markdown.dart';
import '../core/localizations.dart';
import '../core/theme.dart';
import '../models/chat_message.dart';
import 'typing_indicator.dart';

/// Individual chat message bubble with markdown rendering, code highlighting,
/// streaming indicator, error display, and timestamp on long-press.
class MessageBubble extends StatefulWidget {
  final ChatMessage message;

  const MessageBubble({super.key, required this.message});

  @override
  State<MessageBubble> createState() => _MessageBubbleState();
}

class _MessageBubbleState extends State<MessageBubble> {
  bool _showTimestamp = false;

  @override
  Widget build(BuildContext context) {
    final message = widget.message;
    final isUser = message.role == 'user';
    final isError = message.role == 'error';
    final isAssistant = message.role == 'assistant';

    return GestureDetector(
      onLongPress: () {
        setState(() => _showTimestamp = !_showTimestamp);
      },
      child: Padding(
        padding: const EdgeInsets.only(bottom: 12),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (!isUser) ...[
              _buildAvatar(isError),
              const SizedBox(width: 12),
            ],
            Expanded(
              child: Column(
                crossAxisAlignment:
                    isUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                children: [
                  _buildBubble(message, isUser, isError, isAssistant),
                  if (_showTimestamp) ...[
                    const SizedBox(height: 4),
                    _buildTimestamp(message, isUser),
                  ],
                ],
              ),
            ),
            if (isUser) ...[
              const SizedBox(width: 12),
              _buildUserAvatar(),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildAvatar(bool isError) {
    return CircleAvatar(
      radius: 16,
      backgroundColor: isError ? TccColors.error : TccColors.primary,
      child: Icon(
        isError ? Icons.error_outline : Icons.auto_awesome,
        size: 16,
        color: Colors.white,
      ),
    );
  }

  Widget _buildUserAvatar() {
    return CircleAvatar(
      radius: 16,
      backgroundColor: TccColors.surfaceLight,
      child: const Icon(Icons.person, size: 16, color: TccColors.onSurface),
    );
  }

  Widget _buildBubble(
      ChatMessage message, bool isUser, bool isError, bool isAssistant) {
    return Container(
      constraints: BoxConstraints(
        maxWidth: MediaQuery.of(context).size.width * 0.78,
      ),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: isUser ? TccColors.primary.withOpacity(0.1) : TccColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isError
              ? TccColors.error.withOpacity(0.5)
              : isUser
                  ? TccColors.primary.withOpacity(0.3)
                  : TccColors.border,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (isError)
            _buildErrorContent(message)
          else if (isAssistant || isUser)
            _buildMessageContent(message, isUser),
          if (message.isStreaming && message.content.isEmpty) ...[
            const SizedBox(height: 4),
            const TypingIndicator(),
          ],
        ],
      ),
    );
  }

  Widget _buildErrorContent(ChatMessage message) {
    return Row(
      children: [
        const Icon(Icons.error_outline, color: TccColors.error, size: 16),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            message.error ?? message.content,
            style: TccTextStyles.bodyMedium.copyWith(color: TccColors.error),
          ),
        ),
      ],
    );
  }

  Widget _buildMessageContent(ChatMessage message, bool isUser) {
    return MarkdownBody(
      data: message.content,
      selectable: true,
      styleSheet: MarkdownStyleSheet(
        p: TccTextStyles.bodyLarge.copyWith(
          color: isUser ? Colors.white : TccColors.onSurface,
        ),
        strong: TccTextStyles.bodyLarge.copyWith(fontWeight: FontWeight.w700),
        em: TccTextStyles.bodyLarge.copyWith(fontStyle: FontStyle.italic),
        code: TccTextStyles.code.copyWith(
          backgroundColor: TccColors.surfaceLight,
          color: TccColors.primary,
        ),
        codeblockDecoration: BoxDecoration(
          color: const Color(0xFF1A1A2E),
          borderRadius: BorderRadius.circular(8),
          border: Border.all(color: TccColors.border),
        ),
        codeblockPadding: const EdgeInsets.all(12),
        h1: TccTextStyles.titleLarge.copyWith(fontSize: 22),
        h2: TccTextStyles.titleLarge.copyWith(fontSize: 18),
        h3: TccTextStyles.titleMedium.copyWith(fontSize: 16),
        blockquote: TccTextStyles.bodyLarge.copyWith(
          color: TccColors.onSurfaceVariant,
        ),
        blockquoteDecoration: BoxDecoration(
          border: Border(
            left: BorderSide(color: TccColors.primary, width: 3),
          ),
        ),
        blockquotePadding: const EdgeInsets.only(left: 12, top: 4, bottom: 4),
        listBullet: TccTextStyles.bodyLarge.copyWith(color: TccColors.primary),
        a: TccTextStyles.bodyLarge.copyWith(
          color: TccColors.primary,
          decoration: TextDecoration.underline,
        ),
      ),
    );
  }

  Widget _buildTimestamp(ChatMessage message, bool isUser) {
    final time =
        '${message.timestamp.hour.toString().padLeft(2, '0')}:${message.timestamp.minute.toString().padLeft(2, '0')}';
    return GestureDetector(
      onTap: () {
        Clipboard.setData(ClipboardData(text: message.content));
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(AppStrings.copiedToClipboard),
            duration: Duration(seconds: 1),
          ),
        );
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
        decoration: BoxDecoration(
          color: TccColors.surfaceLight.withOpacity(0.5),
          borderRadius: BorderRadius.circular(4),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(time, style: TccTextStyles.caption.copyWith(fontSize: 11)),
            const SizedBox(width: 4),
            const Icon(Icons.copy, size: 12, color: TccColors.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}
