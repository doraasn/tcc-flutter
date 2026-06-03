import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../models/chat_message.dart';
import 'process_provider.dart';

/// ---------------------------------------------------------------------------
/// Providers
/// ---------------------------------------------------------------------------

/// High-level chat controller that wraps the raw [chatMessagesProvider] with
/// convenience methods for adding, clearing, and exporting messages.
///
/// All mutations are written through to [chatMessagesProvider] so that any
/// widget watching that provider (e.g. [ChatArea]) sees updates immediately.
final chatProvider =
    StateNotifierProvider<ChatController, List<ChatMessage>>((ref) {
  return ChatController(ref);
});

/// Whether the chat currently has any messages.
final chatHasMessagesProvider = Provider<bool>((ref) {
  return ref.watch(chatProvider).isNotEmpty;
});

/// ---------------------------------------------------------------------------
/// Controller
/// ---------------------------------------------------------------------------

/// Manages the chat message list with a rich API.
///
/// Internally every write goes through to [chatMessagesProvider] so that
/// existing widgets that watch that provider continue to work.
class ChatController extends StateNotifier<List<ChatMessage>> {
  ChatController(this._ref) : super([]) {
    // Bootstrap from whatever the raw provider currently holds.
    state = List<ChatMessage>.from(_ref.read(chatMessagesProvider));

    // Mirror any changes the process provider makes directly.
    _ref.listen<List<ChatMessage>>(chatMessagesProvider, (prev, next) {
      // Avoid feedback loops: only update if the lists differ by reference
      // or contents.  The _sync() call in our own methods temporarily
      // writes the same list back, so the listener sees the same object
      // and skips the assignment.
      if (!identical(state, next)) {
        state = List<ChatMessage>.from(next);
      }
    });
  }

  static const _uuid = Uuid();
  final Ref _ref;

  // ---------------------------------------------------------------------------
  // Private: keep chatMessagesProvider in sync
  // ---------------------------------------------------------------------------

  /// Push the current [state] into [chatMessagesProvider] so widgets rebuild.
  void _sync() {
    _ref.read(chatMessagesProvider.notifier).state = state;
  }

  // ---------------------------------------------------------------------------
  // Read helpers
  // ---------------------------------------------------------------------------

  /// Whether a streaming response is still in progress.
  bool get isStreaming =>
      state.isNotEmpty &&
      state.last.role == 'assistant' &&
      state.last.isStreaming;

  // ---------------------------------------------------------------------------
  // Mutations
  // ---------------------------------------------------------------------------

  /// Add a user message and forward it to the Claude process.
  Future<void> addUserMessage(String content) async {
    if (content.trim().isEmpty) return;

    final message = ChatMessage(
      id: _uuid.v4(),
      role: 'user',
      content: content.trim(),
      timestamp: DateTime.now(),
    );

    state = [...state, message];
    _sync();

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
    _sync();
  }

  /// Finalise the current streaming assistant message.
  void finaliseStream() {
    if (!isStreaming) return;
    final messages = List<ChatMessage>.from(state);
    messages[messages.length - 1] =
        messages.last.copyWith(isStreaming: false);
    state = messages;
    _sync();
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
    _sync();
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
    _sync();
  }

  /// Clear the entire chat history.
  void clearHistory() {
    state = [];
    _sync();
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
          '${m.timestamp.hour.toString().padLeft(2, '0')}:'
          '${m.timestamp.minute.toString().padLeft(2, '0')}:'
          '${m.timestamp.second.toString().padLeft(2, '0')}';
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
