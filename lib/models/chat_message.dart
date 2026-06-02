import 'package:freezed_annotation/freezed_annotation.dart';

part 'chat_message.freezed.dart';
part 'chat_message.g.dart';

@freezed
class ChatMessage with _$ChatMessage {
  const factory ChatMessage({
    required String id,
    required String role,
    required String content,
    required DateTime timestamp,
    @Default(false) bool isStreaming,
    String? error,
    Map<String, dynamic>? metadata,
  }) = _ChatMessage;

  factory ChatMessage.fromJson(Map<String, dynamic> json) =>
      _$ChatMessageFromJson(json);
}

@freezed
class SlashCommand with _$SlashCommand {
  const factory SlashCommand({
    required String name,
    required String description,
    String? category,
  }) = _SlashCommand;
}
