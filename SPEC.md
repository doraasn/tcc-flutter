# TCC — 超级智能体客户端

## 概述
TCC 是运行在 Android 上的原生 AI 对话客户端。内置完整 Termux Linux 环境（bash/apt/Node.js/Claude Code CLI），
通过 ProcessBuilder 直接调用 Claude Code 命令行，实现本地 AI 对话。100% Kotlin 原生开发，无 WebView，无后端服务。

---

## 一、核心功能

### 1. AI 对话界面（主页）

| 功能 | 说明 |
|------|------|
| 对话气泡 | 用户消息右对齐（紫色），AI 回复左对齐（暗色），自适应宽度 |
| Markdown 渲染 | **加粗**、`行内代码`、代码块、列表(`- ` / `1. `)、链接(`[text](url)`) |
| 流式显示 | 缓冲区流式输出（64字符/80ms 间隔），末尾闪烁光标，支持中途停止 |
| 快捷指令 | 输入 `/` 触发命令面板：`/context` `/compact` `/clear` `/help` `/model` `/temperature` |
| 建议入口 | 空对话时显示快捷建议卡片 |
| 文字可选 | 对话气泡长按可选中复制 |

### 2. 对话管理

| 功能 | 说明 |
|------|------|
| 侧边栏 | 左侧 280dp 面板，汉堡按钮(Canvas 绘制三条线)触发 |
| 新对话 | 一键创建，自动生成标题，重置 Claude sessionId |
| 切换/删除 | 点击切换，长按删除 |
| 日期分组 | 今天 / 昨天 / 更早 |
| 搜索 | 实时搜索对话标题 |
| 自动保存 | 每条消息实时写入本地 JSON |

### 3. AI 引擎 (Claude CLI)

| 功能 | 说明 |
|------|------|
| 本地运行 | ProcessBuilder 调用内置 Termux 的 `claude` 命令行 |
| API Key | 注入 `ANTHROPIC_API_KEY` 环境变量 |
| 会话管理 | 自动解析 Claude session ID，通过 `--resume` 维持上下文 |
| 流式输出 | 读取 stdout 缓冲输出到 ChatListView |
| 系统提示词 | shell 转义后通过 `-p "System: ..."` 传入 |

### 4. 工具集成

| 功能 | 说明 |
|------|------|
| Shell 终端 | 完整 Termux bash + apt + 命令执行，支持切换目录 |
| lark-cli | 飞书 CLI 图形化操作面板（认证/发消息/文档） |
| 系统状态 | Node.js / glibc / Claude Code / lark-cli / proot 环境检测 + 存储查看 |

### 5. 设置

| 功能 | 说明 |
|------|------|
| Anthropic API Key | 密码输入 + 明文切换，SharedPreferences 存储 |
| 模型显示 | 默认/Claude Sonnet/Claude Opus (CLI 自身管理模型) |
| 系统提示词 | 多行编辑器 + 保存/加载模板 |
| 字体大小 | 滑块调节 (12-20sp)，全局生效 |
| 主题 | 暗色主题 |

### 6. WebDAV 备份

| 功能 | 说明 |
|------|------|
| 坚果云支持 | 标准 WebDAV 协议 |
| 测试连接 / 备份 / 恢复 / 本地导出 | 完整 CRUD |

---

## 二、技术架构 (实际)

```
TCC APK (com.tcc, ~280MB)
├── assets/termux-bundle.tar.gz  ← 完整 Termux 环境 (277MB gzip)
│
├── MainActivity                 ← 主控制器
│   ├── 初始化屏 (ProgressBar)   ← 首次启动解压环境
│   ├── ChatListView             ← 消息气泡 + Markdown 渲染
│   ├── MessageInputView         ← 输入栏 + 命令面板
│   ├── SidebarView              ← 对话列表 + 搜索 + 工具入口
│   ├── SettingsView             ← 设置面板
│   ├── SystemStatusView         ← 系统检测
│   ├── LarkToolsView            ← 飞书 CLI
│   └── ShellView                ← Termux 终端
│
├── ClaudeCli                    ← ProcessBuilder 调用 bash -c "claude -p '...'"
├── TermuxBootstrap              ← tar.gz 解压 + chmod + 环境变量
├── LarkClient                   ← lark-cli 进程调用
├── ConversationManager          ← JSON 文件存储对话
├── ConfigManager               ← SharedPreferences 配置
├── BackupManager               ← WebDAV 备份/恢复
└── WebDavClient                ← WebDAV HTTP 客户端
```

### 技术栈
| 层 | 技术 |
|----|------|
| 语言 | Kotlin |
| UI | 纯程序化 View（无 XML 布局） |
| 编译 | kotlinc → d8 → DEX |
| 打包 | Python (AXML writer + zip + apksigner + 二进制 manifest 补丁) |
| 存储 | JSON (Context.filesDir) + SharedPreferences |
| AI 引擎 | Claude Code CLI (本地进程，非 HTTP API) |
| Termux 环境 | 内置 bootstrap (bash + apt + Node.js + @anthropic-ai/claude-code) |
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 |

### 关键文件
| 文件 | 作用 |
|------|------|
| `build.py` | 编译 Kotlin → DEX → AXML 生成 → 打包 → 签名 → 二进制补丁 INTERNET 权限 |
| `bundle.sh` | 从本机 Termux 导出环境到 assets/termux-bundle.tar.gz |
| `TermuxBootstrap.kt` | tar.gz 解压 (GZIP + 自写 tar 解析器) + chmod 权限修复 + 环境变量 |
| `ClaudeCli.kt` | ProcessBuilder 启动 bash 执行 claude 命令 + 缓冲流式输出 |
| `ChatListView.kt` | 气泡渲染 + Markdown 解析 (bold/code/list/link) |

---

## 三、数据流

```
用户输入 → MessageInputView
         → MainActivity.sendMessage()
         → Conversation.messages.add(userMsg + assistantMsg)
         → ClaudeCli.streamChat()
           → ProcessBuilder("bash", "-c", "cd ~ && claude -p '...'")
           → 缓冲读取 stdout → StreamEvent.Chunk
           → ChatListView.updateLastMessage() [实时显示]
         → onDone() → parseSessionId → ConversationManager.save()
```

首次启动：
```
MainActivity.onCreate()
  → isInstalled() == false
  → 显示 ProgressBar 加载页
  → TermuxBootstrap.install()
    → 解压 assets/termux-bundle.tar.gz (277MB)
    → /system/bin/chmod -R 755 或 setExecutable 回退
    → isInstalled() == true
  → 隐藏加载页，进入对话界面
```

---

## 四、开发注意事项 (AI 必读)

### 构建流程
```bash
# 1. 首先生成 Termux 环境包（只需一次，或 Termux 环境更新后）
bash android/bundle.sh

# 2. 构建 APK
python3 android/build.py

# 3. APK 自动签名输出到 android/dist/TCC.apk
```

### 二进制 Manifest 补丁
`build.py` 生成的 AXML manifest 不含 INTERNET 权限。
必须在签名后用 Python 脚本二进制注入 `uses-permission` 标签。
**当前代码中这个补丁未自动集成在 build.py 里**——需要单独运行。
注意 `np+=sd` 必须写在 for 循环外，否则 manifest 膨胀 N 倍导致 `packageInfo is null`。

### TermuxBootstrap 关键约束
- tar 解压需处理 type '0'(文件)、'5'(目录)、'2'(符号链接)
- USTAR 长路径(>100字符)需读 offset 345 的 prefix 字段
- `setExecutable(true, false)` 在 Android 内部存储不可靠，必须 `/system/bin/chmod -R 755` 做主力，setExecutable 做回退
- 安装后必须 `mkdirs("tmp")` 否则 Claude/NPM 报 TMPDIR 未找到
- 安装前 deleteRecursively() 清旧残留
- `bundle.sh` 必须 `(cd $PREFIX && tar -ch ...)` 在子 shell 展开 glob，加 `-h` 解引用符号链接

### ClaudeCli 约束
- 构造函数需要 `Context` 参数（用于 TermuxBootstrap）
- 流式输出用缓冲模式（64 字符或 80ms），不能逐字符发 Chunk（1000 字符 = 1000 次 UI 刷新）
- sessionId 只在检测到时更新，未检测到不要清空
- 单引号必须 escape：`str.replace("'", "'\\''")`
- 必须前置检查 `TermuxBootstrap.isInstalled()` 给出友好错误
- 命令：`cd ~ && claude -p '$escaped'` 或 `--resume $sid -p '...'`

### MainActivity 初始化流程
- 首次启动(未安装): 显示加载页 → `install()` → **检查返回值** → 成功才进主界面
- 已安装: 直接进主界面
- 控制变量: `mainContent.visibility` 和 `setupView.visibility`
- `sendMessageInternal` 开头检查 `isInstalled()`，未就绪弹出 Toast

### 颜色常量规范
```kotlin
BG = 0xFF0A0A0B.toInt()        // 主背景
SURFACE = 0xFF141416.toInt()    // 卡片背景
SURFACE_ELEVATED = 0xFF1C1C1F.toInt()  // 凸起卡片
ACCENT = 0xFF6C5CE7.toInt()     // 主题紫
TEXT_PRIMARY = 0xFFFFFFFF.toInt()
TEXT_SECONDARY = 0xFF8B8B93.toInt()
TEXT_TERTIARY = 0xFF5E5E66.toInt()
BORDER = 0xFF2A2A2E.toInt()
```

### 不用的文件和遗留问题
- `AnthropicClient.kt` 已废弃，保留作参考（HTTP API 方式）
- `java/com/tcc/` 下的 Java 文件是旧代码，`build.py` 不编译它们（只编译 `src/`）
- `MCC.apk` 是旧版 APK，可删除
- `tmp_*` 目录是 apktool 工作目录，可清理
- `build_orig.py` 已删除，当前只用 `build.py`

### 已知未完成
- [ ] INTERNET 权限补丁未集成进 build.py 主流程
- [ ] Claude CLI 的 `--output-format stream-json` 未验证可用性
- [ ] sessionId 通过 Regex 解析可能不准确（依赖 CLI 输出格式）
- [ ] 多 DEX (classes2.dex) 不支持
- [ ] 本地导出按钮 UI (SettingsView) 已加但未验证
- [ ] 符号链接的回退复制未实现
