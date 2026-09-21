package kz.zhanfinance.bot.handler;

import kz.zhanfinance.bot.service.TelegramMessageSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HelpCommandHandlerTest {

    @Mock
    private TelegramMessageSender messageSender;

    @InjectMocks
    private HelpCommandHandler handler;

    @Test
    @DisplayName("canHandle should accept /help and /HELP case-insensitively")
    void canHandle() {
        assertThat(handler.canHandle("/help")).isTrue();
        assertThat(handler.canHandle("/HELP")).isTrue();
        assertThat(handler.canHandle("/start")).isFalse();
    }

    @Test
    @DisplayName("handle should transmit formatted help message with WhatsApp contact")
    void handle() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.getMessage()).thenReturn(message);
        when(message.getChatId()).thenReturn(777L);

        handler.handle(update, "/help", new String[0]);

        verify(messageSender).sendHtml(eq(777L), contains("Команды бота ЖАН FINANCE"));
        verify(messageSender).sendHtml(eq(777L), contains("https://wa.me/77750584021"));
    }
}
