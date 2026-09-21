package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.service.HtmlMessageFormatter;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class HelpCommandHandler implements CommandHandler {

    private final TelegramMessageSender messageSender;

    public HelpCommandHandler(TelegramMessageSender messageSender) {
        this.messageSender = messageSender;
    }

    @Override
    public boolean canHandle(String command) {
        return "/help".equalsIgnoreCase(command);
    }

    @Override
    public void handle(Update update, String command, String[] args) {
        if (update.getMessage() == null || update.getMessage().getChatId() == null) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        messageSender.sendHtml(chatId, HtmlMessageFormatter.formatHelp());
    }
}
