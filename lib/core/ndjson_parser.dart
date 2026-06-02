import 'dart:async';
import 'dart:convert';

/// Represents a single parsed line from the NDJSON stream produced by
/// Claude Code (`--output-format stream-json`).
class NdjsonChunk {
  /// The event type, e.g. "assistant", "result", "error", "system", etc.
  final String type;

  /// The parsed JSON payload.  Shape varies by [type].
  final dynamic data;

  /// Non-null when the event signals an error.
  final String? error;

  /// Session identifier, present on "result" and some "system" events.
  final String? sessionId;

  /// Raw line that produced this chunk (useful for debugging).
  final String raw;

  const NdjsonChunk({
    required this.type,
    this.data,
    this.error,
    this.sessionId,
    this.raw = '',
  });

  factory NdjsonChunk.fromJson(Map<String, dynamic> json, {String raw = ''}) {
    return NdjsonChunk(
      type: json['type'] as String? ?? 'unknown',
      data: json['data'] ?? json['content'],
      error: json['error'] as String?,
      sessionId: json['session_id'] as String?,
      raw: raw,
    );
  }

  /// Convenience accessor: the `content` string from the payload, if present.
  String? get content {
    if (data is String) return data as String;
    if (data is Map<String, dynamic>) return data['content'] as String?;
    return null;
  }

  @override
  String toString() => 'NdjsonChunk(type=$type, sessionId=$sessionId)';
}

/// A stateful NDJSON parser that handles partial lines, encoding errors, and
/// produces a clean [Stream<NdjsonChunk>].
///
/// Usage:
/// ```dart
/// final parser = NdjsonParser();
/// parser.stream.listen((chunk) { ... });
/// // feed raw bytes from process stdout
/// process.stdout.transform(utf8.decoder).listen(parser.feed);
/// // when the stream ends:
/// parser.close();
/// ```
class NdjsonParser {
  /// Internal line buffer — holds partial lines between [feed] calls.
  String _buffer = '';

  /// Whether [close] has been called.
  bool _closed = false;

  /// Cumulative count of successfully parsed lines (for metrics).
  int _parsedCount = 0;

  /// Cumulative count of lines that failed to parse (for metrics).
  int _errorCount = 0;

  final StreamController<NdjsonChunk> _controller =
      StreamController<NdjsonChunk>.broadcast();

  // ---------------------------------------------------------------------------
  // Public API
  // ---------------------------------------------------------------------------

  /// The stream of parsed chunks.  Safe to listen to multiple times
  /// (broadcast controller).
  Stream<NdjsonChunk> get stream => _controller.stream;

  /// Number of lines successfully parsed so far.
  int get parsedCount => _parsedCount;

  /// Number of lines that failed JSON parsing so far.
  int get errorCount => _errorCount;

  /// Whether the parser has been closed.
  bool get isClosed => _closed;

  /// Feed raw text into the parser.  Accepts arbitrary chunks — the parser
  /// splits on newlines internally and buffers incomplete lines.
  void feed(String chunk) {
    if (_closed) return;

    _buffer += chunk;
    _processBuffer();
  }

  /// Flush any remaining buffered data and close the stream.
  ///
  /// If a trailing partial line exists it will be attempted as a final parse
  /// (NDJSON producers sometimes omit a trailing newline).
  void close() {
    if (_closed) return;
    _closed = true;

    _flushBuffer();
    _controller.close();
  }

  /// Reset the parser state so it can be reused for a new process.
  void reset() {
    _buffer = '';
    _closed = false;
    _parsedCount = 0;
    _errorCount = 0;
  }

  // ---------------------------------------------------------------------------
  // Internal helpers
  // ---------------------------------------------------------------------------

  /// Split [_buffer] on newlines and parse every complete line.
  void _processBuffer() {
    // Process all complete lines in the buffer.
    while (_buffer.contains('\n')) {
      final idx = _buffer.indexOf('\n');
      final line = _buffer.substring(0, idx);
      _buffer = _buffer.substring(idx + 1);

      _parseLine(line);
    }
  }

  /// Flush the last partial line when the stream ends.
  void _flushBuffer() {
    final remaining = _buffer.trim();
    if (remaining.isNotEmpty) {
      _parseLine(remaining);
    }
    _buffer = '';
  }

  /// Attempt to JSON-decode a single line and emit the result.
  void _parseLine(String line) {
    final trimmed = line.trim();
    if (trimmed.isEmpty) return;

    try {
      final json = jsonDecode(trimmed);
      if (json is Map<String, dynamic>) {
        _parsedCount++;
        _controller.add(NdjsonChunk.fromJson(json, raw: trimmed));
      } else {
        // Valid JSON but not an object — wrap it.
        _parsedCount++;
        _controller.add(NdjsonChunk(
          type: 'unexpected',
          data: json,
          raw: trimmed,
        ));
      }
    } on FormatException catch (e) {
      _errorCount++;
      _controller.add(NdjsonChunk(
        type: 'parse_error',
        error: e.message,
        raw: trimmed,
      ));
    } catch (e) {
      _errorCount++;
      _controller.add(NdjsonChunk(
        type: 'parse_error',
        error: e.toString(),
        raw: trimmed,
      ));
    }
  }
}
