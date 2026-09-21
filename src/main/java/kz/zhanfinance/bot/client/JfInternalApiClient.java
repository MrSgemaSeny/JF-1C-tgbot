package kz.zhanfinance.bot.client;

import kz.zhanfinance.bot.client.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
public class JfInternalApiClient implements BackendClient {

    private static final Logger log = LoggerFactory.getLogger(JfInternalApiClient.class);

    private final RestClient backendRestClient;

    public JfInternalApiClient(RestClient backendRestClient) {
        this.backendRestClient = backendRestClient;
    }

    @Override
    public BindResponse bindTelegram(BindRequest request) {
        try {
            return backendRestClient.post()
                    .uri("/v1/internal/telegram/bind")
                    .body(request)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, resp) -> {
                        String body = new String(resp.getBody().readAllBytes());
                        throw new BackendClientException(resp.getStatusCode().value(), "Binding failed: " + body);
                    })
                    .body(BindResponse.class);
        } catch (BackendClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to bind telegram account for chatId {}: {}", request.chatId(), e.getMessage());
            throw new BackendClientException("Error connecting to backend: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<ClientProfileDto> getClientByChatId(Long chatId) {
        try {
            return backendRestClient.get()
                    .uri("/v1/internal/telegram/chat/{chatId}/client", chatId)
                    .exchange((req, resp) -> {
                        if (resp.getStatusCode().value() == 404) {
                            return Optional.empty();
                        }
                        if (resp.getStatusCode().isError()) {
                            log.warn("Error resolving client for chatId {}: HTTP {}", chatId, resp.getStatusCode());
                            return Optional.empty();
                        }
                        ClientProfileDto dto = resp.bodyTo(ClientProfileDto.class);
                        return Optional.ofNullable(dto);
                    });
        } catch (Exception e) {
            log.warn("Exception resolving client for chatId {}: {}", chatId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<TaskSummaryDto> getClientTasks(Long clientId, int limit) {
        try {
            List<TaskSummaryDto> tasks = backendRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/internal/clients/{clientId}/tasks")
                            .queryParam("limit", limit)
                            .build(clientId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TaskSummaryDto>>() {});
            return tasks != null ? tasks : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed fetching tasks for clientId {}: {}", clientId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<DocumentSummaryDto> getClientDocuments(Long clientId, int limit) {
        try {
            List<DocumentSummaryDto> docs = backendRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/internal/clients/{clientId}/documents")
                            .queryParam("limit", limit)
                            .build(clientId))
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<DocumentSummaryDto>>() {});
            return docs != null ? docs : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed fetching documents for clientId {}: {}", clientId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public boolean unlinkByChatId(Long chatId) {
        try {
            backendRestClient.delete()
                    .uri("/v1/internal/telegram/chat/{chatId}", chatId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.error("Failed to unlink chatId {}: {}", chatId, e.getMessage());
            return false;
        }
    }

    @Override
    public List<PendingNotificationDto> getPendingNotifications(int limit) {
        try {
            List<PendingNotificationDto> items = backendRestClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/internal/telegram/pending")
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<PendingNotificationDto>>() {});
            return items != null ? items : Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch pending notifications: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public NotificationAckResponse acknowledgeNotifications(NotificationAckRequest request) {
        try {
            return backendRestClient.post()
                    .uri("/v1/internal/telegram/ack")
                    .body(request)
                    .retrieve()
                    .body(NotificationAckResponse.class);
        } catch (Exception e) {
            log.error("Failed to acknowledge notifications: {}", e.getMessage());
            return new NotificationAckResponse(0, 0, 0, 0);
        }
    }
}
