import 'dart:convert';
import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;
import '../core/constants.dart';
import '../models/workspace_state.dart';

final sessionProvider = StateNotifierProvider<SessionController, AsyncValue<List<SessionInfo>>>((ref) {
  return SessionController();
});

class SessionController extends StateNotifier<AsyncValue<List<SessionInfo>>> {
  SessionController() : super(const AsyncValue.loading()) {
    loadSessions();
  }

  Future<void> loadSessions({String? projectId}) async {
    state = const AsyncValue.loading();
    try {
      final sessionsDir = await TccPaths.sessionsDir();
      final dir = Directory(sessionsDir);
      if (!await dir.exists()) {
        state = const AsyncValue.data([]);
        return;
      }

      final sessions = <SessionInfo>[];
      await for (final entity in dir.list(recursive: true)) {
        if (entity is File && entity.path.endsWith('.jsonl')) {
          try {
            final firstLine = await entity.openRead()
                .transform(const LineSplitter())
                .first;
            final json = _parseJson(firstLine);
            if (json != null && json.containsKey('session_id')) {
              final stat = await entity.stat();
              sessions.add(SessionInfo(
                id: json['session_id'] ?? p.basenameWithoutExtension(entity.path),
                title: json['title'] ?? 'Untitled',
                createdAt: stat.modified,
                projectId: json['project_id'] ?? '',
              ));
            }
          } catch (_) {}
        }
      }

      sessions.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      state = AsyncValue.data(sessions);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  Map<String, dynamic>? _parseJson(String line) {
    try {
      return Map<String, dynamic>.from(jsonDecode(line) as Map);
    } catch (_) {
      return null;
    }
  }
}
