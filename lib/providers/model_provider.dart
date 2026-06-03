import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';
import 'package:uuid/uuid.dart';
import '../models/workspace_state.dart';

/// ---------------------------------------------------------------------------
/// Providers
/// ---------------------------------------------------------------------------

/// List of all configured models.
final modelProvider =
    StateNotifierProvider<ModelController, List<ModelConfig>>((ref) {
  return ModelController();
});

/// The currently active model (convenience read).
final activeModelProvider = Provider<ModelConfig>((ref) {
  final models = ref.watch(modelProvider);
  return models.firstWhere(
    (m) => m.isActive,
    orElse: () => models.first,
  );
});

/// ---------------------------------------------------------------------------
/// Controller
/// ---------------------------------------------------------------------------

class ModelController extends StateNotifier<List<ModelConfig>> {
  ModelController() : super([]) {
    _loadModels();
  }

  static const _uuid = Uuid();
  Box get _box => Hive.box('models');

  // ---------------------------------------------------------------------------
  // Persistence
  // ---------------------------------------------------------------------------

  void _loadModels() {
    final modelsJson = _box.get('models', defaultValue: []) as List;
    state = modelsJson
        .map((m) => ModelConfig(
              id: m['id'] ?? '',
              name: m['name'] ?? '',
              baseUrl: m['baseUrl'] ?? '',
              apiKey: m['apiKey'] ?? '',
              modelId: m['modelId'] ?? '',
              contextLength: m['contextLength'] ?? 200000,
              isActive: m['isActive'] ?? false,
            ))
        .toList();

    if (state.isEmpty) {
      _addDefaults();
    }
  }

  void _saveModels() {
    final modelsJson = state
        .map((m) => {
              'id': m.id,
              'name': m.name,
              'baseUrl': m.baseUrl,
              'apiKey': m.apiKey,
              'modelId': m.modelId,
              'contextLength': m.contextLength,
              'isActive': m.isActive,
            })
        .toList();
    _box.put('models', modelsJson);
  }

  // ---------------------------------------------------------------------------
  // Default presets
  // ---------------------------------------------------------------------------

  void _addDefaults() {
    state = const [
      ModelConfig(
        id: 'mimo',
        name: 'Mimo v2.5',
        baseUrl: 'https://api.siliconflow.cn/v1',
        apiKey: '',
        modelId: 'XiaomiMiMo/MiMo-7B-RL',
        contextLength: 128000,
        isActive: true,
      ),
      ModelConfig(
        id: 'deepseek',
        name: 'DeepSeek R1',
        baseUrl: 'https://api.deepseek.com/v1',
        apiKey: '',
        modelId: 'deepseek-reasoner',
        contextLength: 64000,
      ),
      ModelConfig(
        id: 'anthropic',
        name: 'Claude Sonnet 4.6',
        baseUrl: 'https://api.anthropic.com/v1',
        apiKey: '',
        modelId: 'claude-sonnet-4-6-20250514',
        contextLength: 200000,
      ),
    ];
    _saveModels();
  }

  // ---------------------------------------------------------------------------
  // CRUD
  // ---------------------------------------------------------------------------

  /// Add a new model. If [setActive] is true the new model becomes the active
  /// one and all others are deactivated.
  void addModel(ModelConfig model, {bool setActive = false}) {
    final newModel = model.id.isEmpty
        ? model.copyWith(id: _uuid.v4())
        : model;

    if (setActive) {
      state = [
        ...state.map((m) => m.copyWith(isActive: false)),
        newModel.copyWith(isActive: true),
      ];
    } else {
      state = [...state, newModel];
    }
    _saveModels();
  }

  /// Replace a model by its id.
  void updateModel(ModelConfig model) {
    state = state.map((m) => m.id == model.id ? model : m).toList();
    _saveModels();
  }

  /// Remove a model. If the removed model was active the first remaining
  /// model becomes active.
  void deleteModel(String id) {
    final wasActive = state.any((m) => m.id == id && m.isActive);
    var remaining = state.where((m) => m.id != id).toList();
    if (wasActive && remaining.isNotEmpty) {
      remaining = remaining.asMap().entries.map((entry) {
        if (entry.key == 0) return entry.value.copyWith(isActive: true);
        return entry.value.copyWith(isActive: false);
      }).toList();
    }
    state = remaining;
    _saveModels();
  }

  // ---------------------------------------------------------------------------
  // Active model
  // ---------------------------------------------------------------------------

  /// Set the model with [id] as the active one. All others are deactivated.
  void setActive(String id) {
    state = state.map((m) => m.copyWith(isActive: m.id == id)).toList();
    _saveModels();
  }

  /// Update the API key for a specific model.
  void updateApiKey(String id, String apiKey) {
    state = state.map((m) {
      if (m.id == id) return m.copyWith(apiKey: apiKey);
      return m;
    }).toList();
    _saveModels();
  }

  /// Update the base URL for a specific model.
  void updateBaseUrl(String id, String baseUrl) {
    state = state.map((m) {
      if (m.id == id) return m.copyWith(baseUrl: baseUrl);
      return m;
    }).toList();
    _saveModels();
  }

  /// Returns the active [ModelConfig].
  ModelConfig get activeModel {
    return state.firstWhere(
      (m) => m.isActive,
      orElse: () => state.first,
    );
  }

  /// Find a model by its id.
  ModelConfig? getById(String id) {
    try {
      return state.firstWhere((m) => m.id == id);
    } catch (_) {
      return null;
    }
  }
}
