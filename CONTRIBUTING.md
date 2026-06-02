# Contributing to TCC

## Development Setup

### Prerequisites
- Flutter SDK 3.32.2+
- Android SDK 35
- Android NDK (for proot cross-compilation)
- Git

### Getting Started

```bash
# Clone the repository
git clone https://github.com/doraasn/tcc-flutter.git
cd tcc-flutter

# Install dependencies
flutter pub get

# Run code generation
flutter pub run build_runner build

# Run in debug mode
flutter run
```

## Project Structure

- `lib/core/` - Core utilities and constants
- `lib/models/` - Data models (freezed)
- `lib/providers/` - Riverpod state management
- `lib/services/` - Business logic services
- `lib/screens/` - Page-level widgets
- `lib/widgets/` - Reusable UI components
- `scripts/` - Build and utility scripts
- `android/` - Android-specific code

## Code Style

### Dart
- Use `const` constructors where possible
- Prefer `final` for immutable variables
- Follow effective_dart guidelines
- Use freezed for immutable data classes

### Flutter
- Use Material Design 3
- Follow Flutter best practices
- Use const widgets for performance

## Commit Messages

Use conventional commits:
- `feat:` New feature
- `fix:` Bug fix
- `docs:` Documentation
- `style:` Code style
- `refactor:` Refactoring
- `test:` Tests
- `chore:` Maintenance

## Pull Requests

1. Create a feature branch
2. Make your changes
3. Run `flutter analyze`
4. Run `flutter test`
5. Submit a pull request

## Building APK

```bash
# Build proot (requires NDK)
./scripts/build_proot.sh

# Build rootfs (requires network)
./scripts/build_rootfs.sh

# Build APK
flutter build apk --release
```

## Architecture

See [SPEC.md](SPEC.md) for detailed architecture documentation.
