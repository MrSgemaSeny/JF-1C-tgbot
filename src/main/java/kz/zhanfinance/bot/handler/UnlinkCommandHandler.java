package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.service.HtmlMessageFormatter;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import kz.zhanfinance.bot.service.UserSessionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

@Component
public class UnlinkCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(UnlinkCommandHandler.class);

    private final BackendClient backendClient;
    private final UserSessionCache sessionCache;
    private final TelegramMessageSender messageSender;

    public UnlinkCommandHandler(
            BackendClient backendClient,
            UserSessionCache sessionCache,
            TelegramMessageSender messageSender
    ) {
        this.backendClient = backendClient;
        this.sessionCache = sessionCache;
        this.messageSender = messageSender;
    }

    @Override
    public boolean canHandle(String command) {
        return "/unlink".equalsIgnoreCase(command);
    }

    @Override
    public void handle(Update update, String command, String[] args) {
        if (update.getMessage() == null || update.getMessage().getChatId() == null) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Optional<ClientProfileDto> profileOpt = sessionCache.resolve(chatId, () -> backendClient.getClientByChatId(chatId));

        if (profileOpt.isEmpty()) {
            messageSender.sendHtml(chatId, "Аккаунт не привязан.");
            return;
        }

        log.info("Unlinking telegram account for chatId={}", chatId);
        boolean unlinked = backendClient.unlinkByChatId(chatId);
        sessionCache.invalidate(chatId);

        if (unlinked) {
            messageSender.sendHtml(chatId, HtmlMessageFormatter.formatUnlinkSuccess());
        } else {
            messageSender.sendHtml(chatId, "Не удалось выполнить отвязку аккаунта. Попробуйте позже или обратитесь в поддержку.");
        }
    }
}
