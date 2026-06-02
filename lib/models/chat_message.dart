import 'package:freezed_annotation/freezed_annotation.dart';

part 'chat_message.freezed.dart';
part 'chat_message.g.dart';

/// Roles a message can take in the chat.
enum MessageRole {
  user,
  assistant,
  system,
  error,
}

@freezed
class ChatMessage with _$ChatMessage {
  const factory ChatMessage({
    required String id,
    required String role,
    required String content,
    required DateTime timestamp,
    @Default(false) bool isStreaming,
    String? error,
    @Default({}) Map<String, dynamic>? metadata,
  }) = _ChatMessage;

  factory ChatMessage.fromJson(Map<String, dynamic> json) =>
      _$ChatMessageFromJson(json);

  /// Convenience getters that parse the role string.
  bool get isUser => role == 'user';
  bool get isAssistant => role == 'assistant';
  bool get isSystem => role == 'system';
  bool get isError => role == 'error';
}

@freezed
class SlashCommand with _$SlashCommand {
  const factory SlashCommand({
    required String name,
    required String description,
    String? category,
  }) = _SlashCommand;

  factory SlashCommand.fromJson(Map<String, dynamic> json) =>
      _$SlashCommandFromJson(json);
}

/// Built-in slash commands available in the chat.
class SlashCommands {
  SlashCommands._();

  static const all = <SlashCommand>[
    SlashCommand(name: 'help', description: 'Show available commands', category: 'general'),
    SlashCommand(name: 'clear', description: 'Clear chat history', category: 'general'),
    SlashCommand(name: 'export', description: 'Export chat as markdown', category: 'general'),
    SlashCommand(name: 'model', description: 'Switch active model', category: 'model'),
    SlashCommand(name: 'models', description: 'List available models', category: 'model'),
    SlashCommand(name: 'sessions', description: 'List past sessions', category: 'session'),
    SlashCommand(name: 'resume', description: 'Resume a previous session', category: 'session'),
    SlashCommand(name: 'project', description: 'Switch project', category: 'project'),
    SlashCommand(name: 'projects', description: 'List all projects', category: 'project'),
    SlashCommand(name: 'settings', description: 'Open settings', category: 'general'),
    SlashCommand(name: 'theme', description: 'Switch theme (dark/light)', category: 'general'),
    SlashCommand(name: 'compact', description: 'Compact conversation context', category: 'session'),
  ];

  /// Parse a slash command from user input. Returns null if input is not a command.
  static SlashCommand? parse(String input) {
    final trimmed = input.trim();
    if (!trimmed.startsWith('/')) return null;
    final name = trimmed.split(RegExp(r'\s+')).first.substring(1).toLowerCase();
    try {
      return all.firstWhere((cmd) => cmd.name == name);
    } catch (_) {
      return null;
    }
  }
}
