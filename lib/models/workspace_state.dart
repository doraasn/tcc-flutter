import 'package:freezed_annotation/freezed_annotation.dart';

part 'workspace_state.freezed.dart';
part 'workspace_state.g.dart';

@freezed
class WorkspaceState with _$WorkspaceState {
  const factory WorkspaceState({
    @Default('') String projectId,
    @Default('') String projectName,
    @Default('') String cwd,
    @Default([]) List<String> openSpecSkills,
    @Default(0) int createdAt,
  }) = _WorkspaceState;

  factory WorkspaceState.fromJson(Map<String, dynamic> json) =>
      _$WorkspaceStateFromJson(json);
}

@freezed
class ProcessState with _$ProcessState {
  const factory ProcessState({
    @Default(false) bool isRunning,
    @Default(false) bool isStarting,
    String? sessionId,
    String? error,
    @Default([]) List<String> outputBuffer,
  }) = _ProcessState;

  factory ProcessState.fromJson(Map<String, dynamic> json) =>
      _$ProcessStateFromJson(json);
}

@freezed
class SessionInfo with _$SessionInfo {
  const factory SessionInfo({
    required String id,
    required String title,
    required DateTime createdAt,
    required String projectId,
    @Default('') String lastMessage,
  }) = _SessionInfo;

  factory SessionInfo.fromJson(Map<String, dynamic> json) =>
      _$SessionInfoFromJson(json);
}

@freezed
class ModelConfig with _$ModelConfig {
  const factory ModelConfig({
    required String id,
    required String name,
    required String baseUrl,
    required String apiKey,
    required String modelId,
    @Default(200000) int contextLength,
    @Default(false) bool isActive,
  }) = _ModelConfig;

  factory ModelConfig.fromJson(Map<String, dynamic> json) =>
      _$ModelConfigFromJson(json);
}
