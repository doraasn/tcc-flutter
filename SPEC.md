# TCC — 超级智能体客户端

## 概述
TCC 是一款运行在 Android 平板上的原生 AI 对话客户端，深度集成 Claude Code 能力的自主智能体平台。
100% Kotlin 原生开发，无 WebView，无后端服务。

---

## 一、核心功能

### 1. AI 对话界面（主页）

| 功能 | 说明 |
|------|------|
| 对话气泡 | 用户消息右对齐（紫色），AI 回复左对齐（暗色），流式逐字输出 |
| Markdown 渲染 | **加粗**、`行内代码`、代码块（深色背景+等宽字体）、列表、链接 |
| 流式显示 | AI 回复实时逐字出现，末尾闪烁光标，支持中途停止 |
| 快捷指令 | 输入 `/` 触发命令面板：`/context` `/compact` `/clear` `/help` `/model` `/temperature` |
| 建议入口 | 空对话时显示快捷建议卡片（如"帮我写代码"、"总结文档"） |

### 2. 对话管理

| 功能 | 说明 |
|------|------|
| 侧边栏 | 左侧 280dp 面板，显示所有历史对话 |
| 新对话 | 一键创建新对话，自动生成标题 |
| 切换/删除 | 点击切换对话，长按删除 |
| 日期分组 | 今天 / 昨天 / 更早 |
| 搜索 | 搜索对话标题和内容 |
| 自动保存 | 每条消息实时写入本地存储 |

### 3. AI 引擎

| 功能 | 说明 |
|------|------|
| 直接 API 调用 | Java HttpURLConnection + SSE 流式解析，不依赖 Node.js |
| 多模型支持 | DeepSeek V4 Flash / DeepSeek V4 / Claude Sonnet 4.6 / Claude Opus 4.7 / Claude Haiku 4.5 / Gemini 2.5 Flash |
| 自定义端点 | 可配置任意兼容 Anthropic API 格式的地址 |
| 系统提示词 | 自定义 system prompt，支持保存多个模板 |
| 上下文管理 | 自动管理对话上下文长度 |

### 4. 工具集成

| 功能 | 说明 |
|------|------|
| lark-cli 管理 | 查看认证状态、执行飞书命令、发送消息、读取文档 |
| 自定义工具 | 注册和执行自定义脚本/命令工具 |
| 命令执行 | 在 Android 环境中安全执行 shell 命令 |

### 5. 设置

| 功能 | 说明 |
|------|------|
| API Key | 密码输入 + 明文切换，本地安全存储 |
| 模型选择 | 下拉选择预设模型列表 |
| API 地址 | 自定义 Base URL |
| 系统提示词 | 多行编辑器 + 保存/加载模板 |
| 字体大小 | 滑块调节 (12-20sp) |
| 主题 | 暗色主题（默认） |

### 6. 系统状态

| 功能 | 说明 |
|------|------|
| 环境检测 | Node.js / glibc / Claude Code / lark-cli / proot 状态 |
| 存储查看 | 可用空间 |
| 认证状态 | 飞书 CLI 认证状态 |

---

## 二、技术架构

```
┌──────────────────────────────────────┐
│           TCC APK (com.mcc)          │
├──────────────────────────────────────┤
│  MainActivity                        │
│  ├── SidebarView ← 对话列表          │
│  ├── ChatListView ← 消息气泡         │
│  ├── MessageInputView ← 输入栏       │
│  └── SettingsView ← 设置面板         │
├──────────────────────────────────────┤
│  AnthropicClient ← SSE API 流式调用   │
│  LarkClient ← lark-cli 进程调用       │
│  ConversationManager ← 本地存储管理   │
│  ConfigManager ← 配置持久化           │
└──────────────────────────────────────┘
```

### 技术栈
| 层 | 技术 |
|----|------|
| 语言 | Kotlin 2.3.21 |
| UI | 纯程序化 View（无 XML 布局） |
| 编译 | kotlinc → JVM bytecode → d8 → DEX |
| 打包 | Python 脚本（AXML + zip + apksigner）|
| 存储 | JSON 文件（Context.filesDir）|
| API 通信 | SSE over HTTP（无第三方库）|
| 最低 SDK | API 26 (Android 8.0) |
| 目标 SDK | API 35 (Android 15) |

### 关键依赖
- Kotlin stdlib（打包在 APK 内）
- Android SDK Platform 35（编译时）
- 无其他第三方库

---

## 三、数据流

```
用户输入 → MessageInputView
         → MainActivity.sendMessage()
         → ConversationManager.addMessage() [保存用户消息]
         → AnthropicClient.streamChat() [HTTP POST + SSE]
         → onChunk(text) → ChatListView.updateLastMessage() [实时显示]
         → onDone() → ConversationManager.saveConversation() [保存完整回复]
```

指令流程：
```
输入 /command
  → MessageInputView 识别 "/" 前缀
  → 弹出命令面板
  → 选择命令 → 本地执行 / API 调用
  → 结果显示在对话中
```

---

## 四、开发 & 调试

```bash
# 构建
python3 android/build.py

# 安装到设备
cp android/dist/TCC.apk /sdcard/Download/
termux-open /sdcard/Download/TCC.apk

# 查看日志
logcat -s TCC
logcat -b crash
```

注意：MIUI 系统需在设置中允许未知来源安装。

---

### 7. WebDAV 备份

| 功能 | 说明 |
|------|------|
| 坚果云支持 | 兼容标准 WebDAV 协议，可直接使用坚果云 |
| 配置项 | 服务器地址 / 用户名 / 密码（应用密码） |
| 测试连接 | 验证 WebDAV 服务器连通性 |
| 一键备份 | 将所有对话记录 + 配置打包上传到云端 |
| 一键恢复 | 从云端下载最新备份并恢复所有数据 |
| 数据格式 | JSON 格式，包含所有对话消息和配置（不含 API Key）|
| 本地导出 | 也支持导出到本地文件 |

---

## 五、后续规划

- [ ] 多 API Key 轮换
- [ ] 对话导出/导入
- [ ] MCP 协议支持
- [ ] lark-cli 深度集成（文档读写、消息发送图形化）
- [ ] 自动定时备份
- [ ] 智能体编排（多模型协同）
- [ ] OTA 自动更新
