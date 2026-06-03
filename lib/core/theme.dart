import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

class TccColors {
  static const background = Color(0xFF1E1E2E);
  static const surface = Color(0xFF2D2D3F);
  static const surfaceLight = Color(0xFF3D3D5C);
  static const primary = Color(0xFFF59E0B);
  static const onSurface = Color(0xFFE0E0E0);
  static const onSurfaceVariant = Color(0xFF9E9E9E);
  static const error = Color(0xFFEF4444);
  static const success = Color(0xFF22C55E);
  static const border = Color(0xFF3D3D5C);
  static const divider = Color(0xFF2D2D3F);
}

class TccTheme {
  static ThemeData get dark {
    final base = ThemeData.dark(useMaterial3: true);
    return base.copyWith(
      scaffoldBackgroundColor: TccColors.background,
      colorScheme: const ColorScheme.dark(
        primary: TccColors.primary,
        onPrimary: Colors.black,
        surface: TccColors.surface,
        onSurface: TccColors.onSurface,
        error: TccColors.error,
      ),
      textTheme: GoogleFonts.interTextTheme(base.textTheme).apply(
        bodyColor: TccColors.onSurface,
        displayColor: TccColors.onSurface,
      ),
      cardTheme: CardThemeData(
        color: TccColors.surface,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: TccColors.border),
        ),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: TccColors.background,
        elevation: 0,
        centerTitle: false,
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: TccColors.surface,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(24),
          borderSide: const BorderSide(color: TccColors.border),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(24),
          borderSide: const BorderSide(color: TccColors.border),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(24),
          borderSide: const BorderSide(color: TccColors.primary, width: 2),
        ),
        contentPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
      ),
      dividerTheme: const DividerThemeData(
        color: TccColors.divider,
        thickness: 1,
      ),
    );
  }
}

class TccTextStyles {
  static const titleLarge = TextStyle(fontSize: 18, fontWeight: FontWeight.w600);
  static const titleMedium = TextStyle(fontSize: 16, fontWeight: FontWeight.w500);
  static const bodyLarge = TextStyle(fontSize: 14, fontWeight: FontWeight.w400);
  static const bodyMedium = TextStyle(fontSize: 13, fontWeight: FontWeight.w400);
  static const caption = TextStyle(fontSize: 12, fontWeight: FontWeight.w300, color: TccColors.onSurfaceVariant);
  static const code = TextStyle(fontSize: 13, fontFamily: 'monospace', fontWeight: FontWeight.w400);
}
