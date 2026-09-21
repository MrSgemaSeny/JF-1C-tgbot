# JF-1C Telegram Bot — Полнейший технический аудит и мастер-план реализации

> Дата аудита: актуально для кодовой базы JF-1C (версия миграций V123, Spring Boot 3.4+, Java 17).
> Проверено на соответствие: Second Brain, Security Invariants, Flyway chain, Context-path, Event architecture.

---

## 1. Резюме аудита и вердикт

Исходный план `tg-bot-audit-and-plan.md` содержит правильную базовую концепцию (отдельный микросервис бота, связка через одноразовые токены, Outbox-паттерн, Long Polling), однако при детальном исследовании монолита JF-1C выявлены **7 критических архитектурных и эксплуатационных несоответствий**, которые привели бы к падению сервиса в продакшене:

1. **Несоответствие Context-Path и URL-роутинга**: в `application.properties` бекенда задан `server.servlet.context-path=/api`. Все запросы к `/v1/internal/**` на самом деле физически идут на `/api/v1/internal/**`. В исходном плане пути были хаотично смешаны (`/v1/internal/...` vs `/api/v1/...`).
2. **Отсутствие обработки сбоев и Dead Letter Queue в Outbox**: исходная таблица `telegram_notifications` имела только флаг `sent BOOLEAN`. Если пользователь заблокировал бота (Telegram API 403 Forbidden) или сообщение невалидно, поллер застревает в бесконечном цикле повторных попыток каждые 30 секунд.
3. **Уязвимость MarkdownV2 vs HTML**: в `TelegramNotifierService` используется `MarkdownV2`, требующий экранирования 18 спецсимволов (`_ * [ ] ( ) ~ > # + - = | { } . ! \`). В названиях документов, номерах счетов с дефисами и датах с точками это систематически вызывает 400 Bad Request от Telegram API. Для бота необходим строгий переход на HTML parse mode (экранируются только `&`, `<`, `>`).
4. **Архитектурный спагетти-триггер Outbox**: ручная вставка вызова `TelegramOutboxService.enqueue()` в 25+ методов `TaskService` нарушает SRP и гарантирует пропуски событий. Решение: использование Spring `@TransactionalEventListener(phase = BEFORE_COMMIT)` на базе событий домена или расширение `NotificationService`.
5. **Безопасность роли INTERNAL_BOT**: роль `INTERNAL_BOT` не должна регистрироваться через публичные Auth эндпоинты (`AuthService`, `GoogleAuthService`). Нужна строгая санитизация.
6. **Очистка и коллизии токенов привязки**: повторная генерация токена пользователем до истечения TTL приводила к накоплению мусора или конфликтам. Требуется аннулирование старых активных токенов при выпуске нового.
7. **Fly.io Internal DNS и сеть (6PN)**: коммуникация между ботом и монолитом в продакшене должна идти по внутренней защищенной сети Fly.io `http://zhanfinance.internal:8080/api` с нулевым выходом во внешний интернет для `/api/v1/internal/**`.

---

## 2. Детальный аудит текущего состояния монолита (JF-1C)

### 2.1 Матрица готовности компонентов

| Компонент | Текущее состояние в JF-1C | Необходимое изменение |
|---|---|---|
| `server.servlet.context-path` | `/api` | Все internal эндпоинты бота: `/api/v1/internal/**` |
| `SecurityConfig.java` | `.requestMatchers("/v1/internal/**").denyAll()` | Замена на `hasRole("INTERNAL_BOT")` + `InternalTokenFilter` |
| `Role.java` | `ADMIN, EMPLOYEE, CLIENT, LEARNER, CURATOR, ADVISOR` | Добавление `INTERNAL_BOT` (служебный принципал) |
| `AuthService` / `GoogleAuthService` | Санитизация ролей | Добавить `INTERNAL_BOT` в черный список пользовательских ролей |
| Таблица `telegram_links` | Отсутствует | Миграция `V124__Telegram_Link_Schema.sql` |
| Таблица `telegram_link_tokens` | Отсутствует | Миграция `V124__Telegram_Link_Schema.sql` |
| Таблица `telegram_notifications` | Отсутствует | Миграция `V125__Telegram_Outbox.sql` (с `attempts` и `status`) |
| `TelegramNotifierService` | Шлет напрямую в канал админа через RestClient | Сохраняется для критических админ-алертов, бот работает автономно |
| `NotificationService` | Создает запись в `notifications` | Подключение триггера `TelegramOutboxService` |
| `ApiRateLimitFilter` | Защищает `/api/v1/**` по IP | Исключение `/v1/internal/**` из клиентского Bucket4j лимита |

---

## 3. Архитектура взаимодействия

```
+-----------------------------------------------------------------------------------+
|                                 БРАУЗЕР КЛИЕНТА                                   |
|  1. Переход в Профиль -> Настройки -> Telegram                                    |
|  2. POST /api/v1/telegram/link/generate (Bearer JWT)                              |
|  3. Получение deeplink: https://t.me/zhan_finance_bot?start=TOKEN                 |
+----------------------------------------+------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                             TELEGRAM КЛИЕНТ (Юзер)                                |
|  4. Нажатие START в боте -> отправка команды: /start TOKEN                        |
+----------------------------------------+------------------------------------------+
                                         |
                                         v (Telegram Long Polling)
+-----------------------------------------------------------------------------------+
|                        МИКРОСЕРВИС БОТА (jf-tg-bot)                               |
|  5. Прием сообщения -> извлечение TOKEN                                           |
|  6. POST http://backend:8080/api/v1/internal/telegram/bind                        |
|     Header: X-Internal-Token: [CRITICAL_SECRET_KEY]                               |
|     Body: {"token": "...", "chatId": 123456789, "username": "tg_user"}           |
+----------------------------------------+------------------------------------------+
                                         |
                                         v (Internal HTTP / 6PN Network)
+-----------------------------------------------------------------------------------+
|                           ОСНОВНОЙ БЕКЕНД (JF-1C)                                 |
|  7. InternalTokenFilter: валидация токена (constant-time MessageDigest)           |
|  8. TelegramLinkService:                                                          |
|     - Проверка TTL и валидности токена                                            |
|     - Создание записи в telegram_links (user_id <-> chat_id)                      |
|     - Удаление использованного токена                                             |
|     - HTTP 200 OK                                                                |
+----------------------------------------+------------------------------------------+
                                         |
                                         v
+-----------------------------------------------------------------------------------+
|                        ОТВЕТ БОТА В ТЕЛЕГРАМ ЮЗЕРУ                                |
|  9. "Аккаунт успешно привязан к ЖАН FINANCE. Вам доступны команды: /tasks, /docs" |
+-----------------------------------------------------------------------------------+
```

---

## 4. Паттерн Outbox и консистентность доставки

### 4.1 Жизненный цикл уведомления

```
[Бизнес-транзакция в JF-1C] (например, смена статуса задачи в TaskService)
         |
         +--> 1. INSERT INTO tasks ...
         +--> 2. INSERT INTO notifications (in-app)
         +--> 3. INSERT INTO telegram_notifications (chat_id, message, status='PENDING', attempts=0)
         |
      COMMIT TRANSACTIONS (100% гарантия: если упадет транзакция, outbox не запишется)

                                         ~~~ Асинхронный контур ~~~

[NotificationPoller в jf-tg-bot] (Каждые 15-30 сек)
         |
         +--> 4. GET /api/v1/internal/telegram/pending?limit=50 (X-Internal-Token)
         |
[JF-1C Backend]
         +--> 5. SELECT * FROM telegram_notifications WHERE status = 'PENDING' AND attempts < 3 ORDER BY created_at ASC LIMIT 50
         |
[jf-tg-bot]
         +--> 6. Итерация по списку:
                 - Отправка в Telegram API (задержка 40ms между вызовами для соблюдения лимитов 30 msg/sec)
                 - Сбор успешных ID в list_success, сбойных в list_failed
         |
         +--> 7. POST /api/v1/internal/telegram/ack
                 Body: {
                   "processed": [ {"id": 101, "status": "SENT"}, {"id": 102, "status": "FAILED", "error": "Bot was blocked by user"} ]
                 }
         |
[JF-1C Backend]
         +--> 8. UPDATE telegram_notifications SET status = ..., attempts = attempts + 1 WHERE id = ...
                 - Если ошибка 403 (Blocked by user) -> автоматическая деактивация telegram_links
```

---

## 5. Спецификация БД и Flyway миграций

### 5.1 Миграция V124 — Таблицы привязки аккаунтов

```sql
-- V124__Telegram_Link_Schema.sql
-- Таблица постоянной связи пользователя и Telegram Chat ID

CREATE TABLE telegram_links (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL UNIQUE REFERENCES app_users(id) ON DELETE CASCADE,
    chat_id             BIGINT NOT NULL UNIQUE,
    telegram_username   VARCHAR(64),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    linked_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Таблица одноразовых токенов привязки (TTL 15 минут)
CREATE TABLE telegram_link_tokens (
    token               VARCHAR(64) PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    expires_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_tg_links_chat_id ON telegram_links(chat_id);
CREATE INDEX idx_tg_links_user_id ON telegram_links(user_id);
CREATE INDEX idx_tg_link_tokens_expires ON telegram_link_tokens(expires_at);
CREATE INDEX idx_tg_link_tokens_user_id ON telegram_link_tokens(user_id);
```

### 5.2 Миграция V125 — Outbox-очередь уведомлений

```sql
-- V125__Telegram_Outbox.sql
-- Outbox очередь гарантированной доставки сообщений в Telegram

CREATE TABLE telegram_notifications (
    id                  BIGSERIAL PRIMARY KEY,
    chat_id             BIGINT NOT NULL,
    user_id             BIGINT REFERENCES app_users(id) ON DELETE SET NULL,
    message             TEXT NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER NOT NULL DEFAULT 0,
    max_attempts        INTEGER NOT NULL DEFAULT 3,
    last_error          TEXT,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    processed_at        TIMESTAMP WITH TIME ZONE
);

-- Частичный индекс для сверхбыстрой выборки непосланных уведомлений
CREATE INDEX idx_tg_notif_pending ON telegram_notifications(created_at)
    WHERE status = 'PENDING';

-- Индекс для ротации и очистки архивных записей
CREATE INDEX idx_tg_notif_cleanup ON telegram_notifications(status, created_at);
```

---

## 6. Изменения в основном монолите (JF-1C Backend)

### 6.1 Роль и безопасность

#### 1. Обновление enum Role.java
```java
package com.example.zhanfinancebackend.modules.auth.entity;

public enum Role {
    ADMIN,
    EMPLOYEE,
    CLIENT,
    LEARNER,
    CURATOR,
    ADVISOR,
    INTERNAL_BOT // Служебная роль для внутреннего API
}
```

#### 2. Фильтр InternalTokenFilter.java
```java
package com.example.zhanfinancebackend.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;

@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String INTERNAL_HEADER = "X-Internal-Token";
    private static final String INTERNAL_PATH_PREFIX = "/v1/internal/";

    @Value("${app.security.internal-bot-token:}")
    private String configuredToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getServletPath();

        // Фильтр активен только для путей /v1/internal/**
        if (path.startsWith(INTERNAL_PATH_PREFIX)) {
            String requestToken = request.getHeader(INTERNAL_HEADER);

            if (configuredToken == null || configuredToken.isBlank() || configuredToken.length() < 32) {
                log.error("CRITICAL: INTERNAL_BOT_TOKEN is not configured or too short");
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Internal API is misconfigured");
                return;
            }

            if (requestToken == null || !isEqualConstantTime(requestToken, configuredToken)) {
                log.warn("Unauthorized access attempt to internal endpoint: {} from IP: {}", path, request.getRemoteAddr());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid internal token");
                return;
            }

            // Успешная аутентификация внутреннего бота
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    "INTERNAL_BOT_SYSTEM",
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_INTERNAL_BOT"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }

    private boolean isEqualConstantTime(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
```

#### 3. Настройка SecurityConfig.java
- Зарегистрировать `InternalTokenFilter` **перед** `JwtAuthenticationFilter`.
- Заменить `.requestMatchers("/v1/internal/**").denyAll()` на `.requestMatchers("/v1/internal/**").hasRole("INTERNAL_BOT")`.

### 6.2 Доменные сервисы и сущности

#### 1. Сущности `TelegramLink` и `TelegramLinkToken`
- Создать в пакете `com.example.zhanfinancebackend.modules.telegram.entity`.
- Создать соответствующие репозитории `TelegramLinkRepository` и `TelegramLinkTokenRepository`.

#### 2. Сервис `TelegramLinkService`
Методы:
- `generateLinkToken(Long userId)`: удаляет предыдущие токены юзера, генерирует криптостойкий UUID токен, сохраняет в `telegram_link_tokens` со сроком 15 мин, возвращает `{token, deeplink: "https://t.me/BOT_USERNAME?start=" + token, expiresAt}`.
- `bindTelegramAccount(String token, Long chatId, String username)`: проверяет токен, ищет пользователя, создает/обновляет `TelegramLink`, удаляет токен, возвращает успешный результат.
- `unlinkTelegramAccount(Long userId)`: удаляет привязку.
- `getLinkStatus(Long userId)`: возвращает статус привязки `{linked: boolean, username: string, linkedAt: instant}`.
- `resolveUserByChatId(Long chatId)`: возвращает клиента для Telegram бота.

#### 3. Сервис `TelegramOutboxService`
Методы:
- `enqueue(Long userId, String message)`: находит активный `chat_id` пользователя по `user_id`. Если привязка есть, сохраняет запись в `telegram_notifications` со статусом `PENDING`. Выполняется в той же транзакции.
- `fetchPendingNotifications(int limit)`: выборка пачки со статусом `PENDING` и `attempts < 3`.
- `acknowledgeNotifications(List<NotificationAckDto> acks)`: обновление статусов на `SENT` или `FAILED` с записью ошибки.

#### 4. Планировщик `TelegramCleanupScheduler`
```java
@Scheduled(cron = "0 0 * * * *") // Каждый час
public void cleanupExpiredTokens() {
    linkTokenRepository.deleteByExpiresAtBefore(Instant.now());
}

@Scheduled(cron = "0 0 3 * * *") // Каждую ночь в 3:00
public void cleanupArchivedNotifications() {
    outboxRepository.deleteSentOlderThan(Instant.now().minus(7, ChronoUnit.DAYS));
}
```

---

## 7. Спецификация REST API

### 7.1 Пользовательские эндпоинты (Frontend -> Backend)
Префикс: `/api/v1/telegram/link` (Доступ: Аутентифицированные пользователи с ролью `CLIENT`, `EMPLOYEE`, `ADMIN`).

| Метод | URL | Описание | Ответ |
|---|---|---|---|
| `POST` | `/api/v1/telegram/link/generate` | Генерация токена привязки | `{ "token": "...", "deeplink": "https://t.me/...", "expiresAt": "2026-09-21T10:30:00Z" }` |
| `GET` | `/api/v1/telegram/link/status` | Проверка статуса привязки | `{ "linked": true, "telegramUsername": "johndoe", "linkedAt": "2026-09-21T09:00:00Z" }` |
| `DELETE`| `/api/v1/telegram/link` | Отвязка Telegram | `{ "success": true }` |

### 7.2 Внутренние эндпоинты (Bot -> Backend)
Префикс: `/api/v1/internal/telegram` (Доступ: строго заголовок `X-Internal-Token`).

| Метод | URL | Описание | Ответ |
|---|---|---|---|
| `POST` | `/api/v1/internal/telegram/bind` | Привязка аккаунта по токену | `{ "success": true, "userId": 42, "fullName": "Иван Иванов" }` |
| `GET` | `/api/v1/internal/telegram/chat/{chatId}/client` | Резолвинг профиля клиента | `{ "userId": 42, "fullName": "...", "companyName": "...", "role": "CLIENT" }` |
| `GET` | `/api/v1/internal/clients/{clientId}/tasks` | Активные задачи клиента | `[ { "id": 1, "title": "Сдача НДС", "stage": "В работе", "deadline": "2026-09-25" } ]` |
| `GET` | `/api/v1/internal/clients/{clientId}/documents` | Последние 5 документов | `[ { "id": 10, "title": "Акт сверки", "status": "SIGNED", "createdAt": "..." } ]` |
| `GET` | `/api/v1/internal/telegram/pending?limit=50` | Выборка очереди outbox | `[ { "id": 100, "chatId": 123456, "message": "HTML текст" } ]` |
| `POST` | `/api/v1/internal/telegram/ack` | Подтверждение доставки | `{ "acknowledgedCount": 50 }` |

---

## 8. Архитектура микросервиса бота (`zhan-finance-tgbot`)

### 8.1 Стек и зависимости
- **Java 17** + **Spring Boot 3.3.4**
- **Telegrambots Spring Boot Starter / Long Polling** (версия 7.2.1)
- **Spring Web (RestClient)** — легковесный блокирующий HTTP-клиент с пулом соединений
- Режим без сервлета: `spring.main.web-application-type=none` (потребление RAM ~45-60MB)

### 8.2 Структура пакетов репозитория

```
zhan-finance-tgbot/
├── src/main/java/kz/zhanfinance/bot/
│   ├── ZhanFinanceBotApplication.java
│   ├── config/
│   │   ├── BotConfig.java                  # Бин TelegramClient и Long Polling
│   │   └── RestClientConfig.java           # RestClient с таймаутами (3s connect, 5s read) и X-Internal-Token
│   ├── bot/
│   │   ├── ZhanFinanceTelegramBot.java     # Прием обновлений Telegram
│   │   └── CommandDispatcher.java          # Диспетчеризация команд и кнопок
│   ├── handler/
│   │   ├── StartCommandHandler.java        # /start [token] флоу привязки
│   │   ├── TasksCommandHandler.java        # /tasks список задач
│   │   ├── DocsCommandHandler.java         # /docs список документов
│   │   ├── StatusCommandHandler.java       # /status сводный статус
│   │   ├── UnlinkCommandHandler.java       # /unlink отвязка аккаунта
│   │   └── HelpCommandHandler.java         # /help справка
│   ├── client/
│   │   ├── JfInternalApiClient.java        # HTTP-клиент к бекенду JF-1C
│   │   └── dto/                            # DTO запросов и ответов
│   ├── service/
│   │   ├── UserSessionCache.java           # In-memory кэш chatId -> ClientDto (Caffeine, TTL 5 min)
│   │   └── HtmlMessageFormatter.java       # Безопасное экранирование и HTML-форматирование
│   └── scheduler/
│       └── OutboxNotificationPoller.java   # @Scheduled(fixedDelay = 20000) опрос очереди и отправка
├── src/main/resources/
│   └── application.yml
├── Dockerfile                              # Мультистейдж сборка (Eclipse Temurin 17 JRE Alpine)
├── build.gradle
└── .gitignore
```

### 8.3 Безопасное HTML форматирование сообщений

Вместо нестабильного `MarkdownV2`, бот использует режим `ParseMode.HTML`.
Утилита форматирования:
```java
public class HtmlMessageFormatter {
    public static String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    public static String formatTaskNotification(String title, String status, String deadline) {
        return "<b>Обновление по задаче</b>\n\n"
             + "<b>Задача:</b> " + escape(title) + "\n"
             + "<b>Статус:</b> <code>" + escape(status) + "</code>\n"
             + (deadline != null ? "<b>Дедлайн:</b> " + escape(deadline) + "\n\n" : "\n")
             + "<a href=\"https://zhanfinance.kz/client/tasks\">Открыть в кабинете</a>";
    }
}
```

---

## 9. Развертывание и эксплуатация на Fly.io

### 9.1 Сетевая топология (Fly.io 6PN Private Network)
1. **JF-1C Backend**: приложение `zhanfinance` на Fly.io.
2. **TG Bot**: приложение `zhanfinance-tgbot` в том же регионе и организации.
3. **URL вызова внутри облака**: `http://zhanfinance.internal:8080/api`.
4. Трафик не выходит в публичный интернет, имеет нулевую задержку (<1ms) и не расходует внешний трафик.

### 9.2 Лимиты ресурсов JVM
Для контейнера бота с лимитом 256MB RAM:
```bash
JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC -Xss256k -Dfile.encoding=UTF-8"
```

---

## 10. Пошаговый Roadmap реализации

### Этап 1: Доработка монолита JF-1C (Backend)
- [ ] 1.1. Добавить `INTERNAL_BOT` в `Role.java` и исключить из пользовательской регистрации.
- [ ] 1.2. Создать миграцию `V124__Telegram_Link_Schema.sql`.
- [ ] 1.3. Создать миграцию `V125__Telegram_Outbox.sql`.
- [ ] 1.4. Реализовать `InternalTokenFilter` и зарегистрировать в `SecurityConfig`.
- [ ] 1.5. Реализовать сущности и репозитории `TelegramLink`, `TelegramLinkToken`, `TelegramNotification`.
- [ ] 1.6. Реализовать `TelegramLinkService` и `TelegramOutboxService`.
- [ ] 1.7. Создать клиентский контроллер `TelegramLinkController` (`/api/v1/telegram/link/**`).
- [ ] 1.8. Создать внутренний контроллер `InternalTelegramController` (`/api/v1/internal/**`).
- [ ] 1.9. Интегрировать триггер `TelegramOutboxService` в события задач и документов.
- [ ] 1.10. Написать модульные и интеграционные тесты для API и фильтра.

### Этап 2: Создание и разработка микросервиса бота (`zhan-finance-tgbot`)
- [ ] 2.1. Инициализировать проект Gradle (`Java 17`, `Spring Boot 3.3.4`).
- [ ] 2.2. Настроить `RestClient` с передачей `X-Internal-Token` и таймаутами.
- [ ] 2.3. Реализовать Long Polling клиент Telegram.
- [ ] 2.4. Реализовать обработчик `/start TOKEN` с вызовом `/api/v1/internal/telegram/bind`.
- [ ] 2.5. Реализовать обработчики команд `/tasks`, `/docs`, `/status`, `/unlink`, `/help`.
- [ ] 2.6. Реализовать `OutboxNotificationPoller` с защитой от 429 Rate Limit и подтверждением доставки.
- [ ] 2.7. Написать тесты для команд и парсера.

### Этап 3: Интеграция с фронтендом (JF-1C Frontend)
- [ ] 3.1. Создать компонент привязки Telegram в профиле клиента (`TelegramIntegrationCard`).
- [ ] 3.2. Добавить запрос статуса привязки через React Query.
- [ ] 3.3. Добавить кнопку «Привязать Telegram» с открытием deeplink в новом окне или QR-кодом.
- [ ] 3.4. Добавить кнопку «Отвязать Telegram» с диалогом подтверждения.

### Этап 4: Тестирование, CI/CD и релиз
- [ ] 4.1. Провести сквозной E2E тест: Генерация токена -> Переход в бот -> /start -> Привязка -> Смена стадии задачи в CRM -> Получение push в Telegram.
- [ ] 4.2. Настроить Dockerfile и GitHub Actions workflow для деплоя бота на Fly.io.
- [ ] 4.3. Выпустить релиз в production.
