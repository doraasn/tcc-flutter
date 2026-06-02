# TCC - AI-Native Mobile IDE Workspace

基于 Flutter + PRoot 架构的 Claude Code 移动端客户端。

## 特性

- **轻量级容器**：Alpine Linux rootfs (5MB) 替代完整 Termux (280MB)
- **原生会话管理**：直接复用 Claude Code 的 --continue/--resume 机制
- **热切换模型**：1 秒内无缝切换 AI 模型
- **现代 UI**：Material Design 3 + 暗色主题
- **跨平台潜力**：Flutter 可同时出 Android/iOS

## 架构

```
Flutter UI → Dart Service → PRoot Container → Claude Code CLI
```

## 构建

### 前置要求

- Flutter SDK 3.32.2+
- Android SDK 35
- proot (arm64-v8a)

### 构建步骤

```bash
# 安装依赖
flutter pub get

# 运行代码生成
flutter pub run build_runner build

# 构建 APK
flutter build apk --release
```

## 目录结构

```
lib/
├── main.dart              # 应用入口
├── core/                  # 核心模块
│   ├── theme.dart         # 主题定义
│   ├── constants.dart     # 路径常量
│   └── ndjson_parser.dart # 流解析器
├── models/                # 数据模型
├── providers/             # Riverpod 状态管理
├── screens/               # 页面
├── widgets/               # 组件
└── services/              # 服务层
```

## 开发状态

- [x] 项目结构
- [ ] PRoot 集成
- [ ] 进程控制
- [ ] Chat UI
- [ ] 会话管理
- [ ] 模型切换
- [ ] 版本控制
