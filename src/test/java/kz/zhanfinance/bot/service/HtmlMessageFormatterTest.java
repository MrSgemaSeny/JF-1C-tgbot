package kz.zhanfinance.bot.service;

import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.client.dto.DocumentSummaryDto;
import kz.zhanfinance.bot.client.dto.TaskSummaryDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlMessageFormatterTest {

    @Test
    @DisplayName("escape() should sanitize &, <, and > and handle null/empty strings safely")
    void escapeSpecialHtmlCharacters() {
        assertThat(HtmlMessageFormatter.escape(null)).isEqualTo("");
        assertThat(HtmlMessageFormatter.escape("")).isEqualTo("");
        assertThat(HtmlMessageFormatter.escape("Simple text")).isEqualTo("Simple text");
        assertThat(HtmlMessageFormatter.escape("A & B < C > D")).isEqualTo("A &amp; B &lt; C &gt; D");
        assertThat(HtmlMessageFormatter.escape("<<<&&&>>>")).isEqualTo("&lt;&lt;&lt;&amp;&amp;&amp;&gt;&gt;&gt;");
    }

    @Test
    @DisplayName("bold, italic, code, link should produce safe HTML tags")
    void formattingTags() {
        assertThat(HtmlMessageFormatter.bold("title & info")).isEqualTo("<b>title &amp; info</b>");
        assertThat(HtmlMessageFormatter.italic("sub < text >")).isEqualTo("<i>sub &lt; text &gt;</i>");
        assertThat(HtmlMessageFormatter.code("UUID-123 & 456")).isEqualTo("<code>UUID-123 &amp; 456</code>");
        assertThat(HtmlMessageFormatter.link("Portal & CRM", "https://zhanfinance.kz/?a=1&b=2"))
                .isEqualTo("<a href=\"https://zhanfinance.kz/?a=1&amp;b=2\">Portal &amp; CRM</a>");
    }

    @Test
    @DisplayName("formatTaskNotification should include title, status, and optional deadline")
    void formatTaskNotification() {
        String withDeadline = HtmlMessageFormatter.formatTaskNotification("Сдача НДС <300.00>", "В работе", "2026-09-30");
        assertThat(withDeadline)
                .contains("<b>Обновление по задаче</b>")
                .contains("Сдача НДС &lt;300.00&gt;")
                .contains("<code>В работе</code>")
                .contains("2026-09-30");

        String withoutDeadline = HtmlMessageFormatter.formatTaskNotification("Ревизия кассы", "Новая", null);
        assertThat(withoutDeadline)
                .contains("<b>Обновление по задаче</b>")
                .contains("Ревизия кассы")
                .contains("<code>Новая</code>")
                .doesNotContain("Дедлайн:");
    }

    @Test
    @DisplayName("formatDocumentNotification should format document title and status")
    void formatDocumentNotification() {
        String formatted = HtmlMessageFormatter.formatDocumentNotification("Акт сверки & отчет.pdf", "SIGNED");
        assertThat(formatted)
                .contains("<b>Новый документ</b>")
                .contains("Акт сверки &amp; отчет.pdf")
                .contains("<code>SIGNED</code>")
                .contains("https://zhanfinance.kz/client/documents");
    }

    @Test
    @DisplayName("formatTasksList should handle empty and populated lists")
    void formatTasksList() {
        assertThat(HtmlMessageFormatter.formatTasksList(Collections.emptyList()))
                .contains("У вас нет активных задач.");

        List<TaskSummaryDto> tasks = List.of(
                new TaskSummaryDto(1L, "Задача 1 & Отчет", "В работе", "IN_PROGRESS", "2026-09-25", "Иван Иванов"),
                new TaskSummaryDto(2L, "Задача 2", null, null, null, null)
        );
        String formatted = HtmlMessageFormatter.formatTasksList(tasks);
        assertThat(formatted)
                .contains("<b>Ваши активные задачи:</b>")
                .contains("Задача 1 &amp; Отчет")
                .contains("В работе")
                .contains("2026-09-25")
                .contains("Иван Иванов")
                .contains("Задача 2")
                .contains("Не указан");
    }

    @Test
    @DisplayName("formatDocumentsList should handle empty and populated lists")
    void formatDocumentsList() {
        assertThat(HtmlMessageFormatter.formatDocumentsList(Collections.emptyList()))
                .contains("У вас пока нет документов.");

        List<DocumentSummaryDto> docs = List.of(
                new DocumentSummaryDto(10L, "Договор <KZ>.pdf", "application/pdf", 1024L, "UPLOADED", Instant.now())
        );
        String formatted = HtmlMessageFormatter.formatDocumentsList(docs);
        assertThat(formatted)
                .contains("<b>Последние документы:</b>")
                .contains("Договор &lt;KZ&gt;.pdf")
                .contains("<code>UPLOADED</code>");
    }

    @Test
    @DisplayName("formatStatus should render client overview card with task and doc counts")
    void formatStatus() {
        ClientProfileDto profile = new ClientProfileDto(
                true, 42L, "Темирлан Сериков", "temirlan@example.kz", "+77015554433", "ТОО Спектр & Ко", "CLIENT"
        );
        String formatted = HtmlMessageFormatter.formatStatus(profile, 3, 7);
        assertThat(formatted)
                .contains("<b>Сводка аккаунта ЖАН FINANCE</b>")
                .contains("Темирлан Сериков")
                .contains("ТОО Спектр &amp; Ко")
                .contains("temirlan@example.kz")
                .contains("+77015554433")
                .contains("CLIENT")
                .contains("Активных задач:</b> 3")
                .contains("Документов:</b> 7");
    }

    @Test
    @DisplayName("verification that no emojis exist in any template output")
    void verifyStrictNoEmojisInTemplates() {
        List<String> outputs = List.of(
                HtmlMessageFormatter.formatWelcomeLinked("Тест"),
                HtmlMessageFormatter.formatWelcomeUnlinked(),
                HtmlMessageFormatter.formatLinkSuccess("Тест"),
                HtmlMessageFormatter.formatLinkError("Ошибка"),
                HtmlMessageFormatter.formatUnlinkSuccess(),
                HtmlMessageFormatter.formatNotLinkedPrompt(),
                HtmlMessageFormatter.formatHelp(),
                HtmlMessageFormatter.formatTaskNotification("Таск", "Статус", "2026-09-30"),
                HtmlMessageFormatter.formatDocumentNotification("Док", "SIGNED")
        );

        // Standard regex matching emoji Unicode blocks
        String emojiRegex = "[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u26FF\u2700-\u27BF]";
        for (String output : outputs) {
            assertThat(output).doesNotMatch(".*" + emojiRegex + ".*");
        }
    }
}
