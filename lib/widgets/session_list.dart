import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';
import '../core/theme.dart';
import '../core/localizations.dart';
import '../models/workspace_state.dart';
import '../providers/session_provider.dart';

/// Session history grouped by date (Today, Yesterday, Older).
/// Displays sessions in a scrollable list with date section headers.
class SessionList extends ConsumerWidget {
  final Function(String sessionId)? onSessionSelected;
  final bool showSearch;

  const SessionList({
    super.key,
    this.onSessionSelected,
    this.showSearch = false,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final sessionsAsync = ref.watch(sessionProvider);

    return sessionsAsync.when(
      data: (sessions) {
        if (sessions.isEmpty) {
          return const Center(
            child: Padding(
              padding: EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(Icons.chat_bubble_outline,
                      size: 48, color: TccColors.onSurfaceVariant),
                  SizedBox(height: 12),
                  Text(AppStrings.noSessions, style: TccTextStyles.caption),
                  SizedBox(height: 4),
                  Text(
                    AppStrings.startConversation,
                    style: TccTextStyles.caption,
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          );
        }

        final grouped = _groupByDate(sessions);
        return _buildGroupedList(context, grouped);
      },
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: CircularProgressIndicator(),
        ),
      ),
      error: (e, _) => Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text('Error: $e', style: TccTextStyles.caption),
        ),
      ),
    );
  }

  Map<String, List<SessionInfo>> _groupByDate(List<SessionInfo> sessions) {
    final now = DateTime.now();
    final today = DateTime(now.year, now.month, now.day);
    final yesterday = today.subtract(const Duration(days: 1));

    final Map<String, List<SessionInfo>> grouped = {};
    final List<SessionInfo> older = [];

    for (final session in sessions) {
      final sessionDate = DateTime(
        session.createdAt.year,
        session.createdAt.month,
        session.createdAt.day,
      );

      if (sessionDate.isAtSameMomentAs(today)) {
        grouped.putIfAbsent(AppStrings.today, () => []).add(session);
      } else if (sessionDate.isAtSameMomentAs(yesterday)) {
        grouped.putIfAbsent(AppStrings.yesterday, () => []).add(session);
      } else {
        older.add(session);
      }
    }

    if (older.isNotEmpty) {
      grouped[AppStrings.older] = older;
    }

    return grouped;
  }

  Widget _buildGroupedList(
      BuildContext context, Map<String, List<SessionInfo>> grouped) {
    final sections = grouped.entries.toList();

    return ListView.builder(
      padding: const EdgeInsets.symmetric(vertical: 8),
      itemCount: sections.length,
      itemBuilder: (context, sectionIndex) {
        final section = sections[sectionIndex];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            _buildSectionHeader(section.key, section.value.length),
            for (final session in section.value)
              _buildSessionTile(context, session),
          ],
        );
      },
    );
  }

  Widget _buildSectionHeader(String title, int count) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
      child: Row(
        children: [
          Text(
            title.toUpperCase(),
            style: TccTextStyles.caption.copyWith(
              color: TccColors.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(width: 8),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
            decoration: BoxDecoration(
              color: TccColors.surfaceLight,
              borderRadius: BorderRadius.circular(8),
            ),
            child: Text(
              '$count',
              style: TccTextStyles.caption.copyWith(fontSize: 10),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSessionTile(BuildContext context, SessionInfo session) {
    final timeFormat = DateFormat('HH:mm');
    final dateFormat = DateFormat('MMM d');
    final time = timeFormat.format(session.createdAt);
    final date = dateFormat.format(session.createdAt);

    return ListTile(
      dense: true,
      leading: const Icon(
        Icons.chat_bubble_outline,
        size: 18,
        color: TccColors.onSurfaceVariant,
      ),
      title: Text(
        session.title,
        style: TccTextStyles.bodyMedium,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        '${session.projectId} - $date $time',
        style: TccTextStyles.caption,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: const Icon(
        Icons.chevron_right,
        size: 18,
        color: TccColors.onSurfaceVariant,
      ),
      onTap: () => onSessionSelected?.call(session.id),
    );
  }
}
