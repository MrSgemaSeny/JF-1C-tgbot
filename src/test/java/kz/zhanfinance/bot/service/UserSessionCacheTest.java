package kz.zhanfinance.bot.service;

import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UserSessionCacheTest {

    private UserSessionCache sessionCache;

    @BeforeEach
    void setUp() {
        sessionCache = new UserSessionCache(5, 100);
    }

    @Test
    @DisplayName("put and get should store and retrieve client profile")
    void putAndGet() {
        ClientProfileDto profile = new ClientProfileDto(
                true, 10L, "Имя Фамилия", "test@example.com", "+77001112233", "Компания", "CLIENT"
        );

        sessionCache.put(12345L, profile);

        Optional<ClientProfileDto> retrieved = sessionCache.get(12345L);
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().userId()).isEqualTo(10L);
        assertThat(retrieved.get().fullName()).isEqualTo("Имя Фамилия");
    }

    @Test
    @DisplayName("invalidate should remove specific entry from cache")
    void invalidate() {
        ClientProfileDto profile = new ClientProfileDto(
                true, 10L, "Имя", "test@test.com", null, null, "CLIENT"
        );
        sessionCache.put(12345L, profile);
        assertThat(sessionCache.get(12345L)).isPresent();

        sessionCache.invalidate(12345L);
        assertThat(sessionCache.get(12345L)).isEmpty();
    }

    @Test
    @DisplayName("resolve should invoke loader only on cache miss")
    void resolve() {
        ClientProfileDto profile = new ClientProfileDto(
                true, 20L, "Клиент 20", "client20@example.com", null, null, "CLIENT"
        );

        AtomicInteger loaderCalls = new AtomicInteger(0);

        // First call: cache miss -> loader invoked
        Optional<ClientProfileDto> first = sessionCache.resolve(99999L, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(profile);
        });

        assertThat(first).isPresent();
        assertThat(first.get().userId()).isEqualTo(20L);
        assertThat(loaderCalls.get()).isEqualTo(1);

        // Second call: cache hit -> loader NOT invoked
        Optional<ClientProfileDto> second = sessionCache.resolve(99999L, () -> {
            loaderCalls.incrementAndGet();
            return Optional.of(profile);
        });

        assertThat(second).isPresent();
        assertThat(loaderCalls.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("null checks for chatId should not throw exceptions")
    void nullChatIdHandling() {
        assertThat(sessionCache.get(null)).isEmpty();
        sessionCache.put(null, null);
        sessionCache.invalidate(null);
        assertThat(sessionCache.resolve(null, Optional::empty)).isEmpty();
    }
}
