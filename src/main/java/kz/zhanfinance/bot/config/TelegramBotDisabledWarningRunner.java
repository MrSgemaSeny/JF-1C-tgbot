package kz.zhanfinance.bot.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${bot.token:}'.isEmpty() || '${bot.token:}'.startsWith('000000') || '${bot.token:}'.equals('change-me')")
public class TelegramBotDisabledWarningRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotDisabledWarningRunner.class);

    @Override
    public void run(ApplicationArguments args) {
        log.warn("================================================================================");
        log.warn("TELEGRAM_BOT_TOKEN is not configured or is set to a dummy value.");
        log.warn("Telegram bot long polling is DISABLED to prevent Telegram API 404 connection errors.");
        log.warn("To enable Telegram Bot interaction, set the environment variable:");
        log.warn("  PowerShell: $env:TELEGRAM_BOT_TOKEN=\"your_bot_token_from_BotFather\"");
        log.warn("  cmd:        set TELEGRAM_BOT_TOKEN=your_bot_token_from_BotFather");
        log.warn("  bash:       export TELEGRAM_BOT_TOKEN=your_bot_token_from_BotFather");
        log.warn("================================================================================");
    }
}
