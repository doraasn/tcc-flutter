import 'dart:convert';
import 'dart:io';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:path/path.dart' as p;
import '../core/constants.dart';
import '../models/workspace_state.dart';

/// ---------------------------------------------------------------------------
/// Providers
/// ---------------------------------------------------------------------------

/// All sessions, optionally filtered by project.
final sessionProvider =
    StateNotifierProvider<SessionController, AsyncValue<List<SessionInfo>>>(
        (ref) {
  return SessionController();
});

/// Sessions grouped by date label (e.g. "Today", "Yesterday", "2025-01-15").
final groupedSessionsProvider = Provider<AsyncValue<Map<String, List<SessionInfo>>>>((ref) {
  final asyncSessions = ref.watch(sessionProvider);
  return asyncSessions.whenData(_groupByDate);
});

/// Sessions filtered by project id.
final projectSessionsProvider =
    Provider.family<AsyncValue<List<SessionInfo>>, String>((ref, projectId) {
  final asyncSessions = ref.watch(sessionProvider);
  return asyncSessions.whenData(
    (sessions) => sessions.where((s) => s.projectId == projectId).toList(),
  );
});

/// ---------------------------------------------------------------------------
/// Grouping helper
/// ---------------------------------------------------------------------------

Map<String, List<SessionInfo>> _groupByDate(List<SessionInfo> sessions) {
  final now = DateTime.now();
  final today = DateTime(now.year, now.month, now.day);
  final yesterday = today.subtract(const Duration(days: 1));

  final grouped = <String, List<SessionInfo>>{};

  for (final session in sessions) {
    final sessionDate = DateTime(
      session.createdAt.year,
      session.createdAt.month,
      session.createdAt.day,
    );

    String label;
    if (!sessionDate.isBefore(today)) {
      label = 'Today';
    } else if (!sessionDate.isBefore(yesterday)) {
      label = 'Yesterday';
    } else {
      label =
          '${session.createdAt.year}-${session.createdAt.month.toString().padLeft(2, '0')}-${session.createdAt.day.toString().padLeft(2, '0')}';
    }

    grouped.putIfAbsent(label, () => []).add(session);
  }

  return grouped;
}

/// ---------------------------------------------------------------------------
/// Controller
/// ---------------------------------------------------------------------------

class SessionController extends StateNotifier<AsyncValue<List<SessionInfo>>> {
  SessionController() : super(const AsyncValue.loading()) {
    loadSessions();
  }

  /// Load all session JSONL files from ~/.claude/projects/.
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
            final info = await _parseSessionFile(entity);
            if (info != null) {
              if (projectId == null || info.projectId == projectId) {
                sessions.add(info);
              }
            }
          } catch (_) {
            // Skip malformed files silently.
          }
        }
      }

      sessions.sort((a, b) => b.createdAt.compareTo(a.createdAt));
      state = AsyncValue.data(sessions);
    } catch (e, st) {
      state = AsyncValue.error(e, st);
    }
  }

  /// Refresh from disk.
  Future<void> refresh({String? projectId}) async {
    await loadSessions(projectId: projectId);
  }

  /// Read the first line of a JSONL session file and extract metadata.
  Future<SessionInfo?> _parseSessionFile(File file) async {
    final firstLine = await file.openRead()
        .transform(utf8.decoder)
        .transform(const LineSplitter())
        .first;
    final json = _parseJson(firstLine);
    if (json == null || !json.containsKey('session_id')) return null;

    // Try to extract the last human message as a preview.
    String lastMessage = '';
    try {
      final allLines = await file.openRead()
          .transform(utf8.decoder)
          .transform(const LineSplitter())
          .toList();
      for (var i = allLines.length - 1; i >= 1; i--) {
        final lineJson = _parseJson(allLines[i]);
        if (lineJson != null && lineJson['type'] == 'human') {
          final msg = lineJson['message'];
          if (msg is Map && msg['content'] is String) {
            lastMessage = (msg['content'] as String).trim();
          } else if (msg is String) {
            lastMessage = msg.trim();
          }
          break;
        }
      }
    } catch (_) {
      // Ignore: lastMessage stays empty.
    }

    // Fall back to file modification time if createdAt is missing.
    DateTime createdAt;
    if (json.containsKey('created_at')) {
      createdAt = DateTime.tryParse(json['created_at'] as String? ?? '') ??
          (await file.stat()).modified;
    } else {
      createdAt = (await file.stat()).modified;
    }

    return SessionInfo(
      id: json['session_id'] ?? p.basenameWithoutExtension(file.path),
      title: json['title'] ?? 'Untitled',
      createdAt: createdAt,
      projectId: json['project_id'] ?? '',
      lastMessage: lastMessage.length > 120
          ? '${lastMessage.substring(0, 120)}...'
          : lastMessage,
    );
  }

  Map<String, dynamic>? _parseJson(String line) {
    try {
      return Map<String, dynamic>.from(jsonDecode(line) as Map);
    } catch (_) {
      return null;
    }
  }
}
