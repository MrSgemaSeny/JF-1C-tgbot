package kz.zhanfinance.bot.handler;

import org.telegram.telegrambots.meta.api.objects.Update;

public interface CommandHandler {
    boolean canHandle(String command);
    void handle(Update update, String command, String[] args);
}
