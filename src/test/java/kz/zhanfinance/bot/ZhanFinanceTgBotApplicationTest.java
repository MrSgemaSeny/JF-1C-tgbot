package kz.zhanfinance.bot;

import kz.zhanfinance.bot.bot.CommandDispatcher;
import kz.zhanfinance.bot.bot.ZhanFinanceLongPollingBot;
import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.scheduler.OutboxNotificationPoller;
import kz.zhanfinance.bot.service.UserSessionCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ZhanFinanceTgBotApplicationTest {

    @MockBean
    private TelegramClient telegramClient;

    @MockBean
    private TelegramBotsLongPollingApplication telegramBotsLongPollingApplication;

    @Autowired(required = false)
    private ZhanFinanceLongPollingBot bot;

    @Autowired(required = false)
    private CommandDispatcher dispatcher;

    @Autowired(required = false)
    private BackendClient backendClient;

    @Autowired(required = false)
    private UserSessionCache sessionCache;

    @Autowired(required = false)
    private OutboxNotificationPoller poller;

    @Test
    @DisplayName("Application context loads with all essential components wired")
    void contextLoads() {
        assertThat(bot).isNotNull();
        assertThat(dispatcher).isNotNull();
        assertThat(backendClient).isNotNull();
        assertThat(sessionCache).isNotNull();
        assertThat(poller).isNotNull();
    }
}
