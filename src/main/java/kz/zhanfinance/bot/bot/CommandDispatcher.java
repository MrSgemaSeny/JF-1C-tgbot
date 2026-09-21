package kz.zhanfinance.bot.bot;

import kz.zhanfinance.bot.handler.CommandHandler;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Arrays;
import java.util.List;

@Component
public class CommandDispatcher {

    private static final Logger log = LoggerFactory.getLogger(CommandDispatcher.class);

    private final List<CommandHandler> handlers;
    private final TelegramMessageSender messageSender;

    public CommandDispatcher(List<CommandHandler> handlers, TelegramMessageSender messageSender) {
        this.handlers = handlers;
        this.messageSender = messageSender;
    }

    public void dispatch(Update update) {
        if (update == null || !update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String rawText = update.getMessage().getText().trim();
        if (!rawText.startsWith("/")) {
            return;
        }

        String[] parts = rawText.split("\\s+");
        String commandWithBot = parts[0].toLowerCase();
        String command = commandWithBot.contains("@")
                ? commandWithBot.substring(0, commandWithBot.indexOf('@'))
                : commandWithBot;

        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        log.debug("Dispatching command '{}' with {} args", command, args.length);

        for (CommandHandler handler : handlers) {
            if (handler.canHandle(command)) {
                try {
                    handler.handle(update, command, args);
                } catch (Exception e) {
                    log.error("Error executing command '{}': {}", command, e.getMessage(), e);
                    if (update.getMessage().getChatId() != null) {
                        messageSender.sendHtml(update.getMessage().getChatId(),
                                "Произошла ошибка при обработке команды. Пожалуйста, попробуйте позже.");
                    }
                }
                return;
            }
        }

        if (update.getMessage().getChatId() != null) {
            messageSender.sendHtml(update.getMessage().getChatId(),
                    "Неизвестная команда. Введите /help для просмотра доступных команд.");
        }
    }
}
