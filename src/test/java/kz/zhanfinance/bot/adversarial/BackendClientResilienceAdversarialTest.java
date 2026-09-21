package kz.zhanfinance.bot.adversarial;

import kz.zhanfinance.bot.client.BackendClientException;
import kz.zhanfinance.bot.client.JfInternalApiClient;
import kz.zhanfinance.bot.client.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class BackendClientResilienceAdversarialTest {

    private static final String BASE_URL = "http://localhost:8080/api";
    private static final String INTERNAL_TOKEN = "valid-bot-internal-secret";

    private MockRestServiceServer mockServer;
    private JfInternalApiClient apiClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder clientBuilder = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("X-Internal-Token", INTERNAL_TOKEN);

        mockServer = MockRestServiceServer.bindTo(clientBuilder).build();
        RestClient restClient = clientBuilder.build();
        apiClient = new JfInternalApiClient(restClient);
    }

    @Test
    @DisplayName("Adversarial HTTP 500: getPendingNotifications degrades to empty list on server error")
    void testGetPendingNotificationsHttp500() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/pending?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        List<PendingNotificationDto> pending = apiClient.getPendingNotifications(50);
        assertThat(pending).isNotNull().isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 500: acknowledgeNotifications degrades to zero-count response on server error")
    void testAcknowledgeNotificationsHttp500() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/ack"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        NotificationAckResponse ackResp = apiClient.acknowledgeNotifications(
                new NotificationAckRequest(List.of(new NotificationAckItemDto(1L, "SENT", null)))
        );
        assertThat(ackResp).isNotNull();
        assertThat(ackResp.acknowledgedCount()).isZero();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 500: getClientByChatId degrades to Optional.empty on server error")
    void testGetClientByChatIdHttp500() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/chat/123/client"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        Optional<ClientProfileDto> client = apiClient.getClientByChatId(123L);
        assertThat(client).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 500: getClientTasks degrades to empty list on server error")
    void testGetClientTasksHttp500() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/clients/99/tasks?limit=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        List<TaskSummaryDto> tasks = apiClient.getClientTasks(99L, 10);
        assertThat(tasks).isNotNull().isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 500: getClientDocuments degrades to empty list on server error")
    void testGetClientDocumentsHttp500() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/clients/99/documents?limit=10"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withServerError());

        List<DocumentSummaryDto> docs = apiClient.getClientDocuments(99L, 10);
        assertThat(docs).isNotNull().isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 500: unlinkByChatId degrades to false on server error")
    void testUnlinkByChatIdHttp500() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/chat/123"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withServerError());

        boolean unlinked = apiClient.unlinkByChatId(123L);
        assertThat(unlinked).isFalse();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 500: bindTelegram throws BackendClientException with status 500")
    void testBindTelegramHttp500() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/bind"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"message\":\"DB connection down\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> apiClient.bindTelegram(new BindRequest("tok", 123L, "u", "F", "L")))
                .isInstanceOf(BackendClientException.class)
                .hasMessageContaining("Binding failed")
                .satisfies(ex -> assertThat(((BackendClientException) ex).getStatusCode()).isEqualTo(500));

        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 401: getPendingNotifications degrades to empty list on unauthorized token")
    void testGetPendingNotificationsHttp401() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/pending?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Invalid X-Internal-Token"));

        List<PendingNotificationDto> pending = apiClient.getPendingNotifications(50);
        assertThat(pending).isNotNull().isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 403: getClientByChatId degrades to Optional.empty on forbidden status")
    void testGetClientByChatIdHttp403() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/chat/123/client"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("Forbidden"));

        Optional<ClientProfileDto> client = apiClient.getClientByChatId(123L);
        assertThat(client).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial HTTP 401: bindTelegram throws BackendClientException with status 401")
    void testBindTelegramHttp401() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/bind"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("Unauthorized"));

        assertThatThrownBy(() -> apiClient.bindTelegram(new BindRequest("tok", 123L, "u", "F", "L")))
                .isInstanceOf(BackendClientException.class)
                .satisfies(ex -> assertThat(((BackendClientException) ex).getStatusCode()).isEqualTo(401));

        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial Timeout: SocketTimeoutException during getPendingNotifications returns empty list")
    void testGetPendingNotificationsTimeout() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/pending?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out after 5000ms");
                });

        List<PendingNotificationDto> pending = apiClient.getPendingNotifications(50);
        assertThat(pending).isNotNull().isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial Timeout: SocketTimeoutException during acknowledgeNotifications returns zero count")
    void testAcknowledgeNotificationsTimeout() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/ack"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Read timed out after 5000ms");
                });

        NotificationAckResponse ackResp = apiClient.acknowledgeNotifications(
                new NotificationAckRequest(List.of(new NotificationAckItemDto(1L, "SENT", null)))
        );
        assertThat(ackResp).isNotNull();
        assertThat(ackResp.acknowledgedCount()).isZero();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial Timeout: SocketTimeoutException during bindTelegram throws BackendClientException")
    void testBindTelegramTimeout() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/bind"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new SocketTimeoutException("Connect timed out after 3000ms");
                });

        assertThatThrownBy(() -> apiClient.bindTelegram(new BindRequest("tok", 123L, "u", "F", "L")))
                .isInstanceOf(BackendClientException.class)
                .hasMessageContaining("Connect timed out");

        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial Corrupt JSON: Malformed payloads return safe fallback and do not throw unhandled exceptions")
    void testMalformedJsonResponseHandling() {
        mockServer.expect(requestTo(BASE_URL + "/v1/internal/telegram/pending?limit=50"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("INVALID_CORRUPT_JSON_DATA{{{", MediaType.APPLICATION_JSON));

        List<PendingNotificationDto> pending = apiClient.getPendingNotifications(50);
        assertThat(pending).isNotNull().isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Adversarial Real Socket Timeout: SimpleClientHttpRequestFactory enforces configured timeout threshold")
    void testRealSocketReadTimeoutEnforcement() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0)) {
            int port = serverSocket.getLocalPort();

            // Thread that accepts connection but never writes response data
            Thread serverThread = new Thread(() -> {
                try (Socket client = serverSocket.accept()) {
                    Thread.sleep(1000); // stall connection
                } catch (Exception ignored) {
                }
            });
            serverThread.setDaemon(true);
            serverThread.start();

            // RestClient with very short read timeout of 100ms
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(Duration.ofMillis(300));
            factory.setReadTimeout(Duration.ofMillis(100));

            RestClient timedClient = RestClient.builder()
                    .baseUrl("http://127.0.0.1:" + port)
                    .requestFactory(factory)
                    .build();

            JfInternalApiClient timeoutApiClient = new JfInternalApiClient(timedClient);

            long start = System.currentTimeMillis();
            List<PendingNotificationDto> items = timeoutApiClient.getPendingNotifications(10);
            long elapsed = System.currentTimeMillis() - start;

            // Must catch read timeout, return empty list, and complete well under the 1000ms stall time
            assertThat(items).isEmpty();
            assertThat(elapsed)
                    .as("Timeout must trigger near 100ms and well before 800ms")
                    .isLessThan(800L);
        }
    }
}
