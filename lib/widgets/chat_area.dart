import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/localizations.dart';
import '../core/theme.dart';
import '../models/chat_message.dart';
import '../providers/workspace_provider.dart';
import '../providers/process_provider.dart';
import '../screens/settings_screen.dart';
import '../widgets/project_picker.dart';
import 'input_bar.dart';
import 'message_bubble.dart';

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
            AppStrings.appName,
            style: TccTextStyles.titleLarge.copyWith(
              color: TccColors.onSurface,
              fontSize: 24,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            AppStrings.tagline,
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
        _buildQuickAction(AppStrings.newProject, Icons.add, () {
          ProjectPicker.show(context);
        }),
        _buildQuickAction(AppStrings.openProject, Icons.folder_open, () {
          ProjectPicker.show(context);
        }),
        _buildQuickAction(AppStrings.settings, Icons.settings, () {
          _openSettings();
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
        return MessageBubble(message: messages[index]);
      },
    );
  }

  void _openSettings() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      builder: (context) => DraggableScrollableSheet(
        initialChildSize: 0.9,
        maxChildSize: 0.95,
        minChildSize: 0.5,
        builder: (context, scrollController) => SettingsScreen(scrollController: scrollController),
      ),
    );
  }
}
