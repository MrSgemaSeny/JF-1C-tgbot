package kz.zhanfinance.bot.client.dto;

import java.util.List;

public record NotificationAckRequest(
        List<NotificationAckItemDto> processed
) {}
