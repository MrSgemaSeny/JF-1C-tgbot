package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.client.dto.DocumentSummaryDto;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StatusCommandHandlerTest {

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramMessageSender messageSender;

    private UserSessionCache sessionCache;
    private StatusCommandHandler handler;

    @BeforeEach
    void setUp() {
        sessionCache = new UserSessionCache(5, 100);
        handler = new StatusCommandHandler(backendClient, sessionCache, messageSender);
    }

    private Update createUpdate(Long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        return update;
    }

    @Test
    @DisplayName("/status for unlinked user should prompt to link account")
    void statusUnlinked() {
        Update update = createUpdate(111L);
        when(backendClient.getClientByChatId(111L)).thenReturn(Optional.empty());

        handler.handle(update, "/status", new String[0]);

        verify(messageSender).sendHtml(eq(111L), contains("Аккаунт не привязан"));
    }

    @Test
    @DisplayName("/status for linked user should render account summary card")
    void statusLinked() {
        Update update = createUpdate(222L);
        ClientProfileDto profile = new ClientProfileDto(
                true, 50L, "Азамат Бекетов", "azamat@beketov.kz", "+77071234567", "ТОО Алтын Дан", "CLIENT"
        );
        sessionCache.put(222L, profile);

        when(backendClient.getClientTasks(50L, 50)).thenReturn(List.of(
                new TaskSummaryDto(1L, "T1", "S1", "P", null, null),
                new TaskSummaryDto(2L, "T2", "S2", "P", null, null)
        ));
        when(backendClient.getClientDocuments(50L, 50)).thenReturn(List.of(
                new DocumentSummaryDto(10L, "D1.pdf", "application/pdf", 100L, "OK", Instant.now())
        ));

        handler.handle(update, "/status", new String[0]);

        verify(messageSender).sendHtml(eq(222L), contains("Азамат Бекетов"));
        verify(messageSender).sendHtml(eq(222L), contains("ТОО Алтын Дан"));
        verify(messageSender).sendHtml(eq(222L), contains("Активных задач:</b> 2"));
        verify(messageSender).sendHtml(eq(222L), contains("Документов:</b> 1"));
    }
}
