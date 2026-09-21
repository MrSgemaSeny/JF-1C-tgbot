package kz.zhanfinance.bot.client.dto;

public record NotificationAckResponse(
        int acknowledgedCount,
        int processedCount,
        int successCount,
        int failedCount
) {
    public NotificationAckResponse(int acknowledgedCount) {
        this(acknowledgedCount, acknowledgedCount, acknowledgedCount, 0);
    }
}
