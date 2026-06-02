# TCC - AI-Native Mobile IDE Workspace

## 概述

TCC (Terminal Claude Client) 是一个基于 Flutter + PRoot 架构的 Android 应用，提供完整的 Claude Code 运行环境和现代化的 Chat UI。

## 架构设计

### 三层架构

```
┌─────────────────────────────────────┐
│  Flutter UI Layer                    │
│  - Chat Interface                   │
│  - Sidebar (Projects/Sessions)      │
│  - Settings Panel                   │
├─────────────────────────────────────┤
│  Dart Service Layer                 │
│  - ProcessController                │
│  - ClaudeService                    │
│  - PRootService                     │
├─────────────────────────────────────┤
│  PRoot Container                    │
│  - Alpine Linux Rootfs              │
│  - Node.js + npm                    │
│  - Claude Code CLI                  │
└─────────────────────────────────────┘
```

### 目录结构

```
/data/data/com.tcc.app/
├── rootfs/                    # Alpine Linux rootfs
│   ├── bin/                   # 基础命令
│   ├── usr/                   # 用户程序
│   └── root/
│       ├── workspace/         # 托管工作空间
│       ├── .claude/           # Claude 会话存储
│       └── .tcc/versions/     # 版本控制
├── settings/                  # Hive 数据库
└── logs/                      # 应用日志
```

## 核心模块

### 1. PRootService
- 下载并解压 Alpine Linux rootfs (~5MB)
- 安装 Node.js + npm
- 安装 Claude Code CLI
- 提供 proot 容器执行环境

### 2. ClaudeService
- 通过 proot 启动 Claude Code 进程
- 注入环境变量（API Key, Model, Base URL）
- 管理 stdin/stdout 管道
- 支持 --resume 和 --continue 会话恢复

### 3. ProcessController (Riverpod)
- 管理进程生命周期
- NDJSON 流解析
- 状态响应式驱动 UI
- 热切换模型（Kill + 重启）

### 4. WorkspaceController
- 项目 CRUD 操作
- 工作空间切换
- 监控 openspec 目录

### 5. SessionController
- 扫描 ~/.claude/projects/ 下的 JSONL
- 解析会话元数据
- 渲染历史会话列表

### 6. VersionManager
- 双槽软链接切换
- 版本下载和安装
- 一键回退

### 7. MCPManager
- 读写 claude_desktop_config.json
- 添加/删除 MCP 服务器
- 配置验证

## 模型配置

| Provider | Base URL | Model ID |
|----------|----------|----------|
| Mimo | api.siliconflow.cn/v1 | XiaomiMiMo/MiMo-7B-RL |
| DeepSeek | api.deepseek.com/v1 | deepseek-reasoner |
| Anthropic | api.anthropic.com/v1 | claude-sonnet-4-6-20250514 |

## 热切换机制

1. Kill 当前 cc 进程
2. 更新环境变量（ANTHROPIC_BASE_URL, ANTHROPIC_API_KEY, CLAUDE_MODEL）
3. 带 --resume 参数重新启动
4. 1 秒内无缝切换

## 版本控制

- 内置固化 v2.1.153
- 双槽软链接切换
- 支持手动升级和一键回退

## 构建要求

- Flutter SDK 3.32.2+
- Android SDK 35
- Android NDK (交叉编译 proot)
- proot (arm64-v8a 静态编译)

## 体积对比

| 组件 | 旧版 (Kotlin) | 新版 (Flutter) |
|------|--------------|----------------|
| Rootfs | 280MB (Termux) | 5MB (Alpine) |
| Node.js | 含在 Termux | 15MB |
| Claude Code | 10MB | 10MB |
| proot | N/A | 1MB |
| APK | 183MB | ~40MB |

## 数据流

```
用户输入 → InputBar
        ↓
ProcessController.sendInput()
        ↓
proot → Claude Code CLI (--output-format stream-json)
        ↓
stdout → NdjsonParser
        ↓
ChatMessage (role: assistant) → chatMessagesProvider
        ↓
ChatArea rebuild → MessageBubble
```

## 文件结构

```
lib/
├── main.dart
├── core/
│   ├── theme.dart
│   ├── constants.dart
│   ├── ndjson_parser.dart
│   └── mcp_manager.dart
├── models/
│   ├── workspace_state.dart
│   └── chat_message.dart
├── providers/
│   ├── workspace_provider.dart
│   ├── session_provider.dart
│   ├── process_provider.dart
│   ├── model_provider.dart
│   ├── settings_provider.dart
│   └── chat_provider.dart
├── screens/
│   ├── home_screen.dart
│   └── settings_screen.dart
├── widgets/
│   ├── sidebar.dart
│   ├── chat_area.dart
│   ├── input_bar.dart
│   ├── message_bubble.dart
│   ├── typing_indicator.dart
│   ├── project_picker.dart
│   ├── session_list.dart
│   ├── command_palette.dart
│   ├── opsx_bar.dart
│   └── animated_text.dart
└── services/
    ├── proot_service.dart
    ├── claude_service.dart
    └── version_manager.dart
```

## 构建流程

```bash
# 1. 交叉编译 proot (需要 NDK)
./scripts/build_proot.sh

# 2. 构建 rootfs (需要网络)
./scripts/build_rootfs.sh

# 3. 构建 APK
flutter pub get
flutter pub run build_runner build
flutter build apk --release

# 4. 安装
cp build/app/outputs/flutter-apk/app-release.apk /sdcard/Download/TCC.apk
```
