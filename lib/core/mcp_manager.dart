import 'dart:convert';
import 'dart:io';

import 'package:path/path.dart' as p;

import 'constants.dart';

/// Represents a single MCP (Model Context Protocol) server configuration
/// entry in the Claude Desktop config file.
class McpServer {
  /// Human-readable name for this server.
  final String name;

  /// The command to launch (e.g. `npx`, `node`, `/usr/bin/python3`).
  final String command;

  /// Arguments passed to [command].
  final List<String> args;

  /// Extra environment variables injected into the server process.
  final Map<String, String> env;

  const McpServer({
    required this.name,
    required this.command,
    this.args = const [],
    this.env = const {},
  });

  /// Parse from the JSON object stored under `mcpServers.<name>`.
  factory McpServer.fromJson(String name, Map<String, dynamic> json) {
    return McpServer(
      name: name,
      command: json['command'] as String? ?? '',
      args: (json['args'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          const [],
      env: (json['env'] as Map<String, dynamic>?)
              ?.map((k, v) => MapEntry(k, v.toString())) ??
          const {},
    );
  }

  /// Serialise to the JSON object expected under `mcpServers.<name>`.
  Map<String, dynamic> toJson() {
    final map = <String, dynamic>{
      'command': command,
    };
    if (args.isNotEmpty) {
      map['args'] = args;
    }
    if (env.isNotEmpty) {
      map['env'] = env;
    }
    return map;
  }

  @override
  String toString() => 'McpServer(name=$name, command=$command)';
}

/// Manages the Claude Desktop / MCP configuration file.
///
/// The config file lives at `~/.config/Claude/claude_desktop_config.json`
/// inside the proot rootfs and has the following shape:
///
/// ```json
/// {
///   "mcpServers": {
///     "my-server": {
///       "command": "npx",
///       "args": ["-y", "@example/mcp-server"],
///       "env": { "API_KEY": "..." }
///     }
///   }
/// }
/// ```
class McpManager {
  // ---------------------------------------------------------------------------
  // Config file location
  // ---------------------------------------------------------------------------

  Future<File> _getConfigFile() async {
    final configPath = await TccPaths.mcpConfig;
    return File(configPath);
  }

  // ---------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------

  /// Reads the full config file and returns the parsed JSON, or a default
  /// empty config if the file does not exist.
  Future<Map<String, dynamic>> readConfig() async {
    final file = await _getConfigFile();
    if (!await file.exists()) {
      return {'mcpServers': <String, dynamic>{}};
    }

    try {
      final content = await file.readAsString();
      if (content.trim().isEmpty) {
        return {'mcpServers': <String, dynamic>{}};
      }
      final decoded = jsonDecode(content);
      if (decoded is Map<String, dynamic>) {
        return decoded;
      }
      // Corrupted file -- return default.
      return {'mcpServers': <String, dynamic>{}};
    } on FormatException {
      // Corrupted file -- return default.
      return {'mcpServers': <String, dynamic>{}};
    }
  }

  /// Returns all configured MCP servers as a list of [McpServer] objects.
  Future<List<McpServer>> listServers() async {
    final config = await readConfig();
    final serversMap = config['mcpServers'] as Map<String, dynamic>? ?? {};

    return serversMap.entries.map((entry) {
      final name = entry.key;
      final value = entry.value;
      if (value is Map<String, dynamic>) {
        return McpServer.fromJson(name, value);
      }
      // Malformed entry -- skip.
      return null;
    }).whereType<McpServer>().toList();
  }

  // ---------------------------------------------------------------------------
  // Write
  // ---------------------------------------------------------------------------

  /// Adds (or replaces) an MCP server configuration.
  ///
  /// Validates the server configuration before writing.  Throws a
  /// [McpValidationException] if the server is invalid.
  Future<void> addServer(McpServer server) async {
    _validateServer(server);

    final config = await readConfig();
    final serversMap =
        config['mcpServers'] as Map<String, dynamic>? ?? {};
    serversMap[server.name] = server.toJson();
    config['mcpServers'] = serversMap;

    await _writeConfig(config);
  }

  /// Removes an MCP server by name.  No-op if the server does not exist.
  Future<void> removeServer(String name) async {
    final config = await readConfig();
    final serversMap =
        config['mcpServers'] as Map<String, dynamic>? ?? {};

    if (!serversMap.containsKey(name)) return;

    serversMap.remove(name);
    config['mcpServers'] = serversMap;

    await _writeConfig(config);
  }

  /// Alias for [addServer] -- updates an existing server or creates a new one.
  Future<void> updateServer(McpServer server) async {
    await addServer(server);
  }

  // ---------------------------------------------------------------------------
  // Validation
  // ---------------------------------------------------------------------------

  /// Validates a single server configuration.
  void _validateServer(McpServer server) {
    if (server.name.trim().isEmpty) {
      throw McpValidationException('Server name cannot be empty');
    }
    if (server.command.trim().isEmpty) {
      throw McpValidationException(
        'Server "${server.name}": command cannot be empty',
      );
    }
    // Names must be valid JSON keys (no control chars, etc.).
    if (server.name.contains(RegExp(r'[\x00-\x1f]'))) {
      throw McpValidationException(
        'Server name contains invalid characters',
      );
    }
  }

  // ---------------------------------------------------------------------------
  // File I/O
  // ---------------------------------------------------------------------------

  /// Atomically writes the config file.  Writes to a temporary file first,
  /// then renames into place to avoid partial writes.
  Future<void> _writeConfig(Map<String, dynamic> config) async {
    final file = await _getConfigFile();
    await file.parent.create(recursive: true);

    final content =
        const JsonEncoder.withIndent('  ').convert(config);

    // Atomic write: temp file + rename.
    final tempFile = File('${file.path}.tmp');
    try {
      await tempFile.writeAsString(content, flush: true);

      // On Linux, rename is atomic for same-filesystem moves.
      final result = await Process.run('mv', ['-f', tempFile.path, file.path]);
      if (result.exitCode != 0) {
        // Fallback: direct write.
        await file.writeAsString(content, flush: true);
      }
    } finally {
      // Clean up temp file if it still exists.
      if (await tempFile.exists()) {
        await tempFile.delete();
      }
    }
  }
}

/// Thrown when an MCP server configuration fails validation.
class McpValidationException implements Exception {
  final String message;
  const McpValidationException(this.message);

  @override
  String toString() => 'McpValidationException: $message';
}
