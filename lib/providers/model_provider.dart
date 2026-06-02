import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';
import '../models/workspace_state.dart';

final modelProvider = StateNotifierProvider<ModelController, List<ModelConfig>>((ref) {
  return ModelController();
});

class ModelController extends StateNotifier<List<ModelConfig>> {
  ModelController() : super([]) {
    _loadModels();
  }

  Box get _box => Hive.box('models');

  void _loadModels() {
    final modelsJson = _box.get('models', defaultValue: []) as List;
    state = modelsJson.map((m) => ModelConfig(
      id: m['id'] ?? '',
      name: m['name'] ?? '',
      baseUrl: m['baseUrl'] ?? '',
      apiKey: m['apiKey'] ?? '',
      modelId: m['modelId'] ?? '',
      contextLength: m['contextLength'] ?? 200000,
      isActive: m['isActive'] ?? false,
    )).toList();

    if (state.isEmpty) {
      _addDefaults();
    }
  }

  void _addDefaults() {
    final defaults = [
      const ModelConfig(
        id: 'mimo',
        name: 'Mimo v2.5',
        baseUrl: 'https://api.siliconflow.cn/v1',
        apiKey: '',
        modelId: 'XiaomiMiMo/MiMo-7B-RL',
        isActive: true,
      ),
      const ModelConfig(
        id: 'deepseek',
        name: 'DeepSeek R1',
        baseUrl: 'https://api.deepseek.com/v1',
        apiKey: '',
        modelId: 'deepseek-reasoner',
      ),
      const ModelConfig(
        id: 'anthropic',
        name: 'Claude Sonnet 4.6',
        baseUrl: 'https://api.anthropic.com/v1',
        apiKey: '',
        modelId: 'claude-sonnet-4-6-20250514',
      ),
    ];

    state = defaults;
    _saveModels();
  }

  void addModel(ModelConfig model) {
    state = [...state, model];
    _saveModels();
  }

  void updateModel(ModelConfig model) {
    state = state.map((m) => m.id == model.id ? model : m).toList();
    _saveModels();
  }

  void deleteModel(String id) {
    state = state.where((m) => m.id != id).toList();
    _saveModels();
  }

  void setActive(String id) {
    state = state.map((m) => m.copyWith(isActive: m.id == id)).toList();
    _saveModels();
  }

  ModelConfig get activeModel {
    return state.firstWhere(
      (m) => m.isActive,
      orElse: () => state.first,
    );
  }

  void _saveModels() {
    final modelsJson = state.map((m) => {
      'id': m.id,
      'name': m.name,
      'baseUrl': m.baseUrl,
      'apiKey': m.apiKey,
      'modelId': m.modelId,
      'contextLength': m.contextLength,
      'isActive': m.isActive,
    }).toList();
    _box.put('models', modelsJson);
  }
}
