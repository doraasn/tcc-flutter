import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../core/theme.dart';
import '../providers/process_provider.dart';

/// A scrollable debug log panel that shows process stdout/stderr/errors.
class LogPanel extends ConsumerStatefulWidget {
  const LogPanel({super.key});

  @override
  ConsumerState<LogPanel> createState() => _LogPanelState();
}

class _LogPanelState extends ConsumerState<LogPanel> {
  final _scrollController = ScrollController();

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.jumpTo(
          _scrollController.position.maxScrollExtent,
        );
      }
    });
  }

  Color _colorForLevel(String level) {
    switch (level) {
      case 'ERROR':
        return TccColors.error;
      case 'STDERR':
        return const Color(0xFFF59E0B);
      case 'STDOUT':
        return TccColors.onSurfaceVariant;
      default:
        return TccColors.onSurface;
    }
  }

  void _copyAll(List<LogEntry> logs) {
    final text = logs.map((e) => e.toString()).join('\n');
    Clipboard.setData(ClipboardData(text: text));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('已复制全部日志'), duration: Duration(seconds: 1)),
    );
  }

  void _copyLine(LogEntry entry) {
    Clipboard.setData(ClipboardData(text: entry.toString()));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('已复制该行'), duration: Duration(seconds: 1)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final logs = ref.watch(debugLogProvider);

    ref.listen<List<LogEntry>>(debugLogProvider, (prev, next) {
      if (next.length > (prev?.length ?? 0)) {
        _scrollToBottom();
      }
    });

    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF1A1A2E),
        border: Border(
          top: BorderSide(color: TccColors.border),
        ),
      ),
      child: Column(
        children: [
          // Header
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
            decoration: const BoxDecoration(
              color: TccColors.surface,
              border: Border(
                bottom: BorderSide(color: TccColors.border),
              ),
            ),
            child: Row(
              children: [
                const Icon(Icons.bug_report, size: 14, color: TccColors.primary),
                const SizedBox(width: 6),
                Text(
                  '调试日志 (${logs.length})',
                  style: TccTextStyles.caption.copyWith(
                    color: TccColors.primary,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const Spacer(),
                InkWell(
                  onTap: logs.isEmpty ? null : () => _copyAll(logs),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    child: Text(
                      '复制全部',
                      style: TccTextStyles.caption.copyWith(color: TccColors.primary),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                InkWell(
                  onTap: () => ref.read(debugLogProvider.notifier).clear(),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    child: Text(
                      '清空',
                      style: TccTextStyles.caption.copyWith(color: TccColors.error),
                    ),
                  ),
                ),
              ],
            ),
          ),
          // Log lines
          Expanded(
            child: logs.isEmpty
                ? const Center(
                    child: Text(
                      '暂无日志',
                      style: TccTextStyles.caption,
                    ),
                  )
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    itemCount: logs.length,
                    itemBuilder: (context, index) {
                      final entry = logs[index];
                      return InkWell(
                        onTap: () => _copyLine(entry),
                        child: Padding(
                          padding: const EdgeInsets.symmetric(vertical: 1, horizontal: 4),
                          child: Text(
                            entry.toString(),
                            style: TextStyle(
                              fontSize: 11,
                              fontFamily: 'monospace',
                              color: _colorForLevel(entry.level),
                              height: 1.3,
                            ),
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
