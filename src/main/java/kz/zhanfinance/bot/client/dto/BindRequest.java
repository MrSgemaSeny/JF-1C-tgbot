package kz.zhanfinance.bot.client.dto;

public record BindRequest(
        String token,
        Long chatId,
        String telegramUsername,
        String firstName,
        String lastName
) {
    public BindRequest(String token, Long chatId, String telegramUsername, String firstName) {
        this(token, chatId, telegramUsername, firstName, null);
    }
}
