package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.client.dto.DocumentSummaryDto;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocsCommandHandlerTest {

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramMessageSender messageSender;

    private UserSessionCache sessionCache;
    private DocsCommandHandler handler;

    @BeforeEach
    void setUp() {
        sessionCache = new UserSessionCache(5, 100);
        handler = new DocsCommandHandler(backendClient, sessionCache, messageSender);
    }

    private Update createUpdate(Long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        return update;
    }

    @Test
    @DisplayName("/docs for unlinked user should prompt to link account")
    void docsUnlinked() {
        Update update = createUpdate(111L);
        when(backendClient.getClientByChatId(111L)).thenReturn(Optional.empty());

        handler.handle(update, "/docs", new String[0]);

        verify(messageSender).sendHtml(eq(111L), contains("Аккаунт не привязан"));
        verify(backendClient, never()).getClientDocuments(anyLong(), anyInt());
    }

    @Test
    @DisplayName("/docs for linked user with documents should render recent documents")
    void docsLinkedWithDocs() {
        Update update = createUpdate(222L);
        ClientProfileDto profile = new ClientProfileDto(
                true, 50L, "Иван Иванов", "ivan@test.com", null, null, "CLIENT"
        );
        sessionCache.put(222L, profile);

        when(backendClient.getClientDocuments(50L, 5)).thenReturn(List.of(
                new DocumentSummaryDto(201L, "Акт_сверки.pdf", "application/pdf", 2048L, "SIGNED", Instant.now())
        ));

        handler.handle(update, "/docs", new String[0]);

        verify(messageSender).sendHtml(eq(222L), contains("Акт_сверки.pdf"));
    }

    @Test
    @DisplayName("/docs for linked user with no docs should show empty message")
    void docsLinkedEmpty() {
        Update update = createUpdate(333L);
        ClientProfileDto profile = new ClientProfileDto(
                true, 60L, "Петр", "petr@test.com", null, null, "CLIENT"
        );
        sessionCache.put(333L, profile);

        when(backendClient.getClientDocuments(60L, 5)).thenReturn(Collections.emptyList());

        handler.handle(update, "/docs", new String[0]);

        verify(messageSender).sendHtml(eq(333L), contains("У вас пока нет документов."));
    }
}
