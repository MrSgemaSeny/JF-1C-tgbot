package kz.zhanfinance.bot.client.dto;

public record TaskSummaryDto(
        Long id,
        String title,
        String stageName,
        String stageType,
        String dueDate,
        String assignedEmployeeName
) {}
