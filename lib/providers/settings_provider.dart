import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive/hive.dart';

class AppSettings {
  final String globalPrompt;
  final bool autoConnect;
  final bool showStatusBar;
  final String theme;

  const AppSettings({
    this.globalPrompt = '',
    this.autoConnect = true,
    this.showStatusBar = true,
    this.theme = 'dark',
  });

  AppSettings copyWith({
    String? globalPrompt,
    bool? autoConnect,
    bool? showStatusBar,
    String? theme,
  }) {
    return AppSettings(
      globalPrompt: globalPrompt ?? this.globalPrompt,
      autoConnect: autoConnect ?? this.autoConnect,
      showStatusBar: showStatusBar ?? this.showStatusBar,
      theme: theme ?? this.theme,
    );
  }
}

final settingsProvider = StateNotifierProvider<SettingsController, AppSettings>((ref) {
  return SettingsController();
});

class SettingsController extends StateNotifier<AppSettings> {
  SettingsController() : super(const AppSettings()) {
    _loadSettings();
  }

  Box get _box => Hive.box('settings');

  void _loadSettings() {
    state = AppSettings(
      globalPrompt: _box.get('globalPrompt', defaultValue: ''),
      autoConnect: _box.get('autoConnect', defaultValue: true),
      showStatusBar: _box.get('showStatusBar', defaultValue: true),
      theme: _box.get('theme', defaultValue: 'dark'),
    );
  }

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

  void _save() {
    _box.put('globalPrompt', state.globalPrompt);
    _box.put('autoConnect', state.autoConnect);
    _box.put('showStatusBar', state.showStatusBar);
    _box.put('theme', state.theme);
  }
}
