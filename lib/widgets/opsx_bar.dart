import 'package:flutter/material.dart';
import '../core/theme.dart';

/// Horizontal scrollable bar showing opsx skills when openspec/ exists in project.
/// Displays skill chips that can be tapped to send as slash commands.
class OpsxBar extends StatelessWidget {
  final List<String> skills;
  final ValueChanged<String> onSkillSelected;

  const OpsxBar({
    super.key,
    required this.skills,
    required this.onSkillSelected,
  });

  @override
  Widget build(BuildContext context) {
    if (skills.isEmpty) return const SizedBox.shrink();

    return Container(
      height: 40,
      decoration: const BoxDecoration(
        color: TccColors.surface,
        border: Border(
          bottom: BorderSide(color: TccColors.border),
        ),
      ),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        itemCount: skills.length,
        separatorBuilder: (_, __) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final skill = skills[index];
          return _buildSkillChip(skill);
        },
      ),
    );
  }

  Widget _buildSkillChip(String skill) {
    final displayName = skill.startsWith('/') ? skill.substring(1) : skill;
    final label = displayName.length > 20
        ? '${displayName.substring(0, 17)}...'
        : displayName;

    return Material(
      color: TccColors.primary.withOpacity(0.08),
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        onTap: () => onSkillSelected(skill),
        borderRadius: BorderRadius.circular(16),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 0),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(16),
            border: Border.all(
              color: TccColors.primary.withOpacity(0.25),
            ),
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(
                Icons.bolt,
                size: 14,
                color: TccColors.primary,
              ),
              const SizedBox(width: 4),
              Text(
                label,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w500,
                  color: TccColors.primary,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
