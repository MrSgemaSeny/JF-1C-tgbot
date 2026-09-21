package kz.zhanfinance.bot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.objects.Update;

@Component
public class ZhanFinanceLongPollingBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(ZhanFinanceLongPollingBot.class);

    private final String botToken;
    private final CommandDispatcher commandDispatcher;

    public ZhanFinanceLongPollingBot(
            @Value("${bot.token:}") String botToken,
            CommandDispatcher commandDispatcher
    ) {
        this.botToken = botToken;
        this.commandDispatcher = commandDispatcher;
    }

    @Override
    public String getBotToken() {
        return botToken != null ? botToken : "";
    }

    @Override
    public LongPollingSingleThreadUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        try {
            commandDispatcher.dispatch(update);
        } catch (Exception ex) {
            log.error("Unhandled error processing update {}: {}",
                    update != null ? update.getUpdateId() : null, ex.getMessage(), ex);
        }
    }
}
