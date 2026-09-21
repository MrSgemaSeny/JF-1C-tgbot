package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.BackendClientException;
import kz.zhanfinance.bot.client.dto.BindRequest;
import kz.zhanfinance.bot.client.dto.BindResponse;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.service.HtmlMessageFormatter;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import kz.zhanfinance.bot.service.UserSessionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;

@Component
public class StartCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(StartCommandHandler.class);

    private final BackendClient backendClient;
    private final UserSessionCache sessionCache;
    private final TelegramMessageSender messageSender;

    public StartCommandHandler(
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
        return "/start".equalsIgnoreCase(command);
    }

    @Override
    public void handle(Update update, String command, String[] args) {
        if (update.getMessage() == null || update.getMessage().getChatId() == null) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        User from = update.getMessage().getFrom();
        String username = from != null ? from.getUserName() : null;
        String firstName = from != null ? from.getFirstName() : null;
        String lastName = from != null ? from.getLastName() : null;

        if (args != null && args.length > 0 && !args[0].isBlank()) {
            String token = args[0].trim();
            log.info("Processing /start with token for chatId {}", chatId);
            try {
                BindRequest req = new BindRequest(token, chatId, username, firstName, lastName);
                BindResponse resp = backendClient.bindTelegram(req);
                if (resp != null && resp.success()) {
                    sessionCache.put(chatId, new ClientProfileDto(
                            true,
                            resp.userId(),
                            resp.fullName(),
                            resp.email(),
                            null,
                            null,
                            resp.role()
                    ));
                    messageSender.sendHtml(chatId, HtmlMessageFormatter.formatLinkSuccess(resp.fullName()));
                } else {
                    messageSender.sendHtml(chatId, HtmlMessageFormatter.formatLinkError("Ответ сервера не подтвердил привязку"));
                }
            } catch (BackendClientException e) {
                log.warn("Binding failed for chatId {}: status={}, message={}", chatId, e.getStatusCode(), e.getMessage());
                messageSender.sendHtml(chatId, HtmlMessageFormatter.formatLinkError(e.getMessage()));
            } catch (Exception e) {
                log.error("Unexpected error during binding for chatId {}: {}", chatId, e.getMessage(), e);
                messageSender.sendHtml(chatId, HtmlMessageFormatter.formatLinkError("Внутренняя ошибка сервиса"));
            }
        } else {
            Optional<ClientProfileDto> profileOpt = sessionCache.resolve(chatId, () -> backendClient.getClientByChatId(chatId));
            if (profileOpt.isPresent()) {
                messageSender.sendHtml(chatId, HtmlMessageFormatter.formatWelcomeLinked(profileOpt.get().fullName()));
            } else {
                messageSender.sendHtml(chatId, HtmlMessageFormatter.formatWelcomeUnlinked());
            }
        }
    }
}
