import 'dart:async';
import 'dart:convert';

class NdjsonChunk {
  final String type;
  final dynamic data;
  final String? error;
  final String? sessionId;

  NdjsonChunk({required this.type, this.data, this.error, this.sessionId});

  factory NdjsonChunk.fromJson(Map<String, dynamic> json) {
    return NdjsonChunk(
      type: json['type'] ?? 'unknown',
      data: json['data'],
      error: json['error'],
      sessionId: json['session_id'],
    );
  }
}

class NdjsonParser {
  String _buffer = '';
  final StreamController<NdjsonChunk> _controller = StreamController<NdjsonChunk>();

  Stream<NdjsonChunk> get stream => _controller.stream;

  void feed(String chunk) {
    _buffer += chunk;
    final lines = _buffer.split('\n');
    _buffer = lines.removeLast();

    for (final line in lines) {
      if (line.trim().isEmpty) continue;
      try {
        final json = jsonDecode(line) as Map<String, dynamic>;
        _controller.add(NdjsonChunk.fromJson(json));
      } catch (e) {
        _controller.add(NdjsonChunk(type: 'parse_error', error: line));
      }
    }
  }

  void close() {
    if (_buffer.trim().isNotEmpty) {
      try {
        final json = jsonDecode(_buffer) as Map<String, dynamic>;
        _controller.add(NdjsonChunk.fromJson(json));
      } catch (e) {
        _controller.add(NdjsonChunk(type: 'parse_error', error: _buffer));
      }
    }
    _controller.close();
  }
}
