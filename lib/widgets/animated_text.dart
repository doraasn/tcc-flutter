import 'package:flutter/material.dart';
import '../core/theme.dart';

/// Typewriter effect widget that progressively reveals text character by character.
/// Used during streaming to animate incoming assistant text.
class AnimatedText extends StatefulWidget {
  final String text;
  final TextStyle? style;
  final bool animate;

  const AnimatedText({
    super.key,
    required this.text,
    this.style,
    this.animate = true,
  });

  @override
  State<AnimatedText> createState() => _AnimatedTextState();
}

class _AnimatedTextState extends State<AnimatedText>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<int> _charCount;
  String _previousText = '';

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 300),
      vsync: this,
    );
    _charCount = IntTween(begin: 0, end: widget.text.length).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOut),
    );

    if (widget.animate && widget.text.isNotEmpty) {
      _controller.forward();
    }
  }

  @override
  void didUpdateWidget(AnimatedText oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (widget.text != _previousText) {
      _previousText = widget.text;

      if (widget.animate) {
        _charCount = IntTween(
          begin: 0,
          end: widget.text.length,
        ).animate(
          CurvedAnimation(parent: _controller, curve: Curves.easeOut),
        );
        _controller
          ..reset()
          ..forward();
      } else {
        _controller.value = 1.0;
      }
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (!widget.animate) {
      return Text(
        widget.text,
        style: widget.style ?? TccTextStyles.bodyLarge,
      );
    }

    return AnimatedBuilder(
      animation: _charCount,
      builder: (context, _) {
        final displayText = widget.text.substring(
          0,
          _charCount.value.clamp(0, widget.text.length),
        );
        return Text(
          displayText,
          style: widget.style ?? TccTextStyles.bodyLarge,
        );
      },
    );
  }
}
