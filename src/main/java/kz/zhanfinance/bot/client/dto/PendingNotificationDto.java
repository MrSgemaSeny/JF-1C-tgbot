package kz.zhanfinance.bot.client.dto;

import java.time.Instant;

public record PendingNotificationDto(
        Long id,
        Long chatId,
        Long userId,
        String message,
        int attempts,
        Instant createdAt
) {}
