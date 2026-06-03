class ChatMessage {
  final String id;
  final String role;
  final String content;
  final DateTime timestamp;
  final bool isStreaming;
  final String? error;
  final Map<String, dynamic>? metadata;

  const ChatMessage({
    required this.id,
    required this.role,
    required this.content,
    required this.timestamp,
    this.isStreaming = false,
    this.error,
    this.metadata,
  });

  bool get isUser => role == 'user';

  ChatMessage copyWith({
    String? id,
    String? role,
    String? content,
    DateTime? timestamp,
    bool? isStreaming,
    String? error,
    Map<String, dynamic>? metadata,
  }) {
    return ChatMessage(
      id: id ?? this.id,
      role: role ?? this.role,
      content: content ?? this.content,
      timestamp: timestamp ?? this.timestamp,
      isStreaming: isStreaming ?? this.isStreaming,
      error: error ?? this.error,
      metadata: metadata ?? this.metadata,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'role': role,
    'content': content,
    'timestamp': timestamp.toIso8601String(),
    'isStreaming': isStreaming,
    'error': error,
    'metadata': metadata,
  };

  factory ChatMessage.fromJson(Map<String, dynamic> json) {
    return ChatMessage(
      id: json['id'] ?? '',
      role: json['role'] ?? '',
      content: json['content'] ?? '',
      timestamp: DateTime.parse(json['timestamp']),
      isStreaming: json['isStreaming'] ?? false,
      error: json['error'],
      metadata: json['metadata'],
    );
  }
}

class SlashCommand {
  final String name;
  final String description;
  final String category;

  const SlashCommand({
    required this.name,
    required this.description,
    this.category = '',
  });

}

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
