import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../models/chat_message.dart';
import 'process_provider.dart';

/// ---------------------------------------------------------------------------
/// Providers
/// ---------------------------------------------------------------------------

/// High-level chat controller that wraps the raw message list with
/// convenience methods for adding, clearing, and exporting messages.
final chatProvider =
    StateNotifierProvider<ChatController, List<ChatMessage>>((ref) {
  return ChatController(ref);
});

/// Total number of messages in the current chat.
final chatMessageCountProvider = Provider<int>((ref) {
  return ref.watch(chatProvider).length;
});

/// Whether the chat currently has any messages.
final chatHasMessagesProvider = Provider<bool>((ref) {
  return ref.watch(chatProvider).isNotEmpty;
});

/// The last message in the chat (or null).
final chatLastMessageProvider = Provider<ChatMessage?>((ref) {
  final messages = ref.watch(chatProvider);
  return messages.isEmpty ? null : messages.last;
});

/// ---------------------------------------------------------------------------
/// Controller
/// ---------------------------------------------------------------------------

class ChatController extends StateNotifier<List<ChatMessage>> {
  ChatController(this._ref) : super([]);

  static const _uuid = Uuid();
  final Ref _ref;

  // ---------------------------------------------------------------------------
  // Read helpers
  // ---------------------------------------------------------------------------

  /// All user-authored messages (role == 'user').
  List<ChatMessage> get userMessages =>
      state.where((m) => m.role == 'user').toList();

  /// All assistant responses.
  List<ChatMessage> get assistantMessages =>
      state.where((m) => m.role == 'assistant').toList();

  /// Whether a streaming response is still in progress.
  bool get isStreaming =>
      state.isNotEmpty && state.last.role == 'assistant' && state.last.isStreaming;

  /// Number of tokens (rough estimate: 1 token ~ 4 chars).
  int get estimatedTokenCount {
    var total = 0;
    for (final m in state) {
      total += (m.content.length / 4).ceil();
    }
    return total;
  }

  // ---------------------------------------------------------------------------
  // Mutations
  // ---------------------------------------------------------------------------

  /// Add a user message and send it to the Claude process.
  Future<void> addUserMessage(String content) async {
    if (content.trim().isEmpty) return;

    final message = ChatMessage(
      id: _uuid.v4(),
      role: 'user',
      content: content.trim(),
      timestamp: DateTime.now(),
    );

    state = [...state, message];

    // Forward to the process so it actually reaches Claude.
    final processCtrl = _ref.read(processProvider.notifier);
    await processCtrl.sendInput(content.trim());
  }

  /// Append (or concatenate to) an assistant streaming chunk.
  ///
  /// If the last message is already a streaming assistant message the chunk
  /// is appended; otherwise a new assistant message is created.
  void appendAssistantChunk(String chunk) {
    if (chunk.isEmpty) return;

    final messages = List<ChatMessage>.from(state);
    if (messages.isNotEmpty &&
        messages.last.role == 'assistant' &&
        messages.last.isStreaming) {
      final last = messages.last;
      messages[messages.length - 1] =
          last.copyWith(content: last.content + chunk);
    } else {
      messages.add(ChatMessage(
        id: _uuid.v4(),
        role: 'assistant',
        content: chunk,
        timestamp: DateTime.now(),
        isStreaming: true,
      ));
    }
    state = messages;
  }

  /// Finalise the current streaming assistant message.
  void finaliseStream() {
    if (!isStreaming) return;
    final messages = List<ChatMessage>.from(state);
    messages[messages.length - 1] =
        messages.last.copyWith(isStreaming: false);
    state = messages;
  }

  /// Add a system message (e.g. "Session resumed").
  void addSystemMessage(String content) {
    state = [
      ...state,
      ChatMessage(
        id: _uuid.v4(),
        role: 'system',
        content: content,
        timestamp: DateTime.now(),
      ),
    ];
  }

  /// Add an error message.
  void addErrorMessage(String error, {String? details}) {
    state = [
      ...state,
      ChatMessage(
        id: _uuid.v4(),
        role: 'error',
        content: error,
        timestamp: DateTime.now(),
        error: error,
        metadata: details != null ? {'details': details} : null,
      ),
    ];
  }

  /// Remove a single message by its [id].
  void removeMessage(String id) {
    state = state.where((m) => m.id != id).toList();
  }

  /// Replace the entire message list (e.g. after loading from disk).
  void replaceAll(List<ChatMessage> messages) {
    state = messages;
  }

  /// Clear the entire chat history.
  void clearHistory() {
    state = [];
  }

  // ---------------------------------------------------------------------------
  // Export
  // ---------------------------------------------------------------------------

  /// Export the chat as a Markdown string.
  String exportAsMarkdown({String? title}) {
    final buf = StringBuffer();
    if (title != null) {
      buf.writeln('# $title\n');
    }
    buf.writeln('Exported at: ${DateTime.now().toIso8601String()}\n');
    buf.writeln('---\n');

    for (final m in state) {
      final label = switch (m.role) {
        'user' => 'You',
        'assistant' => 'Claude',
        'system' => 'System',
        'error' => 'Error',
        _ => m.role,
      };
      final time =
          '${m.timestamp.hour.toString().padLeft(2, '0')}:${m.timestamp.minute.toString().padLeft(2, '0')}:${m.timestamp.second.toString().padLeft(2, '0')}';
      buf.writeln('**$label** _($time)_\n');
      buf.writeln('${m.content}\n');
    }

    return buf.toString();
  }

  /// Export the chat as a JSON-serialisable list.
  List<Map<String, dynamic>> exportAsJson() {
    return state.map((m) => m.toJson()).toList();
  }

  /// Export the chat as a raw JSON string.
  String exportAsJsonString({bool pretty = true}) {
    if (pretty) {
      const encoder = JsonEncoder.withIndent('  ');
      return encoder.convert(exportAsJson());
    }
    return jsonEncode(exportAsJson());
  }
}
