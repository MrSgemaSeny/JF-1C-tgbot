package kz.zhanfinance.bot.scheduler;

import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.dto.NotificationAckItemDto;
import kz.zhanfinance.bot.client.dto.NotificationAckRequest;
import kz.zhanfinance.bot.client.dto.NotificationAckResponse;
import kz.zhanfinance.bot.client.dto.PendingNotificationDto;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxNotificationPollerTest {

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramClient telegramClient;

    private OutboxNotificationPoller poller;

    @BeforeEach
    void setUp() {
        poller = new OutboxNotificationPoller(backendClient, telegramClient, 50, 0L);
    }

    @Test
    @DisplayName("pollAndDispatch should dispatch messages and ACK with SENT on success")
    void pollAndDispatchSuccess() throws Exception {
        PendingNotificationDto notif1 = new PendingNotificationDto(
                101L, 12345L, 1L, "<b>Сообщение 1</b>", 0, Instant.now()
        );
        PendingNotificationDto notif2 = new PendingNotificationDto(
                102L, 67890L, 2L, "<b>Сообщение 2</b>", 0, Instant.now()
        );

        when(backendClient.getPendingNotifications(50)).thenReturn(List.of(notif1, notif2));
        when(backendClient.acknowledgeNotifications(any(NotificationAckRequest.class)))
                .thenReturn(new NotificationAckResponse(2, 2, 2, 0));

        poller.pollAndDispatch();

        verify(telegramClient, times(2)).execute(any(SendMessage.class));

        ArgumentCaptor<NotificationAckRequest> ackCaptor = ArgumentCaptor.forClass(NotificationAckRequest.class);
        verify(backendClient).acknowledgeNotifications(ackCaptor.capture());

        List<NotificationAckItemDto> items = ackCaptor.getValue().processed();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).id()).isEqualTo(101L);
        assertThat(items.get(0).status()).isEqualTo("SENT");
        assertThat(items.get(1).id()).isEqualTo(102L);
        assertThat(items.get(1).status()).isEqualTo("SENT");
    }

    @Test
    @DisplayName("pollAndDispatch should capture TelegramApiException and mark item as FAILED")
    void pollAndDispatchWithTelegramError() throws Exception {
        PendingNotificationDto notif = new PendingNotificationDto(
                201L, 12345L, 1L, "<b>Уведомление</b>", 0, Instant.now()
        );

        when(backendClient.getPendingNotifications(50)).thenReturn(List.of(notif));
        when(telegramClient.execute(any(SendMessage.class)))
                .thenThrow(new TelegramApiException("Forbidden: bot was blocked by the user"));

        poller.pollAndDispatch();

        ArgumentCaptor<NotificationAckRequest> ackCaptor = ArgumentCaptor.forClass(NotificationAckRequest.class);
        verify(backendClient).acknowledgeNotifications(ackCaptor.capture());

        List<NotificationAckItemDto> items = ackCaptor.getValue().processed();
        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(201L);
        assertThat(items.get(0).status()).isEqualTo("FAILED");
        assertThat(items.get(0).error()).contains("Forbidden: bot was blocked");
    }

    @Test
    @DisplayName("pollAndDispatch should do nothing when queue is empty")
    void pollEmptyQueue() throws Exception {
        when(backendClient.getPendingNotifications(50)).thenReturn(Collections.emptyList());

        poller.pollAndDispatch();

        verify(telegramClient, never()).execute(any(SendMessage.class));
        verify(backendClient, never()).acknowledgeNotifications(any());
    }
}
