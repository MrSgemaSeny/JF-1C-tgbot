package kz.zhanfinance.bot.bot;

import kz.zhanfinance.bot.handler.CommandHandler;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommandDispatcherTest {

    @Mock
    private CommandHandler startHandler;

    @Mock
    private CommandHandler tasksHandler;

    @Mock
    private TelegramMessageSender messageSender;

    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        lenient().when(startHandler.canHandle(anyString()))
                .thenAnswer(inv -> "/start".equalsIgnoreCase(inv.getArgument(0)));
        lenient().when(tasksHandler.canHandle(anyString()))
                .thenAnswer(inv -> "/tasks".equalsIgnoreCase(inv.getArgument(0)));

        dispatcher = new CommandDispatcher(List.of(startHandler, tasksHandler), messageSender);
    }

    @Test
    @DisplayName("dispatch should route command with arguments correctly")
    void dispatchCommandWithArgs() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/start token_abc_123");

        dispatcher.dispatch(update);

        ArgumentCaptor<String[]> argsCaptor = ArgumentCaptor.forClass(String[].class);
        verify(startHandler).handle(eq(update), eq("/start"), argsCaptor.capture());
        assertThat(argsCaptor.getValue()).containsExactly("token_abc_123");
        verify(tasksHandler, never()).handle(any(), any(), any());
    }

    @Test
    @DisplayName("dispatch should strip bot username suffix from command")
    void dispatchWithBotUsernameSuffix() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/tasks@zhan_finance_bot");

        dispatcher.dispatch(update);

        verify(tasksHandler).handle(eq(update), eq("/tasks"), any());
    }

    @Test
    @DisplayName("dispatch should send unknown command message when no handler matches")
    void dispatchUnknownCommand() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("/unknown");
        when(message.getChatId()).thenReturn(111L);

        dispatcher.dispatch(update);

        verify(messageSender).sendHtml(eq(111L), contains("Неизвестная команда"));
    }

    @Test
    @DisplayName("dispatch should ignore non-slash messages")
    void ignoreNonSlashMessages() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.hasText()).thenReturn(true);
        when(message.getText()).thenReturn("Just plain text message");

        dispatcher.dispatch(update);

        verify(startHandler, never()).handle(any(), any(), any());
        verify(tasksHandler, never()).handle(any(), any(), any());
        verifyNoInteractions(messageSender);
    }

    @Test
    @DisplayName("dispatch should handle null or empty updates gracefully")
    void handleNullOrEmpty() {
        dispatcher.dispatch(null);

        Update update = mock(Update.class);
        when(update.hasMessage()).thenReturn(false);
        dispatcher.dispatch(update);

        verify(startHandler, never()).handle(any(), any(), any());
        verify(tasksHandler, never()).handle(any(), any(), any());
        verifyNoInteractions(messageSender);
    }
}
