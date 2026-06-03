/// Comprehensive Chinese localization strings for TCC Flutter app.
///
/// All user-facing strings are collected here as static const fields,
/// grouped logically by the UI area they belong to.
class AppStrings {
  AppStrings._();

  // ── General ──────────────────────────────────────────────────────────────────

  static const String appName = 'TCC';
  static const String opsxEnabled = 'opsx 已启用';

  // ── Sidebar ──────────────────────────────────────────────────────────────────

  static const String newProject = '新建项目';
  static const String projects = '项目';
  static const String sessions = '会话';
  static const String noSessions = '暂无会话';
  static const String noProjects = '暂无项目';
  static const String startConversation = '开始对话以创建你的第一个会话';
  static const String projectNameHint = '项目名称';
  static const String projectName = '项目名称';
  static const String cancel = '取消';
  static const String create = '创建';
  static const String today = '今天';
  static const String yesterday = '昨天';
  static const String older = '更早';
  static const String deleteProject = '删除项目';

  /// Returns a localized delete-project confirmation message.
  static String deleteProjectConfirm(String project) =>
      '确定删除 "$project" 及其所有文件？';

  // ── Chat Area ────────────────────────────────────────────────────────────────

  static const String tagline = 'AI 原生移动 IDE 工作站';
  static const String openProject = '打开项目';
  static const String settings = '设置';

  // ── Input Bar ────────────────────────────────────────────────────────────────

  static const String inputMessageHint = '输入消息...';
  static const String startConversationHint = '开始对话...';
  static const String connected = '已连接';
  static const String disconnected = '未连接';
  static const String sessionPrefix = '会话: ';

  // ── Message Bubble ───────────────────────────────────────────────────────────

  static const String copiedToClipboard = '已复制到剪贴板';

  // ── Settings ─────────────────────────────────────────────────────────────────

  static const String models = '模型';
  static const String globalPrompt = '全局提示词';
  static const String globalPromptHint = '输入适用于所有会话的全局提示词...';
  static const String about = '关于';
  static const String tccVersion = 'TCC 版本';
  static const String claudeCodeVersion = 'Claude Code 版本';
  static const String edit = '编辑';
  static const String delete = '删除';
  static const String addModel = '添加模型';
  static const String editModel = '编辑模型';
  static const String name = '名称';
  static const String baseUrl = '接口地址';
  static const String apiKey = 'API 密钥';
  static const String modelId = '模型 ID';
  static const String add = '确定';
  static const String save = '保存';

  // ── Command Palette ──────────────────────────────────────────────────────────

  static const String commands = '命令';
  static const String available = '可用';
  static const String generalCategory = '通用';

  // Command descriptions
  static const String cmdClear = '清空当前对话';
  static const String cmdHelp = '查看可用命令和快捷键';
  static const String cmdCompact = '压缩对话以节省上下文';
  static const String cmdModel = '切换当前模型';
  static const String cmdStatus = '查看当前会话状态';
  static const String cmdStop = '停止当前生成';
  static const String cmdCost = '查看 token 用量和费用';
  static const String cmdProject = '切换或管理项目';

  /// Map from command name to its Chinese description for easy lookup.
  static const Map<String, String> commandDescriptions = <String, String>{
    '/clear': cmdClear,
    '/help': cmdHelp,
    '/compact': cmdCompact,
    '/model': cmdModel,
    '/status': cmdStatus,
    '/stop': cmdStop,
    '/cost': cmdCost,
    '/project': cmdProject,
  };

  // ── MCP Servers ────────────────────────────────────────────────────────────

  static const String mcpServers = 'MCP 服务器';
  static const String mcpDescription =
      'MCP（模型上下文协议）服务器为 Claude 提供额外的工具和数据源。'
      '配置后 Claude 可通过这些服务器访问文件系统、数据库等外部资源。';
  static const String addMcpServer = '添加服务器';
  static const String editMcpServer = '编辑服务器';
  static const String deleteMcpServer = '删除服务器';
  static const String serverName = '服务器名称';
  static const String command = '命令';
  static const String commandHint = '例如: npx, node, /usr/bin/python3';
  static const String args = '参数';
  static const String argsHint = '每行一个参数';
  static const String noMcpServers = '暂无已配置的 MCP 服务器';

  static String deleteMcpServerConfirm(String name) =>
      '确定删除 MCP 服务器 "$name"？';

  // ── Export ─────────────────────────────────────────────────────────────────

  static const String exportSession = '导出会话';
  static const String exportAsMarkdown = '导出为 Markdown';
  static const String exportAsJson = '导出为 JSON';

  // ── Process ──────────────────────────────────────────────────────────────────

  static const String stopProcess = '终止进程';

  // ── Version Management ─────────────────────────────────────────────────────

  static const String versionManagement = '版本管理';
  static const String activeVersion = '当前版本';
  static const String installedVersions = '已安装版本';
  static const String noVersionsInstalled = '尚未安装任何版本';
  static const String installNewVersion = '安装新版本';
  static const String installVersion = '安装版本';
  static const String versionNumberHint = '版本号（留空安装最新版）';
  static const String switchToVersion = '切换到此版本';
  static const String rollback = '回滚';
  static const String rollbackToPrevious = '回滚到上一版本';
  static const String pruneOldVersions = '清理旧版本';
  static const String deleteVersion = '删除此版本';
  static const String active = '使用中';
  static const String installing = '安装中...';
  static const String switching = '切换中...';
  static const String downloadFailed = '下载失败';
  static const String switchFailed = '切换失败';
  static const String cannotDeleteActive = '无法删除当前使用的版本';
  static const String confirmDelete = '确认删除';
  static String confirmDeleteVersion(String name) => '确定删除版本 $name？';
  static const String confirmPrune = '确定删除所有旧版本？仅保留当前使用的版本。';
  static const String versionInstalled = '版本安装成功';
  static const String versionSwitched = '已切换到新版本';
  static const String versionRolledBack = '已回滚到上一版本';
  static const String versionsPruned = '已清理旧版本';
  static const String versionDeleted = '版本已删除';
  static const String noVersionsToPrune = '没有需要清理的旧版本';
  static const String needTwoVersions = '至少需要两个版本才能回滚';
}
