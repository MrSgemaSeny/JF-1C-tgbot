package kz.zhanfinance.bot.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

@Service
public class UserSessionCache {

    private final Cache<Long, ClientProfileDto> cache;

    public UserSessionCache(
            @Value("${jf.cache.session-ttl-minutes:5}") int ttlMinutes,
            @Value("${jf.cache.max-entries:5000}") int maxEntries
    ) {
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxEntries)
                .expireAfterWrite(Duration.ofMinutes(ttlMinutes))
                .build();
    }

    public Optional<ClientProfileDto> get(Long chatId) {
        if (chatId == null) return Optional.empty();
        return Optional.ofNullable(cache.getIfPresent(chatId));
    }

    public void put(Long chatId, ClientProfileDto profile) {
        if (chatId != null && profile != null) {
            cache.put(chatId, profile);
        }
    }

    public void invalidate(Long chatId) {
        if (chatId != null) {
            cache.invalidate(chatId);
        }
    }

    public void clear() {
        cache.invalidateAll();
    }

    public Optional<ClientProfileDto> resolve(Long chatId, Supplier<Optional<ClientProfileDto>> loader) {
        if (chatId == null) return Optional.empty();
        ClientProfileDto cached = cache.getIfPresent(chatId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<ClientProfileDto> loaded = loader.get();
        loaded.ifPresent(p -> cache.put(chatId, p));
        return loaded;
    }
}
