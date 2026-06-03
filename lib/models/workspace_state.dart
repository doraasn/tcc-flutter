class WorkspaceState {
  final String projectId;
  final String projectName;
  final String cwd;
  final List<String> openSpecSkills;
  final int createdAt;

  const WorkspaceState({
    this.projectId = '',
    this.projectName = '',
    this.cwd = '',
    this.openSpecSkills = const [],
    this.createdAt = 0,
  });

  WorkspaceState copyWith({
    String? projectId,
    String? projectName,
    String? cwd,
    List<String>? openSpecSkills,
    int? createdAt,
  }) {
    return WorkspaceState(
      projectId: projectId ?? this.projectId,
      projectName: projectName ?? this.projectName,
      cwd: cwd ?? this.cwd,
      openSpecSkills: openSpecSkills ?? this.openSpecSkills,
      createdAt: createdAt ?? this.createdAt,
    );
  }

}

class ProcessState {
  final bool isRunning;
  final bool isStarting;
  final String? sessionId;
  final String? error;
  final List<String> outputBuffer;

  const ProcessState({
    this.isRunning = false,
    this.isStarting = false,
    this.sessionId,
    this.error,
    this.outputBuffer = const [],
  });

  ProcessState copyWith({
    bool? isRunning,
    bool? isStarting,
    String? sessionId,
    String? error,
    List<String>? outputBuffer,
  }) {
    return ProcessState(
      isRunning: isRunning ?? this.isRunning,
      isStarting: isStarting ?? this.isStarting,
      sessionId: sessionId ?? this.sessionId,
      error: error ?? this.error,
      outputBuffer: outputBuffer ?? this.outputBuffer,
    );
  }

}

class SessionInfo {
  final String id;
  final String title;
  final DateTime createdAt;
  final String projectId;
  final String lastMessage;

  const SessionInfo({
    required this.id,
    required this.title,
    required this.createdAt,
    required this.projectId,
    this.lastMessage = '',
  });

}

class ModelConfig {
  final String id;
  final String name;
  final String baseUrl;
  final String apiKey;
  final String modelId;
  final int contextLength;
  final bool isActive;

  const ModelConfig({
    required this.id,
    required this.name,
    required this.baseUrl,
    required this.apiKey,
    required this.modelId,
    this.contextLength = 200000,
    this.isActive = false,
  });

  ModelConfig copyWith({
    String? id,
    String? name,
    String? baseUrl,
    String? apiKey,
    String? modelId,
    int? contextLength,
    bool? isActive,
  }) {
    return ModelConfig(
      id: id ?? this.id,
      name: name ?? this.name,
      baseUrl: baseUrl ?? this.baseUrl,
      apiKey: apiKey ?? this.apiKey,
      modelId: modelId ?? this.modelId,
      contextLength: contextLength ?? this.contextLength,
      isActive: isActive ?? this.isActive,
    );
  }

}
