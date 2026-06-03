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

  Map<String, dynamic> toJson() => {
    'projectId': projectId,
    'projectName': projectName,
    'cwd': cwd,
    'openSpecSkills': openSpecSkills,
    'createdAt': createdAt,
  };

  factory WorkspaceState.fromJson(Map<String, dynamic> json) {
    return WorkspaceState(
      projectId: json['projectId'] ?? '',
      projectName: json['projectName'] ?? '',
      cwd: json['cwd'] ?? '',
      openSpecSkills: List<String>.from(json['openSpecSkills'] ?? []),
      createdAt: json['createdAt'] ?? 0,
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

  Map<String, dynamic> toJson() => {
    'isRunning': isRunning,
    'isStarting': isStarting,
    'sessionId': sessionId,
    'error': error,
    'outputBuffer': outputBuffer,
  };

  factory ProcessState.fromJson(Map<String, dynamic> json) {
    return ProcessState(
      isRunning: json['isRunning'] ?? false,
      isStarting: json['isStarting'] ?? false,
      sessionId: json['sessionId'],
      error: json['error'],
      outputBuffer: List<String>.from(json['outputBuffer'] ?? []),
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

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'createdAt': createdAt.toIso8601String(),
    'projectId': projectId,
    'lastMessage': lastMessage,
  };

  factory SessionInfo.fromJson(Map<String, dynamic> json) {
    return SessionInfo(
      id: json['id'] ?? '',
      title: json['title'] ?? '',
      createdAt: DateTime.parse(json['createdAt']),
      projectId: json['projectId'] ?? '',
      lastMessage: json['lastMessage'] ?? '',
    );
  }
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

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'baseUrl': baseUrl,
    'apiKey': apiKey,
    'modelId': modelId,
    'contextLength': contextLength,
    'isActive': isActive,
  };

  factory ModelConfig.fromJson(Map<String, dynamic> json) {
    return ModelConfig(
      id: json['id'] ?? '',
      name: json['name'] ?? '',
      baseUrl: json['baseUrl'] ?? '',
      apiKey: json['apiKey'] ?? '',
      modelId: json['modelId'] ?? '',
      contextLength: json['contextLength'] ?? 200000,
      isActive: json['isActive'] ?? false,
    );
  }
}
