import 'package:freezed_annotation/freezed_annotation.dart';

part 'workspace_state.freezed.dart';

@freezed
class WorkspaceState with _$WorkspaceState {
  const factory WorkspaceState({
    @Default('') String projectId,
    @Default('') String projectName,
    @Default('') String cwd,
    @Default([]) List<String> openSpecSkills,
  }) = _WorkspaceState;
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
}

@freezed
class SessionInfo with _$SessionInfo {
  const factory SessionInfo({
    required String id,
    required String title,
    required DateTime createdAt,
    required String projectId,
  }) = _SessionInfo;
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
}
