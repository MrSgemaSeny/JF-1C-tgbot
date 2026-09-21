package kz.zhanfinance.bot.client;

import kz.zhanfinance.bot.client.dto.*;

import java.util.List;
import java.util.Optional;

public interface BackendClient {
    BindResponse bindTelegram(BindRequest request);
    Optional<ClientProfileDto> getClientByChatId(Long chatId);
    List<TaskSummaryDto> getClientTasks(Long clientId, int limit);
    List<DocumentSummaryDto> getClientDocuments(Long clientId, int limit);
    boolean unlinkByChatId(Long chatId);
    List<PendingNotificationDto> getPendingNotifications(int limit);
    NotificationAckResponse acknowledgeNotifications(NotificationAckRequest request);
}
