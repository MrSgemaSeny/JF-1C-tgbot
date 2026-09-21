package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.BackendClientException;
import kz.zhanfinance.bot.client.dto.BindRequest;
import kz.zhanfinance.bot.client.dto.BindResponse;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import kz.zhanfinance.bot.service.UserSessionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartCommandHandlerTest {

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramMessageSender messageSender;

    private UserSessionCache sessionCache;
    private StartCommandHandler handler;

    @BeforeEach
    void setUp() {
        sessionCache = new UserSessionCache(5, 100);
        handler = new StartCommandHandler(backendClient, sessionCache, messageSender);
    }

    private Update createUpdate(Long chatId, String username, String firstName, String lastName) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(chatId);
        when(message.getFrom()).thenReturn(user);
        when(user.getUserName()).thenReturn(username);
        when(user.getFirstName()).thenReturn(firstName);
        when(user.getLastName()).thenReturn(lastName);

        return update;
    }

    @Test
    @DisplayName("/start with token should call backend bind and update cache on success")
    void startWithTokenSuccess() {
        Update update = createUpdate(12345L, "testuser", "Арман", "Сериков");

        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenReturn(new BindResponse(true, 42L, "Арман Сериков", "arman@example.kz", "CLIENT"));

        handler.handle(update, "/start", new String[]{"valid-token-123"});

        ArgumentCaptor<BindRequest> reqCaptor = ArgumentCaptor.forClass(BindRequest.class);
        verify(backendClient).bindTelegram(reqCaptor.capture());
        BindRequest req = reqCaptor.getValue();
        assertThat(req.token()).isEqualTo("valid-token-123");
        assertThat(req.chatId()).isEqualTo(12345L);
        assertThat(req.telegramUsername()).isEqualTo("testuser");

        // Verify session cache updated
        Optional<ClientProfileDto> cached = sessionCache.get(12345L);
        assertThat(cached).isPresent();
        assertThat(cached.get().fullName()).isEqualTo("Арман Сериков");

        // Verify message sent
        verify(messageSender).sendHtml(eq(12345L), contains("Аккаунт успешно привязан"));
    }

    @Test
    @DisplayName("/start with token should handle backend 400/404 exception gracefully")
    void startWithInvalidToken() {
        Update update = createUpdate(12345L, "testuser", "Иван", null);

        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenThrow(new BackendClientException(400, "Token expired or invalid"));

        handler.handle(update, "/start", new String[]{"expired-token"});

        verify(messageSender).sendHtml(eq(12345L), contains("Не удалось привязать аккаунт"));
        assertThat(sessionCache.get(12345L)).isEmpty();
    }

    @Test
    @DisplayName("/start without token for linked user should show welcome with commands")
    void startWithoutTokenAlreadyLinked() {
        Update update = createUpdate(12345L, "testuser", "Арман", null);

        ClientProfileDto profile = new ClientProfileDto(
                true, 42L, "Арман Сериков", "arman@example.kz", null, null, "CLIENT"
        );
        sessionCache.put(12345L, profile);

        handler.handle(update, "/start", new String[0]);

        verify(messageSender).sendHtml(eq(12345L), contains("Вы уже авторизованы как <b>Арман Сериков</b>"));
        verifyNoInteractions(backendClient);
    }

    @Test
    @DisplayName("/start without token for unlinked user should show instructions")
    void startWithoutTokenUnlinked() {
        Update update = createUpdate(99999L, "newuser", "Новый", null);
        when(backendClient.getClientByChatId(99999L)).thenReturn(Optional.empty());

        handler.handle(update, "/start", new String[0]);

        verify(messageSender).sendHtml(eq(99999L), contains("Добро пожаловать в ЖАН FINANCE Bot"));
    }
}
