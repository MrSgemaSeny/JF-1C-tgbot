package kz.zhanfinance.bot.client;

import kz.zhanfinance.bot.client.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class JfInternalApiClientTest {

    private static final String BASE_URL = "http://localhost:8080/api";
    private static final String INTERNAL_TOKEN = "test-internal-token-32-chars-ok";

    private MockRestServiceServer mockServer;
    private JfInternalApiClient apiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("X-Internal-Token", INTERNAL_TOKEN);

        mockServer = MockRestServiceServer.bindTo(builder).build();
        RestClient restClient = builder.build();
        apiClient = new JfInternalApiClient(restClient);
    }

    @Test
    @DisplayName("bindTelegram should send POST to /v1/internal/telegram/bind with X-Internal-Token header")
    void bindTelegramSuccess() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/bind"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(
                        "{\"success\":true,\"userId\":42,\"fullName\":\"Азамат Сериков\",\"email\":\"a@test.kz\",\"role\":\"CLIENT\"}",
                        MediaType.APPLICATION_JSON
                ));

        BindResponse response = apiClient.bindTelegram(new BindRequest("token-123", 12345L, "user_tg", "Азамат"));

        assertThat(response).isNotNull();
        assertThat(response.success()).isTrue();
        assertThat(response.userId()).isEqualTo(42L);
        assertThat(response.fullName()).isEqualTo("Азамат Сериков");
        mockServer.verify();
    }

    @Test
    @DisplayName("getClientByChatId should return client profile on 200 OK")
    void getClientByChatIdFound() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/chat/12345/client"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess(
                        "{\"linked\":true,\"userId\":42,\"fullName\":\"Азамат\",\"email\":\"a@test.kz\",\"role\":\"CLIENT\"}",
                        MediaType.APPLICATION_JSON
                ));

        Optional<ClientProfileDto> profile = apiClient.getClientByChatId(12345L);

        assertThat(profile).isPresent();
        assertThat(profile.get().userId()).isEqualTo(42L);
        assertThat(profile.get().fullName()).isEqualTo("Азамат");
        mockServer.verify();
    }

    @Test
    @DisplayName("getClientByChatId should return Optional.empty on 404 Not Found")
    void getClientByChatIdNotFound() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/chat/99999/client"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withResourceNotFound());

        Optional<ClientProfileDto> profile = apiClient.getClientByChatId(99999L);

        assertThat(profile).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("getClientTasks should fetch active tasks list")
    void getClientTasks() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/clients/42/tasks?limit=10"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess(
                        "[{\"id\":101,\"title\":\"Сдача НДС\",\"stageName\":\"В работе\",\"dueDate\":\"2026-09-30\"}]",
                        MediaType.APPLICATION_JSON
                ));

        List<TaskSummaryDto> tasks = apiClient.getClientTasks(42L, 10);

        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).title()).isEqualTo("Сдача НДС");
        mockServer.verify();
    }

    @Test
    @DisplayName("getClientDocuments should fetch client documents list")
    void getClientDocuments() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/clients/42/documents?limit=5"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess(
                        "[{\"id\":201,\"fileName\":\"Договор.pdf\",\"contentType\":\"application/pdf\",\"fileSize\":1024,\"status\":\"SIGNED\"}]",
                        MediaType.APPLICATION_JSON
                ));

        List<DocumentSummaryDto> docs = apiClient.getClientDocuments(42L, 5);

        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).fileName()).isEqualTo("Договор.pdf");
        mockServer.verify();
    }

    @Test
    @DisplayName("unlinkByChatId should send DELETE to /v1/internal/telegram/chat/{chatId}")
    void unlinkByChatId() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/chat/12345"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess());

        boolean result = apiClient.unlinkByChatId(12345L);

        assertThat(result).isTrue();
        mockServer.verify();
    }

    @Test
    @DisplayName("getPendingNotifications should return queue items")
    void getPendingNotifications() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/pending?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess(
                        "[{\"id\":1,\"chatId\":12345,\"message\":\"<b>Уведомление</b>\",\"attempts\":0}]",
                        MediaType.APPLICATION_JSON
                ));

        List<PendingNotificationDto> items = apiClient.getPendingNotifications(50);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).id()).isEqualTo(1L);
        assertThat(items.get(0).chatId()).isEqualTo(12345L);
        mockServer.verify();
    }

    @Test
    @DisplayName("acknowledgeNotifications should submit ACK payload and return count")
    void acknowledgeNotifications() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/ack"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", INTERNAL_TOKEN))
                .andRespond(withSuccess(
                        "{\"acknowledgedCount\":2,\"processedCount\":2,\"successCount\":2,\"failedCount\":0}",
                        MediaType.APPLICATION_JSON
                ));

        NotificationAckResponse response = apiClient.acknowledgeNotifications(
                new NotificationAckRequest(List.of(
                        new NotificationAckItemDto(1L, "SENT", null),
                        new NotificationAckItemDto(2L, "SENT", null)
                ))
        );

        assertThat(response).isNotNull();
        assertThat(response.acknowledgedCount()).isEqualTo(2);
        mockServer.verify();
    }
}
