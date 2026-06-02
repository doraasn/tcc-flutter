import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:hive_flutter/hive_flutter.dart';
import 'screens/home_screen.dart';
import 'core/theme.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Hive.initFlutter();
  await Hive.openBox('settings');
  await Hive.openBox('models');
  await Hive.openBox('projects');

  runApp(const ProviderScope(child: TccApp()));
}

class TccApp extends ConsumerWidget {
  const TccApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return MaterialApp(
      title: 'TCC',
      debugShowCheckedModeBanner: false,
      theme: TccTheme.dark,
      home: const HomeScreen(),
    );
  }
}
