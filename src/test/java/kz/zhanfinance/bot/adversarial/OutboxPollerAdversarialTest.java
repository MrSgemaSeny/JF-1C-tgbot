package kz.zhanfinance.bot.adversarial;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.NotificationAckItemDto;
import kz.zhanfinance.bot.client.dto.NotificationAckRequest;
import kz.zhanfinance.bot.client.dto.NotificationAckResponse;
import kz.zhanfinance.bot.client.dto.PendingNotificationDto;
import kz.zhanfinance.bot.scheduler.OutboxNotificationPoller;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPollerAdversarialTest {

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramClient telegramClient;

    @Test
    @DisplayName("Adversarial Rate Limiting: 10 messages with 40ms throttle must enforce >= 400ms duration (<= 25 msg/s)")
    void testThrottleDurationAndThroughput() throws Exception {
        int messageCount = 10;
        long throttleMs = 40L;
        List<PendingNotificationDto> items = new ArrayList<>();
        for (long i = 1; i <= messageCount; i++) {
            items.add(new PendingNotificationDto(
                    i, 10000L + i, i, "Message #" + i, 0, Instant.now()
            ));
        }

        when(backendClient.getPendingNotifications(50)).thenReturn(items);
        when(backendClient.acknowledgeNotifications(any(NotificationAckRequest.class)))
                .thenReturn(new NotificationAckResponse(messageCount, messageCount, messageCount, 0));

        OutboxNotificationPoller poller = new OutboxNotificationPoller(
                backendClient, telegramClient, 50, throttleMs
        );

        long startNs = System.nanoTime();
        poller.pollAndDispatch();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);

        verify(telegramClient, times(messageCount)).execute(any(SendMessage.class));
        verify(backendClient, times(1)).acknowledgeNotifications(any(NotificationAckRequest.class));

        long expectedMinDurationMs = messageCount * throttleMs - 20; // 380ms with 20ms timer jitter tolerance
        assertThat(elapsedMs)
                .as("Total elapsed time for %d messages with %d ms throttle must be at least %d ms",
                        messageCount, throttleMs, expectedMinDurationMs)
                .isGreaterThanOrEqualTo(expectedMinDurationMs);

        double messagesPerSecond = (double) messageCount / ((double) elapsedMs / 1000.0);
        assertThat(messagesPerSecond)
                .as("Throughput must not exceed Telegram safe ceiling of 25 msg/s")
                .isLessThanOrEqualTo(26.0); // allow minor delta
    }

    @Test
    @DisplayName("Adversarial Error Capture: Heterogeneous failures must not abort batch and must correctly map SENT/FAILED statuses")
    void testHeterogeneousErrorCaptureAndBatchAck() throws Exception {
        PendingNotificationDto item1 = new PendingNotificationDto(101L, 1111L, 1L, "Msg 1 Success", 0, Instant.now());
        PendingNotificationDto item2 = new PendingNotificationDto(102L, 2222L, 2L, "Msg 2 Blocked", 0, Instant.now());
        PendingNotificationDto item3 = new PendingNotificationDto(103L, 3333L, 3L, "Msg 3 ChatNotFound", 0, Instant.now());
        PendingNotificationDto item4 = new PendingNotificationDto(104L, 4444L, 4L, "Msg 4 RuntimeException", 0, Instant.now());
        PendingNotificationDto item5 = new PendingNotificationDto(105L, 5555L, 5L, "Msg 5 Success", 0, Instant.now());

        when(backendClient.getPendingNotifications(50))
                .thenReturn(List.of(item1, item2, item3, item4, item5));

        // Mock behaviors per message
        doAnswer(invocation -> null).when(telegramClient).execute(argThat((SendMessage m) -> m != null && "1111".equals(m.getChatId())));
        doThrow(new TelegramApiException("Forbidden: bot was blocked by the user")).when(telegramClient).execute(argThat((SendMessage m) -> m != null && "2222".equals(m.getChatId())));
        doThrow(new TelegramApiException("Bad Request: chat not found")).when(telegramClient).execute(argThat((SendMessage m) -> m != null && "3333".equals(m.getChatId())));
        doThrow(new RuntimeException("Connection reset by peer")).when(telegramClient).execute(argThat((SendMessage m) -> m != null && "4444".equals(m.getChatId())));
        doAnswer(invocation -> null).when(telegramClient).execute(argThat((SendMessage m) -> m != null && "5555".equals(m.getChatId())));

        ArgumentCaptor<NotificationAckRequest> ackCaptor = ArgumentCaptor.forClass(NotificationAckRequest.class);
        when(backendClient.acknowledgeNotifications(ackCaptor.capture()))
                .thenReturn(new NotificationAckResponse(5, 5, 2, 3));

        OutboxNotificationPoller poller = new OutboxNotificationPoller(backendClient, telegramClient, 50, 0L);
        poller.pollAndDispatch();

        verify(telegramClient, times(5)).execute(any(SendMessage.class));

        NotificationAckRequest ackRequest = ackCaptor.getValue();
        assertThat(ackRequest).isNotNull();
        List<NotificationAckItemDto> acks = ackRequest.processed();
        assertThat(acks).hasSize(5);

        // Item 1: SENT
        assertThat(acks.get(0).id()).isEqualTo(101L);
        assertThat(acks.get(0).status()).isEqualTo("SENT");
        assertThat(acks.get(0).error()).isNull();

        // Item 2: FAILED (Forbidden)
        assertThat(acks.get(1).id()).isEqualTo(102L);
        assertThat(acks.get(1).status()).isEqualTo("FAILED");
        assertThat(acks.get(1).error()).contains("Forbidden: bot was blocked by the user");

        // Item 3: FAILED (Chat Not Found)
        assertThat(acks.get(2).id()).isEqualTo(103L);
        assertThat(acks.get(2).status()).isEqualTo("FAILED");
        assertThat(acks.get(2).error()).contains("Bad Request: chat not found");

        // Item 4: FAILED (RuntimeException)
        assertThat(acks.get(3).id()).isEqualTo(104L);
        assertThat(acks.get(3).status()).isEqualTo("FAILED");
        assertThat(acks.get(3).error()).contains("Connection reset by peer");

        // Item 5: SENT
        assertThat(acks.get(4).id()).isEqualTo(105L);
        assertThat(acks.get(4).status()).isEqualTo("SENT");
        assertThat(acks.get(4).error()).isNull();
    }

    @Test
    @DisplayName("Adversarial Concurrency: Overlapping poll invocations must be prevented by processingLock")
    void testConcurrentExecutionLocking() throws Exception {
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch unblockExecution = new CountDownLatch(1);

        PendingNotificationDto item = new PendingNotificationDto(999L, 12345L, 1L, "Slow message", 0, Instant.now());
        when(backendClient.getPendingNotifications(50)).thenReturn(List.of(item));

        doAnswer(invocation -> {
            executionStarted.countDown();
            unblockExecution.await(5, TimeUnit.SECONDS);
            return null;
        }).when(telegramClient).execute(any(SendMessage.class));

        OutboxNotificationPoller poller = new OutboxNotificationPoller(backendClient, telegramClient, 50, 0L);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        Future<?> future1 = executor.submit(poller::pollAndDispatch);

        assertThat(executionStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // Second invocation should immediately return because lock is held
        poller.pollAndDispatch();

        unblockExecution.countDown();
        future1.get(2, TimeUnit.SECONDS);

        // backendClient.getPendingNotifications should only have been called ONCE
        verify(backendClient, times(1)).getPendingNotifications(50);
        verify(telegramClient, times(1)).execute(any(SendMessage.class));

        // After completion, lock must be free for next cycle
        poller.pollAndDispatch();
        verify(backendClient, times(2)).getPendingNotifications(50);

        executor.shutdownNow();
    }

    @Test
    @DisplayName("Adversarial Interruption: Interrupted thread during rate limit delay preserves interrupted status and ACKs sent items")
    void testInterruptedThreadBehavior() throws Exception {
        PendingNotificationDto item1 = new PendingNotificationDto(1L, 123L, 1L, "Msg 1", 0, Instant.now());
        PendingNotificationDto item2 = new PendingNotificationDto(2L, 456L, 1L, "Msg 2", 0, Instant.now());

        when(backendClient.getPendingNotifications(50)).thenReturn(List.of(item1, item2));

        OutboxNotificationPoller poller = new OutboxNotificationPoller(backendClient, telegramClient, 50, 2000L);

        AtomicBoolean threadWasInterrupted = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            poller.pollAndDispatch();
            if (Thread.currentThread().isInterrupted()) {
                threadWasInterrupted.set(true);
            }
        });

        worker.start();
        Thread.sleep(100); // Let first message execute and enter sleep
        worker.interrupt();
        worker.join(2000);

        assertThat(threadWasInterrupted.get()).isTrue();

        // Verify first message was sent and acknowledged, but second was aborted
        verify(telegramClient, times(1)).execute(any(SendMessage.class));
        ArgumentCaptor<NotificationAckRequest> captor = ArgumentCaptor.forClass(NotificationAckRequest.class);
        verify(backendClient).acknowledgeNotifications(captor.capture());
        assertThat(captor.getValue().processed()).hasSize(1);
        assertThat(captor.getValue().processed().get(0).id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Adversarial Backend ACK Crash: Poller survives unhandled runtime exception in backendClient.acknowledgeNotifications")
    void testPollerSurvivesBackendAckCrash() throws Exception {
        PendingNotificationDto item = new PendingNotificationDto(1L, 123L, 1L, "Msg", 0, Instant.now());
        when(backendClient.getPendingNotifications(50)).thenReturn(List.of(item));
        when(backendClient.acknowledgeNotifications(any())).thenThrow(new RuntimeException("Database timeout"));

        OutboxNotificationPoller poller = new OutboxNotificationPoller(backendClient, telegramClient, 50, 0L);

        // Must not propagate exception out of pollAndDispatch
        poller.pollAndDispatch();

        // Lock must be released despite the exception
        poller.pollAndDispatch();
        verify(backendClient, times(2)).getPendingNotifications(50);
    }

    @Test
    @DisplayName("Adversarial Empty and Null: Poller handles null and empty pending lists without exceptions or ACKs")
    void testNullAndEmptyPendingListHandling() throws Exception {
        OutboxNotificationPoller poller = new OutboxNotificationPoller(backendClient, telegramClient, 50, 0L);

        // Case 1: Backend returns null
        when(backendClient.getPendingNotifications(50)).thenReturn(null);
        poller.pollAndDispatch();
        verify(telegramClient, never()).execute(any(SendMessage.class));
        verify(backendClient, never()).acknowledgeNotifications(any());

        // Case 2: Backend returns empty list
        when(backendClient.getPendingNotifications(50)).thenReturn(Collections.emptyList());
        poller.pollAndDispatch();
        verify(telegramClient, never()).execute(any(SendMessage.class));
        verify(backendClient, never()).acknowledgeNotifications(any());
    }
}
