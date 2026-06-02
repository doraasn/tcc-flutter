import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';

/// ---------------------------------------------------------------------------
/// Settings model (plain Dart class with copyWith – no freezed needed for a
/// flat config object).
/// ---------------------------------------------------------------------------

class AppSettings {
  final String globalPrompt;
  final bool autoConnect;
  final bool showStatusBar;
  final String theme;
  final bool streamingEnabled;
  final bool compactMode;
  final int maxHistoryMessages;

  const AppSettings({
    this.globalPrompt = '',
    this.autoConnect = true,
    this.showStatusBar = true,
    this.theme = 'dark',
    this.streamingEnabled = true,
    this.compactMode = false,
    this.maxHistoryMessages = 200,
  });

  AppSettings copyWith({
    String? globalPrompt,
    bool? autoConnect,
    bool? showStatusBar,
    String? theme,
    bool? streamingEnabled,
    bool? compactMode,
    int? maxHistoryMessages,
  }) {
    return AppSettings(
      globalPrompt: globalPrompt ?? this.globalPrompt,
      autoConnect: autoConnect ?? this.autoConnect,
      showStatusBar: showStatusBar ?? this.showStatusBar,
      theme: theme ?? this.theme,
      streamingEnabled: streamingEnabled ?? this.streamingEnabled,
      compactMode: compactMode ?? this.compactMode,
      maxHistoryMessages: maxHistoryMessages ?? this.maxHistoryMessages,
    );
  }

  Map<String, dynamic> toJson() => {
        'globalPrompt': globalPrompt,
        'autoConnect': autoConnect,
        'showStatusBar': showStatusBar,
        'theme': theme,
        'streamingEnabled': streamingEnabled,
        'compactMode': compactMode,
        'maxHistoryMessages': maxHistoryMessages,
      };

  factory AppSettings.fromJson(Map<String, dynamic> json) => AppSettings(
        globalPrompt: json['globalPrompt'] as String? ?? '',
        autoConnect: json['autoConnect'] as bool? ?? true,
        showStatusBar: json['showStatusBar'] as bool? ?? true,
        theme: json['theme'] as String? ?? 'dark',
        streamingEnabled: json['streamingEnabled'] as bool? ?? true,
        compactMode: json['compactMode'] as bool? ?? false,
        maxHistoryMessages: json['maxHistoryMessages'] as int? ?? 200,
      );
}

/// ---------------------------------------------------------------------------
/// Provider
/// ---------------------------------------------------------------------------

final settingsProvider =
    StateNotifierProvider<SettingsController, AppSettings>((ref) {
  return SettingsController();
});

/// Whether the current theme is dark (convenience read).
final isDarkThemeProvider = Provider<bool>((ref) {
  return ref.watch(settingsProvider).theme == 'dark';
});

class SettingsController extends StateNotifier<AppSettings> {
  SettingsController() : super(const AppSettings()) {
    _loadSettings();
  }

  Box get _box => Hive.box('settings');

  // ---------------------------------------------------------------------------
  // Persistence
  // ---------------------------------------------------------------------------

  void _loadSettings() {
    final json = _box.get('settings');
    if (json is Map) {
      state = AppSettings.fromJson(Map<String, dynamic>.from(json));
    } else {
      // Fallback: read individual keys (migration from older versions).
      state = AppSettings(
        globalPrompt: _box.get('globalPrompt', defaultValue: ''),
        autoConnect: _box.get('autoConnect', defaultValue: true),
        showStatusBar: _box.get('showStatusBar', defaultValue: true),
        theme: _box.get('theme', defaultValue: 'dark'),
      );
    }
  }

  void _save() {
    _box.put('settings', state.toJson());
    // Also write individual keys for backwards compatibility.
    _box.put('globalPrompt', state.globalPrompt);
    _box.put('autoConnect', state.autoConnect);
    _box.put('showStatusBar', state.showStatusBar);
    _box.put('theme', state.theme);
  }

  // ---------------------------------------------------------------------------
  // Mutations
  // ---------------------------------------------------------------------------

  void updateGlobalPrompt(String prompt) {
    state = state.copyWith(globalPrompt: prompt);
    _save();
  }

  void updateAutoConnect(bool value) {
    state = state.copyWith(autoConnect: value);
    _save();
  }

  void updateShowStatusBar(bool value) {
    state = state.copyWith(showStatusBar: value);
    _save();
  }

  void updateTheme(String theme) {
    state = state.copyWith(theme: theme);
    _save();
  }

  void toggleTheme() {
    state = state.copyWith(
      theme: state.theme == 'dark' ? 'light' : 'dark',
    );
    _save();
  }

  void updateStreamingEnabled(bool value) {
    state = state.copyWith(streamingEnabled: value);
    _save();
  }

  void updateCompactMode(bool value) {
    state = state.copyWith(compactMode: value);
    _save();
  }

  void updateMaxHistoryMessages(int value) {
    state = state.copyWith(maxHistoryMessages: value);
    _save();
  }

  /// Reset all settings to defaults.
  void resetToDefaults() {
    state = const AppSettings();
    _save();
  }
}
