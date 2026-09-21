package kz.zhanfinance.bot.client.dto;

import java.time.Instant;

public record DocumentSummaryDto(
        Long id,
        String fileName,
        String contentType,
        Long fileSize,
        String status,
        Instant createdAt
) {}
