package kz.zhanfinance.bot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.generics.TelegramClient;

@Configuration
public class TelegramBotConfig {

    @Bean
    @ConditionalOnMissingBean
    public TelegramClient telegramClient(@Value("${bot.token:}") String botToken) {
        String token = (botToken != null && !botToken.isBlank()) ? botToken : "000000:dummy_token";
        return new OkHttpTelegramClient(token);
    }
}
