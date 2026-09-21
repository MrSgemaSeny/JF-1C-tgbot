package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UnlinkCommandHandlerTest {

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramMessageSender messageSender;

    private UserSessionCache sessionCache;
    private UnlinkCommandHandler handler;

    @BeforeEach
    void setUp() {
        sessionCache = new UserSessionCache(5, 100);
        handler = new UnlinkCommandHandler(backendClient, sessionCache, messageSender);
    }

    private Update createUpdate(Long chatId) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        return update;
    }

    @Test
    @DisplayName("/unlink when unlinked should notify user that account is not linked")
    void unlinkWhenNotLinked() {
        Update update = createUpdate(111L);
        when(backendClient.getClientByChatId(111L)).thenReturn(Optional.empty());

        handler.handle(update, "/unlink", new String[0]);

        verify(messageSender).sendHtml(eq(111L), eq("Аккаунт не привязан."));
        verify(backendClient, never()).unlinkByChatId(anyLong());
    }

    @Test
    @DisplayName("/unlink when linked should call backend unlink, invalidate cache and send confirmation")
    void unlinkSuccess() {
        Update update = createUpdate(222L);
        ClientProfileDto profile = new ClientProfileDto(
                true, 50L, "Имя", "email", null, null, "CLIENT"
        );
        sessionCache.put(222L, profile);

        when(backendClient.unlinkByChatId(222L)).thenReturn(true);

        handler.handle(update, "/unlink", new String[0]);

        verify(backendClient).unlinkByChatId(222L);
        assertThat(sessionCache.get(222L)).isEmpty();
        verify(messageSender).sendHtml(eq(222L), contains("Telegram успешно отвязан"));
    }

    @Test
    @DisplayName("/unlink backend failure should inform user with error message")
    void unlinkBackendFailure() {
        Update update = createUpdate(333L);
        ClientProfileDto profile = new ClientProfileDto(
                true, 60L, "Имя", "email", null, null, "CLIENT"
        );
        sessionCache.put(333L, profile);

        when(backendClient.unlinkByChatId(333L)).thenReturn(false);

        handler.handle(update, "/unlink", new String[0]);

        verify(backendClient).unlinkByChatId(333L);
        verify(messageSender).sendHtml(eq(333L), contains("Не удалось выполнить отвязку"));
    }
}
