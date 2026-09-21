package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.client.dto.TaskSummaryDto;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import kz.zhanfinance.bot.service.UserSessionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TasksCommandHandlerTest {

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramMessageSender messageSender;

    private UserSessionCache sessionCache;
    private TasksCommandHandler handler;

    @BeforeEach
    void setUp() {
        sessionCache = new UserSessionCache(5, 100);
        handler = new TasksCommandHandler(backendClient, sessionCache, messageSender);
    }

    private Update createUpdate(Long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        return update;
    }

    @Test
    @DisplayName("/tasks for unlinked user should prompt to link account")
    void tasksUnlinked() {
        Update update = createUpdate(111L);
        when(backendClient.getClientByChatId(111L)).thenReturn(Optional.empty());

        handler.handle(update, "/tasks", new String[0]);

        verify(messageSender).sendHtml(eq(111L), contains("Аккаунт не привязан"));
        verify(backendClient, never()).getClientTasks(anyLong(), anyInt());
    }

    @Test
    @DisplayName("/tasks for linked user with tasks should render active tasks")
    void tasksLinkedWithTasks() {
        Update update = createUpdate(222L);
        ClientProfileDto profile = new ClientProfileDto(
                true, 50L, "Иван Иванов", "ivan@test.com", null, null, "CLIENT"
        );
        sessionCache.put(222L, profile);

        when(backendClient.getClientTasks(50L, 10)).thenReturn(List.of(
                new TaskSummaryDto(101L, "Сдача 1С отчета", "В процессе", "IN_PROGRESS", "2026-09-30", "Бухгалтер")
        ));

        handler.handle(update, "/tasks", new String[0]);

        verify(messageSender).sendHtml(eq(222L), contains("Сдача 1С отчета"));
    }

    @Test
    @DisplayName("/tasks for linked user with no tasks should indicate empty list")
    void tasksLinkedEmpty() {
        Update update = createUpdate(333L);
        ClientProfileDto profile = new ClientProfileDto(
                true, 60L, "Петр", "petr@test.com", null, null, "CLIENT"
        );
        sessionCache.put(333L, profile);

        when(backendClient.getClientTasks(60L, 10)).thenReturn(Collections.emptyList());

        handler.handle(update, "/tasks", new String[0]);

        verify(messageSender).sendHtml(eq(333L), contains("У вас нет активных задач."));
    }
}
