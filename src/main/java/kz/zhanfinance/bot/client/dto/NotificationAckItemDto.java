package kz.zhanfinance.bot.client.dto;

public record NotificationAckItemDto(
        Long id,
        String status,
        Boolean success,
        String error
) {
    public NotificationAckItemDto(Long id, String status, String error) {
        this(id, status, "SENT".equalsIgnoreCase(status), error);
    }
}
