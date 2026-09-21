package kz.zhanfinance.bot.bot;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ZhanFinanceLongPollingBotTest {

    @Mock
    private CommandDispatcher dispatcher;

    @Test
    @DisplayName("ZhanFinanceLongPollingBot should return configured token and updates consumer")
    void botConfiguration() {
        ZhanFinanceLongPollingBot bot = new ZhanFinanceLongPollingBot("test_token_123", dispatcher);

        assertThat(bot.getBotToken()).isEqualTo("test_token_123");
        assertThat(bot.getUpdatesConsumer()).isSameAs(bot);
    }

    @Test
    @DisplayName("consume should forward update to dispatcher")
    void consumeForwardsToDispatcher() {
        ZhanFinanceLongPollingBot bot = new ZhanFinanceLongPollingBot("test_token_123", dispatcher);
        Update update = mock(Update.class);

        bot.consume(update);

        verify(dispatcher).dispatch(update);
    }

    @Test
    @DisplayName("consume should catch and suppress exceptions to avoid killing consumer thread")
    void consumeSuppressesExceptions() {
        ZhanFinanceLongPollingBot bot = new ZhanFinanceLongPollingBot("test_token_123", dispatcher);
        Update update = mock(Update.class);

        doThrow(new RuntimeException("Simulated unexpected dispatch failure"))
                .when(dispatcher).dispatch(update);

        // Should not throw
        bot.consume(update);

        verify(dispatcher).dispatch(update);
    }
}
