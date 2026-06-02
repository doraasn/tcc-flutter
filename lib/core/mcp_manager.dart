import 'dart:convert';
import 'dart:io';
import 'package:path/path.dart' as p;
import 'constants.dart';

class McpServer {
  final String name;
  final String command;
  final List<String> args;
  final Map<String, String> env;

  const McpServer({
    required this.name,
    required this.command,
    this.args = const [],
    this.env = const {},
  });

  factory McpServer.fromJson(String name, Map<String, dynamic> json) {
    return McpServer(
      name: name,
      command: json['command'] ?? '',
      args: List<String>.from(json['args'] ?? []),
      env: Map<String, String>.from(json['env'] ?? {}),
    );
  }

  Map<String, dynamic> toJson() => {
    'command': command,
    'args': args,
    'env': env,
  };
}

class McpManager {
  File? _configFile;

  Future<File> _getConfigFile() async {
    if (_configFile != null) return _configFile!;

    final rootfs = await TccPaths.rootfs;
    _configFile = File(p.join(rootfs, 'root', '.config', 'Claude', 'claude_desktop_config.json'));
    return _configFile!;
  }

  Future<Map<String, dynamic>> readConfig() async {
    final file = await _getConfigFile();
    if (!await file.exists()) {
      return {'mcpServers': {}};
    }

    final content = await file.readAsString();
    return jsonDecode(content) as Map<String, dynamic>;
  }

  Future<List<McpServer>> get Servers async {
    final config = await readConfig();
    final servers = config['mcpServers'] as Map<String, dynamic>? ?? {};
    return servers.entries.map((e) => McpServer.fromJson(e.key, e.value as Map<String, dynamic>)).toList();
  }

  Future<void> addServer(McpServer server) async {
    final config = await readConfig();
    final servers = config['mcpServers'] as Map<String, dynamic>? ?? {};
    servers[server.name] = server.toJson();
    config['mcpServers'] = servers;
    await _writeConfig(config);
  }

  Future<void> removeServer(String name) async {
    final config = await readConfig();
    final servers = config['mcpServers'] as Map<String, dynamic>? ?? {};
    servers.remove(name);
    config['mcpServers'] = servers;
    await _writeConfig(config);
  }

  Future<void> updateServer(McpServer server) async {
    await addServer(server);
  }

  Future<void> _writeConfig(Map<String, dynamic> config) async {
    final file = await _getConfigFile();
    await file.parent.create(recursive: true);
    final content = const JsonEncoder.withIndent('  ').convert(config);
    await file.writeAsString(content);
  }
}
