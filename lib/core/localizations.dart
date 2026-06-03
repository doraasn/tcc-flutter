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

  // ── Process ──────────────────────────────────────────────────────────────────

  static const String stopProcess = '终止进程';
}
