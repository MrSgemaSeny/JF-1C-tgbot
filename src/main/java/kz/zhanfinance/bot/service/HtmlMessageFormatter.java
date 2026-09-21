package kz.zhanfinance.bot.service;

import kz.zhanfinance.bot.client.dto.ClientProfileDto;
import kz.zhanfinance.bot.client.dto.DocumentSummaryDto;
import kz.zhanfinance.bot.client.dto.TaskSummaryDto;

import java.util.List;

public final class HtmlMessageFormatter {

    private HtmlMessageFormatter() {}

    public static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    public static String bold(String text) {
        return "<b>" + escape(text) + "</b>";
    }

    public static String italic(String text) {
        return "<i>" + escape(text) + "</i>";
    }

    public static String code(String text) {
        return "<code>" + escape(text) + "</code>";
    }

    public static String link(String text, String url) {
        return "<a href=\"" + escape(url) + "\">" + escape(text) + "</a>";
    }

    public static String formatTaskNotification(String title, String status, String deadline) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Обновление по задаче</b>\n\n");
        sb.append("<b>Задача:</b> ").append(escape(title)).append("\n");
        sb.append("<b>Статус:</b> <code>").append(escape(status)).append("</code>\n");
        if (deadline != null && !deadline.isBlank()) {
            sb.append("<b>Дедлайн:</b> ").append(escape(deadline)).append("\n");
        }
        sb.append("\n<a href=\"https://zhanfinance.kz/client/tasks\">Открыть в личном кабинете</a>");
        return sb.toString();
    }

    public static String formatDocumentNotification(String title, String status) {
        return "<b>Новый документ</b>\n\n"
                + "<b>Документ:</b> " + escape(title) + "\n"
                + "<b>Статус:</b> <code>" + escape(status) + "</code>\n\n"
                + "<a href=\"https://zhanfinance.kz/client/documents\">Перейти к документам</a>";
    }

    public static String formatTasksList(List<TaskSummaryDto> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "<b>У вас нет активных задач.</b>";
        }
        StringBuilder sb = new StringBuilder("<b>Ваши активные задачи:</b>\n\n");
        for (TaskSummaryDto task : tasks) {
            sb.append("• <b>").append(escape(task.title())).append("</b>\n");
            sb.append("  Статус: <code>").append(escape(task.stageName() != null ? task.stageName() : "Не указан")).append("</code>\n");
            if (task.dueDate() != null && !task.dueDate().isBlank()) {
                sb.append("  Дедлайн: ").append(escape(task.dueDate())).append("\n");
            }
            if (task.assignedEmployeeName() != null && !task.assignedEmployeeName().isBlank()) {
                sb.append("  Ответственный: ").append(escape(task.assignedEmployeeName())).append("\n");
            }
            sb.append("\n");
        }
        sb.append("<a href=\"https://zhanfinance.kz/client/tasks\">Открыть все задачи</a>");
        return sb.toString().trim();
    }

    public static String formatDocumentsList(List<DocumentSummaryDto> documents) {
        if (documents == null || documents.isEmpty()) {
            return "<b>У вас пока нет документов.</b>";
        }
        StringBuilder sb = new StringBuilder("<b>Последние документы:</b>\n\n");
        for (DocumentSummaryDto doc : documents) {
            sb.append("• <b>").append(escape(doc.fileName())).append("</b>\n");
            sb.append("  Статус: <code>").append(escape(doc.status() != null ? doc.status() : "Не указан")).append("</code>\n");
            sb.append("\n");
        }
        sb.append("<a href=\"https://zhanfinance.kz/client/documents\">Перейти к документам</a>");
        return sb.toString().trim();
    }

    public static String formatStatus(ClientProfileDto client, int taskCount, int docCount) {
        StringBuilder sb = new StringBuilder("<b>Сводка аккаунта ЖАН FINANCE</b>\n\n");
        sb.append("<b>Клиент:</b> ").append(escape(client.fullName())).append("\n");
        if (client.companyName() != null && !client.companyName().isBlank()) {
            sb.append("<b>Организация:</b> ").append(escape(client.companyName())).append("\n");
        }
        if (client.email() != null && !client.email().isBlank()) {
            sb.append("<b>Email:</b> ").append(escape(client.email())).append("\n");
        }
        if (client.phone() != null && !client.phone().isBlank()) {
            sb.append("<b>Телефон:</b> ").append(escape(client.phone())).append("\n");
        }
        if (client.role() != null && !client.role().isBlank()) {
            sb.append("<b>Роль:</b> ").append(escape(client.role())).append("\n");
        }
        sb.append("<b>Активных задач:</b> ").append(taskCount).append("\n");
        sb.append("<b>Документов:</b> ").append(docCount).append("\n\n");
        sb.append("Для работы используйте команды:\n");
        sb.append("/tasks — Список активных задач\n");
        sb.append("/docs — Последние документы\n");
        sb.append("/help — Справка");
        return sb.toString();
    }

    public static String formatWelcomeLinked(String fullName) {
        return "Вы уже авторизованы как <b>" + escape(fullName) + "</b>.\n\n"
                + "Вам доступны команды:\n"
                + "/tasks — Активные задачи\n"
                + "/docs — Последние документы\n"
                + "/status — Сводка аккаунта\n"
                + "/unlink — Отвязать Telegram\n"
                + "/help — Справка";
    }

    public static String formatWelcomeUnlinked() {
        return "<b>Добро пожаловать в ЖАН FINANCE Bot!</b>\n\n"
                + "Для привязки аккаунта перейдите в личный кабинет на сайте:\n"
                + "Профиль -> Настройки -> Telegram и нажмите «Подключить Telegram».\n\n"
                + "После перехода по ссылке ваш аккаунт будет автоматически привязан к боту.";
    }

    public static String formatLinkSuccess(String fullName) {
        return "<b>Аккаунт успешно привязан к ЖАН FINANCE.</b>\n\n"
                + "Добро пожаловать, " + escape(fullName) + "!\n\n"
                + "Вам доступны команды:\n"
                + "/tasks — Активные задачи\n"
                + "/docs — Последние документы\n"
                + "/status — Сводная информация\n"
                + "/unlink — Отвязать аккаунт\n"
                + "/help — Справка";
    }

    public static String formatLinkError(String details) {
        String msg = "<b>Не удалось привязать аккаунт.</b>\n\n"
                + "Ссылка недействительна или истек срок ее действия (15 минут). "
                + "Сгенерируйте новую ссылку в личном кабинете.";
        if (details != null && !details.isBlank()) {
            msg += "\n\n<i>Детали: " + escape(details) + "</i>";
        }
        return msg;
    }

    public static String formatUnlinkSuccess() {
        return "<b>Telegram успешно отвязан от ЖАН FINANCE.</b>\nУведомления отключены.";
    }

    public static String formatNotLinkedPrompt() {
        return "<b>Аккаунт не привязан.</b>\n\n"
                + "Для привязки перейдите в личный кабинет на сайте (Профиль -> Настройки -> Telegram) и сгенерируйте ссылку.";
    }

    public static String formatHelp() {
        return "<b>Команды бота ЖАН FINANCE:</b>\n\n"
                + "/tasks — Активные задачи\n"
                + "/docs — Последние документы\n"
                + "/status — Сводная информация по аккаунту\n"
                + "/unlink — Отвязать Telegram от учетной записи\n"
                + "/help — Справка и поддержка\n\n"
                + "По вопросам поддержки: <a href=\"https://wa.me/77750584021\">WhatsApp поддержка (+7 775 058 40 21)</a>";
    }
}
