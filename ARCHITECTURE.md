# Архитектурная карта zhan-finance-tgbot (ZhanFinance Telegram Bot)

Краткий справочник архитектуры, структуры микросервиса и соглашений для разработчиков и AI-ассистентов.

---

## 1. Tech Stack & Overview

Микросервис Telegram-бота для платформы бухгалтерского аутсорсинга «ЖАН FINANCE». Выполняет две ключевые функции:
1. **Клиентский бот (Inbound / User Commands)**: прием команд от клиентов (/start, /tasks, /docs, /status, /unlink, /help), авторизация через одноразовый связующий токен и выдача оперативной информации по задачам и документам.
2. **Транспорт уведомлений (Outbox Poller / Outbound Notifications)**: периодический опрос очереди исходящих сообщений в основном бэкенде (`JF-1C`), отправка сообщений клиентам в Telegram и отправка отчетов о доставке (ACK/FAIL).

### Стек технологий
- **Язык & Среда**: Java 17, Spring Boot 3.3.4, Gradle 8.x
- **Telegram API**: `org.telegram:telegrambots-springboot-longpolling-starter:7.2.1`, `telegrambots-client:7.2.1` (Long Polling, OkHttp client)
- **Сетевой клиент**: Spring Framework 6 `RestClient` с таймаутами подключения и чтения (`SimpleClientHttpRequestFactory`)
- **Кэширование**: Caffeine Cache (`com.github.ben-manes.caffeine:caffeine:3.1.8`) — in-memory сессионный кэш профилей клиентов
- **Безопасность связи с монолитом**: Заголовок `X-Internal-Token` со статическим секретом, связывающим микросервис с бэкендом JF-1C (`Role.INTERNAL_BOT`)
- **Форматирование сообщений**: HTML parse mode с обязательным экранированием спецсимволов (`&`, `<`, `>`)
- **Режим запуска**: Headless (`WebApplicationType.NONE`), опциональный HTTP-порт 8081

---

## 2. Project Structure (Directory Map)

```text
zhan-finance-tgbot/
├── .env                                  # Локальные переменные окружения (секреты, токены)
├── .env.example                          # Шаблон переменных окружения
├── Dockerfile                            # Многоэтапный Docker-образ для деплоя
├── build.gradle                          # Конфигурация зависимостей и сборки
├── ARCHITECTURE.md                       # Данная архитектурная карта
├── README.md                             # Инструкция по установке, запуску и командам
│
└── src/
    ├── main/
    │   ├── java/kz/zhanfinance/bot/
    │   │   ├── ZhanFinanceTgBotApplication.java     # Точка входа Spring Boot (web=NONE)
    │   │   │
    │   │   ├── bot/                                # Прием апдейтов и маршрутизация
    │   │   │   ├── ZhanFinanceLongPollingBot.java  # Реализация SpringLongPollingBot
    │   │   │   └── CommandDispatcher.java          # Парсер текстовых команд и диспетчеризация
    │   │   │
    │   │   ├── client/                             # Клиент к внутреннему API JF-1C
    │   │   │   ├── BackendClient.java              # Интерфейс контракта взаимодействия с монолитом
    │   │   │   ├── JfInternalApiClient.java        # Реализация на базе Spring RestClient
    │   │   │   ├── BackendClientException.java     # Кастомное исключение сетевого слоя
    │   │   │   └── dto/                            # DTO запросов и ответов внутреннего API
    │   │   │       ├── BindRequest.java            # Запрос связывания чата с аккаунтом
    │   │   │       ├── BindResponse.java           # Ответ связывания (успех, clientId, имя)
    │   │   │       ├── ClientProfileDto.java       # Профиль клиента в системе JF-1C
    │   │   │       ├── TaskSummaryDto.java         # Краткие данные по задаче
    │   │   │       ├── DocumentSummaryDto.java     # Краткие данные по документу
    │   │   │       ├── PendingNotificationDto.java # Запись очереди outbox из монолита
    │   │   │       ├── NotificationAckItemDto.java # Статус отправки конкретного сообщения
    │   │   │       ├── NotificationAckRequest.java # Батч-подтверждение доставки в монолит
    │   │   │       └── NotificationAckResponse.java# Итог обработки подтверждений
    │   │   │
    │   │   ├── config/                             # Spring Configuration бины
    │   │   │   ├── BackendClientConfig.java        # Бин RestClient с заголовком X-Internal-Token
    │   │   │   ├── TelegramBotConfig.java          # Бин TelegramClient (OkHttpTelegramClient)
    │   │   │   └── TelegramBotDisabledWarningRunner.java # Предупреждение при отсутствии токена бота
    │   │   │
    │   │   ├── handler/                            # Обработчики команд Telegram
    │   │   │   ├── CommandHandler.java             # Интерфейс обработчика команды
    │   │   │   ├── StartCommandHandler.java        # /start [token] (привязка аккаунта)
    │   │   │   ├── TasksCommandHandler.java        # /tasks (список активных задач клиента)
    │   │   │   ├── DocsCommandHandler.java         # /docs (список недавних документов)
    │   │   │   ├── StatusCommandHandler.java       # /status (статус привязки и профиль)
    │   │   │   ├── UnlinkCommandHandler.java       # /unlink (отвязка Telegram от аккаунта)
    │   │   │   └── HelpCommandHandler.java         # /help (список доступных команд)
    │   │   │
    │   │   ├── scheduler/                          # Фоновые шедулеры
    │   │   │   └── OutboxNotificationPoller.java   # Опрос /internal/telegram/pending раз в 15с
    │   │   │
    │   │   └── service/                            # Вспомогательные сервисы
    │   │       ├── HtmlMessageFormatter.java       # Санитизация HTML и генерация шаблонов ответов
    │   │       ├── TelegramMessageSender.java      # Отправка сообщений через TelegramClient
    │   │       └── UserSessionCache.java           # Кэш привязки chatId -> ClientProfileDto (Caffeine)
    │   │
    │   └── resources/
    │       └── application.yml                     # Настройки таймаутов, интервалов, URL бэкенда
    │
    └── test/                                       # Юнит- и стресс-тесты (16 тестовых классов)
```

---

## 3. Key Components & Entry Points

### 1. Жизненный цикл и старт
- **Точка входа**: [`ZhanFinanceTgBotApplication.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/ZhanFinanceTgBotApplication.java)
  - Сконфигурирован с `WebApplicationType.NONE`. Не занимает локальный HTTP порт, если не задействован встроенный веб-сервер.
  - Включает `@EnableScheduling` для фонового шедулера `OutboxNotificationPoller`.
- **Защита от сбоя при отсутствии токена**: [`TelegramBotDisabledWarningRunner.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/config/TelegramBotDisabledWarningRunner.java)
  - Если `TELEGRAM_BOT_TOKEN` пуст или равен тестовой заглушке (`000000...`), сервис не падает при старте с ошибкой `TelegramApiException 404`, а выводит предупреждение и безопасно отключает Long Polling.

### 2. Прием сообщений (Inbound)
- [`ZhanFinanceLongPollingBot.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/bot/ZhanFinanceLongPollingBot.java):
  - Реализует `SpringLongPollingBot` и `LongPollingSingleThreadUpdateConsumer`.
  - Получает `Update` от Telegram серверов и передает в `CommandDispatcher`.
- [`CommandDispatcher.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/bot/CommandDispatcher.java):
  - Парсит команду, отсекает суффикс бота (например `/tasks@zhan_finance_bot` -> `/tasks`).
  - Находит соответствующий `CommandHandler` по `canHandle(command)`.
  - Перехватывает любые непредвиденные исключения и отправляет пользователю вежливый ответ об ошибке без технических деталей.

### 3. Обработчики команд (`kz.zhanfinance.bot.handler`)
- **`/start [token]`**: Если передан токен (из личного кабинета веб-приложения), выполняет вызов `bindTelegram` в бэкенд JF-1C. При успехе инвалидирует кэш и привязывает аккаунт. Без токена выводит приветствие и инструкцию по привязке.
- **`/tasks`**: Проверяет привязку через `UserSessionCache`. Если привязан, запрашивает последние задачи через `getClientTasks` и выводит их со статусами и ссылками на веб-кабинет.
- **`/docs`**: Запрашивает последние документы через `getClientDocuments` и выводит список со статусами.
- **`/status`**: Проверяет статус привязки аккаунта, название компании и ФИО клиента.
- **`/unlink`**: Отвязывает текущий Telegram-чат от учетной записи через `unlinkByChatId` и очищает кэш.
- **`/help`**: Выводит перечень доступных команд бота.

### 4. Исходящие уведомления (Outbound Outbox Poller)
- [`OutboxNotificationPoller.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/scheduler/OutboxNotificationPoller.java):
  - Каждые 15 секунд (`jf.outbox.poll-interval-ms: 15000`) запрашивает батч до 50 сообщений через `GET /v1/internal/telegram/pending`.
  - Защищен атомарным замком `AtomicBoolean processingLock`, исключающим наложение параллельных циклов при сетевых задержках.
  - Отправляет сообщения клиентам в Telegram в HTML-режиме с задержкой `rateLimitDelayMs` (40 мс) для предотвращения 429 Too Many Requests от Telegram.
  - Формирует отчет доставки (`SENT` / `FAILED`) и отправляет подтверждение через `POST /v1/internal/telegram/ack`.

### 5. Сетевой контракт с бэкендом JF-1C
- Конфигурация: `BackendClientConfig` с базовым URL `${JF_BACKEND_BASE_URL:http://localhost:8080/api}`.
- Заголовок аутентификации: `X-Internal-Token: ${INTERNAL_BOT_TOKEN}`.
- Реализация: [`JfInternalApiClient.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/client/JfInternalApiClient.java):
  - `POST /v1/internal/telegram/bind` — связывание токена с чатом.
  - `GET /v1/internal/telegram/chat/{chatId}/client` — поиск клиента по chatId.
  - `DELETE /v1/internal/telegram/chat/{chatId}` — отвязка Telegram от клиента.
  - `GET /v1/internal/clients/{clientId}/tasks?limit=N` — задачи клиента.
  - `GET /v1/internal/clients/{clientId}/documents?limit=N` — документы клиента.
  - `GET /v1/internal/telegram/pending?limit=N` — получение очереди неотправленных уведомлений.
  - `POST /v1/internal/telegram/ack` — подтверждение статуса отправки батча.

---

## 4. Cross-Cutting Concerns

### Безопасность и экранирование сообщений
- [`HtmlMessageFormatter.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/service/HtmlMessageFormatter.java):
  - Все пользовательские данные (названия задач, имена файлов, имена клиентов) обязательно проходят через метод `escape()`, заменяющий `&`, `<` и `>`.
  - Предотвращает ошибки парсинга Telegram API (`Bad Request: can't parse entities`) и инъекции разметки.
  - Все ссылки на личный кабинет формируются на базе официального домена GitHub Pages: `https://mrsgemaseny.github.io/JF-1C`.

### Кэширование сессий
- [`UserSessionCache.java`](file:///C:/Users/murat/IdeaProjects/zhan-finance-tgbot/src/main/java/kz/zhanfinance/bot/service/UserSessionCache.java):
  - In-memory Caffeine кэш с TTL 5 минут и лимитом 5 000 записей.
  - Позволяет обрабатывать частые команды (`/tasks`, `/docs`, `/status`) без повторных HTTP-запросов к монолиту для проверки профиля пользователя.
  - Инвалидируется мгновенно при командах `/start <token>` (новая привязка) и `/unlink` (отвязка).

### Обработка сбоев и отказоустойчивость
- При недоступности монолита бэкенда методы `JfInternalApiClient` логируют предупреждения и возвращают пустые списки или `Optional.empty()`.
- Пользователю выдается корректное сообщение об ошибке («Сервис временно недоступен, попробуйте позже») без технических стектрейсов.
- Очередь Outbox: сообщения со статусом `FAILED` сохраняются в монолите с текстом ошибки и могут быть повторно обработаны или исследованы администратором.

---

## 5. Where to Put New Code (Cheat Sheet)

| Что нужно сделать | Куда помещать код |
|---|---|
| **Новая Telegram-команда** | Создать класс в `src/main/java/kz/zhanfinance/bot/handler/`, реализовать `CommandHandler`, добавить в `CommandDispatcher` |
| **Новый метод взаимодействия с бэкендом** | Добавить метод в интерфейс `BackendClient.java` и реализовать его в `JfInternalApiClient.java` |
| **Новый DTO внутреннего API** | Добавить record в `src/main/java/kz/zhanfinance/bot/client/dto/` |
| **Новый шаблон форматирования сообщения** | Добавить статический метод в `kz.zhanfinance.bot.service.HtmlMessageFormatter.java` |
| **Изменение таймингов опроса outbox / кэша** | Изменить параметры в `src/main/resources/application.yml` (`jf.outbox.*`, `jf.cache.*`) |
| **Юнит- или интеграционный тест** | `src/test/java/kz/zhanfinance/bot/` (по категориям: `handler`, `client`, `service`, `scheduler`) |
