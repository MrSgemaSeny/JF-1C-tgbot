package kz.zhanfinance.bot.scheduler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.NotificationAckItemDto;
import kz.zhanfinance.bot.client.dto.NotificationAckRequest;
import kz.zhanfinance.bot.client.dto.PendingNotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class OutboxNotificationPoller {

    private static final Logger log = LoggerFactory.getLogger(OutboxNotificationPoller.class);

    private final BackendClient backendClient;
    private final TelegramClient telegramClient;
    private final int batchSize;
    private final long rateLimitDelayMs;
    private final AtomicBoolean processingLock = new AtomicBoolean(false);

    public OutboxNotificationPoller(
            BackendClient backendClient,
            TelegramClient telegramClient,
            @Value("${jf.outbox.batch-size:50}") int batchSize,
            @Value("${jf.outbox.rate-limit-delay-ms:40}") long rateLimitDelayMs
    ) {
        this.backendClient = backendClient;
        this.telegramClient = telegramClient;
        this.batchSize = batchSize;
        this.rateLimitDelayMs = rateLimitDelayMs;
    }

    @Scheduled(fixedDelayString = "${jf.outbox.poll-interval-ms:15000}")
    public void pollAndDispatch() {
        if (!processingLock.compareAndSet(false, true)) {
            log.debug("Outbox poll skipped: previous batch still in progress");
            return;
        }

        try {
            List<PendingNotificationDto> pending = backendClient.getPendingNotifications(batchSize);
            if (pending == null || pending.isEmpty()) {
                return;
            }

            log.info("Processing {} pending telegram notifications", pending.size());
            List<NotificationAckItemDto> acks = new ArrayList<>(pending.size());

            for (PendingNotificationDto item : pending) {
                try {
                    SendMessage message = SendMessage.builder()
                            .chatId(item.chatId().toString())
                            .text(item.message())
                            .parseMode("HTML")
                            .build();

                    telegramClient.execute(message);
                    acks.add(new NotificationAckItemDto(item.id(), "SENT", null));
                } catch (TelegramApiException ex) {
                    log.warn("Telegram error sending notification id={} to chat {}: {}",
                            item.id(), item.chatId(), ex.getMessage());
                    acks.add(new NotificationAckItemDto(item.id(), "FAILED", ex.getMessage()));
                } catch (Exception ex) {
                    log.error("Unexpected error sending notification id={}: {}",
                            item.id(), ex.getMessage(), ex);
                    acks.add(new NotificationAckItemDto(item.id(), "FAILED", ex.getMessage()));
                }

                if (rateLimitDelayMs > 0) {
                    try {
                        Thread.sleep(rateLimitDelayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (!acks.isEmpty()) {
                backendClient.acknowledgeNotifications(new NotificationAckRequest(acks));
                log.info("Acknowledged {} notifications to backend", acks.size());
            }
        } catch (Exception ex) {
            log.error("Outbox poll cycle failed: {}", ex.getMessage(), ex);
        } finally {
            processingLock.set(false);
        }
    }
}
