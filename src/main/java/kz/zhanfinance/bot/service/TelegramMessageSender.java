package kz.zhanfinance.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Component
public class TelegramMessageSender {

    private static final Logger log = LoggerFactory.getLogger(TelegramMessageSender.class);

    private final TelegramClient telegramClient;

    public TelegramMessageSender(TelegramClient telegramClient) {
        this.telegramClient = telegramClient;
    }

    public void sendHtml(Long chatId, String htmlText) {
        if (chatId == null || htmlText == null || htmlText.isBlank()) {
            return;
        }
        try {
            SendMessage message = SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(htmlText)
                    .parseMode("HTML")
                    .build();
            telegramClient.execute(message);
        } catch (Exception e) {
            log.error("Failed to send message to chatId {}: {}", chatId, e.getMessage(), e);
        }
    }
}
