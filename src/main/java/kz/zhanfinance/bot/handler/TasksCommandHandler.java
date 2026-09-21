package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.client.dto.TaskSummaryDto;
import kz.zhanfinance.bot.service.HtmlMessageFormatter;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import kz.zhanfinance.bot.service.UserSessionCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;
import java.util.Optional;

@Component
public class TasksCommandHandler implements CommandHandler {

    private static final Logger log = LoggerFactory.getLogger(TasksCommandHandler.class);

    private final BackendClient backendClient;
    private final UserSessionCache sessionCache;
    private final TelegramMessageSender messageSender;

    public TasksCommandHandler(
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
        return "/tasks".equalsIgnoreCase(command);
    }

    @Override
    public void handle(Update update, String command, String[] args) {
        if (update.getMessage() == null || update.getMessage().getChatId() == null) {
            return;
        }

        Long chatId = update.getMessage().getChatId();
        Optional<ClientProfileDto> profileOpt = sessionCache.resolve(chatId, () -> backendClient.getClientByChatId(chatId));

        if (profileOpt.isEmpty()) {
            messageSender.sendHtml(chatId, HtmlMessageFormatter.formatNotLinkedPrompt());
            return;
        }

        ClientProfileDto profile = profileOpt.get();
        log.debug("Fetching tasks for clientId={}", profile.userId());
        List<TaskSummaryDto> tasks = backendClient.getClientTasks(profile.userId(), 10);
        messageSender.sendHtml(chatId, HtmlMessageFormatter.formatTasksList(tasks));
    }
}
