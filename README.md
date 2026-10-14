# Backend CoGoal

Backend включает CoGoal-main, CoGoal-auth, CoGoal-eureka и CoGoal-gateway. Все внешние API-запросы идут через gateway на порту 8080. Auth поддерживает регистрацию, вход по email или username, refresh/logout, сброс пароля и подтверждение нового email.

## Запуск

Frontend — отдельный Git-репозиторий. Для совместного запуска клонируйте его в `Frontend` внутри этой папки, если его ещё нет:

```powershell
git clone --branch features/CG-4/FrontDeployConfig https://github.com/CoGoal/Frontend.git Frontend
```

Если фронт уже находится в другой папке, задайте `FRONTEND_CONTEXT` в `.env` — путь к папке с его `Dockerfile`. Папка `Frontend` исключена из Git backend; изменения фронта сохраняются в его собственном репозитории.

Из этой папки:

```powershell
docker compose up -d --build
```

Для новой копии репозитория сначала скопируйте `.env.example` в `.env` и задайте собственный случайный `JWT_SECRET` (не менее 32 символов). `.env` исключён из Git. В текущей рабочей копии файл уже создан. Для синхронизации профилей используется Kafka.

| Адрес | Назначение |
| --- | --- |
| http://localhost:5173 | Фронт: вход, регистрация, профиль и настройки аккаунта |
| http://localhost:8080/swagger-ui.html | Swagger UI, выбор main или auth |
| http://localhost:8080/auth/v3/api-docs | Реализованный OpenAPI auth |
| http://localhost:8080/main/v3/api-docs | Реализованный OpenAPI main |
| http://localhost:8080/api/v1/HelloWorld | Сохранённый тестовый эндпоинт main |
| http://localhost:8761 | Eureka |
| http://localhost:8025 | Локальные письма в Mailpit |

В локальном режиме main использует базу CoGoal, auth — cg-auth. Сервис auth-db-init создаёт cg-auth, если её ещё нет; он работает и с существующим томом PostgreSQL. В текущей рабочей копии сервисы переведены на Neon: main_db и auth_db. Main и auth не публикуют HTTP-порты наружу. Для остановки используйте `docker compose stop`.

Frontend собирается через Node.js и раздаётся Nginx. Запросы `/api/v1/**` проксируются в gateway внутри Compose; браузеру не нужен адрес контейнера. Ссылки `/reset-password?token=...` и `/email-confirm?token=...` открывают соответствующие формы даже после перезагрузки страницы. `FRONTEND_URL` в auth должен совпадать с внешним адресом фронта; по умолчанию это `http://localhost:5173`. При смене порта задайте и `FRONTEND_PORT`, и `FRONTEND_URL`.

Чтобы поднять только бэк без клона фронта:

```powershell
docker compose up -d --build kafka mailpit eureka auth cogoal-main gateway
```

## Базы Neon

Подключения задаются отдельно: MAIN_DB_URL / MAIN_DB_USERNAME / MAIN_DB_PASSWORD и AUTH_DB_URL / AUTH_DB_USERNAME / AUTH_DB_PASSWORD. Секреты хранятся в игнорируемом `.env`. В JDBC URL нужен TLS: `?sslmode=require&channelBinding=require`; пароль передаётся отдельной переменной. [Подключение Java к Neon](https://neon.com/docs/guides/java).

В текущем `.env` включён режим Neon:

```dotenv
COMPOSE_PATH_SEPARATOR=:
COMPOSE_FILE=docker-compose.yml:docker-compose.neon.yml
```

Поэтому обычный `docker compose up -d --wait` использует Neon. Дополнительный Compose-файл убирает зависимости от локального PostgreSQL и auth-db-init; они включаются только профилем local-db. Размер пула каждого сервиса по умолчанию 5, минимум простаивающих соединений 0. При необходимости задайте DB_POOL_SIZE и DB_POOL_MIN_IDLE.

Для новой копии заполните подключения в `.env` по `.env.example` и включите две переменные выше. На пустых базах схему создаст Liquibase. Существующие данные в этой рабочей копии перенесены из локального PostgreSQL вместе с историей Liquibase; количество строк и содержимое всех таблиц сверены. Исходный том и резервные копии сохранены. Payment-сервис ещё не подключён к backend, его база не участвует в переносе.

Чтобы вернуть локальный режим, уберите COMPOSE_FILE / COMPOSE_PATH_SEPARATOR и MAIN_DB_* / AUTH_DB_* из `.env`, затем пересоздайте сервисы. Это переключает их на прежний локальный том; новые записи из Neon назад автоматически не копируются.

Посмотреть базы можно в [консоли Neon](https://console.neon.tech) или DBeaver. В DBeaver создайте подключение PostgreSQL: Host из JDBC URL, порт 5432, Database auth_db либо main_db, Username из соответствующей переменной `.env`. Пароль берётся там же. В Driver properties задайте sslmode=require и channelBinding=require. Таблицы находятся в схеме public. [Настройки PostgreSQL в DBeaver](https://dbeaver.com/docs/dbeaver/Database-driver-PostgreSQL/).

## Авторизация

Регистрация:

```http
POST /api/v1/auth/register
Content-Type: application/json

{"email":"ivan@example.com","username":"ivan_dev","name":"Иван","password":"secret123"}
```

Вход:

```http
POST /api/v1/auth/login
Content-Type: application/json

{"login":"ivan_dev","password":"secret123"}
```

В login также можно передать email. Регистр идентификатора не учитывается, пробелы по краям удаляются. Username допускает латинские буквы, цифры и `_`, длина от 3 до 32. Пароль регистрации и сброса — минимум 8 символов и максимум 72 байта UTF-8.

Ответ содержит accessToken, refreshToken, expiresIn и user с данными учётной записи. ID пользователей и профилей — числа BIGINT (`Long`), в JWT `sub` — десятичная строка этого ID. Для закрытых запросов передавайте `Authorization: Bearer <accessToken>`. После пяти неверных паролей аккаунт блокируется на 30 минут; дальнейший вход отвечает 423 ACCOUNT_LOCKED. Счётчик общий для email и username. Logout требует access-токен; refresh использует только refresh-токен в теле. Уже выпущенные access-токены действуют до своего истечения после logout, смены пароля или email.

При необходимости первого администратора задайте BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD и BOOTSTRAP_ADMIN_USERNAME в `.env`; он создаётся при запуске auth. По умолчанию создание администратора отключено.

Auth владеет логином, email, паролем, ролями и токенами. `GET /api/v1/auth/me` возвращает данные аккаунта без имени и аватара. Имя и аватар хранятся и изменяются только в main: `PATCH /api/v1/users/me` возвращает полный профиль. Явное `avatarUrl: null` удаляет аватар; пропущенное поле сохраняет его. Передача username в этот PATCH отклоняется с 400. Смена логина выполняется отдельно: `PATCH /api/v1/auth/me/username` с телом `{"username":"new_login"}`. Gateway направляет этот запрос сразу в auth.

Профиль main создаётся асинхронно через Kafka. Сразу после регистрации GET /api/v1/users/me может ответить 404 PROFILE_PENDING; повторите запрос спустя несколько секунд. Начальные монеты задаются INITIAL_COINS (по умолчанию 100) и начисляются один раз. Начальное имя из запроса регистрации передаётся событием, в таблице аккаунтов auth оно не хранится.

## События профиля

```text
auth: аккаунт + outbox в одной транзакции
    → kafka/producer/ProfileOutboxPublisher
    → Kafka: cogoal.user-profiles.v1
    → main: kafka/consumer/UserProfileListener
    → Hibernate / user_profiles
```

Издатели, слушатель, конфигурация и модели событий находятся в отдельных пакетах `kafka` каждого сервиса. После регистрации публикуется UserRegistered; после смены username, подтверждения email или изменения роли — UserProfileUpdated. Новые события используют schemaVersion=2. Снимок аккаунта содержит ID, email, username, роль и монотонную версию аккаунта. Имя передаётся только при регистрации. Имя и аватар существующего профиля не изменяются событиями auth. Старую схему 1 main принимает для недоставленных событий и миграционных снимков.

Если событие обновления аккаунта пришло раньше регистрации, main сохраняет актуальные данные аккаунта и позднее принимает начальное имя отдельно. Изменения имени и аватара, сделанные пользователем в main, имеют приоритет над поздней регистрацией. Профиль и обработка событий используют одну блокировку Hibernate, чтобы параллельные изменения не затирали друг друга. ID пользователя — BIGINT, ключ Kafka — его десятичная строка. Пароли и токены в события не включаются. Имя топика задаёт KAFKA_PROFILE_TOPIC; consumer group main — KAFKA_PROFILE_GROUP.

Auth отмечает outbox DONE только после подтверждения Kafka. При недоступном брокере задача остаётся в базе и повторяется. DONE означает публикацию в брокер; обработка main происходит независимо. Main подтверждает запись после фиксации транзакции Hibernate. Повторная и старая доставка сохраняет баланс, дату создания и более новую версию профиля. При первом переходе на Kafka Liquibase повторно ставит ранее доставленные HTTP-задачи в очередь.

Ошибочные JSON, неподдерживаемая схема и конфликты данных сохраняются в `cogoal.user-profiles.v1.DLT`. Для прочих ошибок есть две повторные попытки перед DLT. Offset ошибочного сообщения подтверждается после успешной записи в DLT; при недоступном брокере сообщение остаётся необработанным. Для восстановления из DLT нужно устранить причину ошибки и повторить доставку. Kafka в compose доступна приложениям на kafka:29092, локальным инструментам — localhost:9092. Топики имеют три раздела; локальный брокер один.

## Письма

POST /api/v1/auth/password/forgot принимает email и отвечает 202, включая неизвестный адрес. POST /api/v1/auth/password/reset принимает token и newPassword. POST /api/v1/users/me/email-change требует JWT, newEmail и текущий password; POST /api/v1/auth/email/confirm принимает token из письма.

Ссылки одноразовые и действуют 30 минут. Токены хранятся как хеши; текст ожидающих писем зашифрован. При недоступном SMTP доставка повторяется до истечения ссылки. В локальном compose SMTP направлен в Mailpit. Для внешней доставки задайте SMTP_HOST, SMTP_PORT, SMTP_USERNAME, SMTP_PASSWORD, SMTP_AUTH, SMTP_STARTTLS и MAIL_FROM. FRONTEND_URL определяет адрес ссылок `/reset-password?token=...` и `/email-confirm?token=...`; соответствующие страницы должны быть реализованы во фронте.

## Проверки

Для сборки и тестов нужен JDK 17 или новее и Maven. Если в PATH стоит Java 8, задайте JAVA_HOME на установленный JDK.

```powershell
mvn test
python tests/smoke_auth.py
python tests/smoke_kafka.py
```

Первая команда запускает тесты всех четырёх модулей. Тесты auth используют PostgreSQL в Testcontainers при доступном Docker. smoke_auth проверяет compose через gateway и Mailpit. smoke_kafka временно останавливает main и Kafka, всегда запускает их обратно и проверяет доставку после восстановления, повторные/старые события и DLT. Обе проверки создают отдельные тестовые аккаунты; используется стандартное INITIAL_COINS=100.

## Контракт и дальнейшая работа

Main работает через Hibernate: сущности и репозитории находятся в `db/entity` и `db/repo`, транзакционная синхронизация — в `ProfileService`. Обработчики `GlobalExceptionHandler` находятся в `web/controllers` обоих сервисов. Схему создаёт Liquibase; Hibernate проверяет её через `ddl-auto=validate`.

Auth использует приложенную схему `src/main/resources/db/changelog/001-auth-schema.sql`: users и общую таблицу auth_tokens с BIGINT ID. XML master находится в `resources/db/db.changelog-master.xml` обоих сервисов. Старые changeset сохранены; новые миграции переводят существующие UUID-аккаунты и токены на BIGINT. Миграции 010–011 удаляют архивные legacy-таблицы auth после проверки переноса данных. В main пока сохраняется legacy_user_profiles для переноса старого баланса и даты создания. Outbox доставляет профили асинхронно. Старый access-токен с UUID после перехода требует refresh или нового входа. UUID остаются у внутренних задач очередей, семейств токенов и миграционных ссылок.

[Целевой OpenAPI](docs/api/openapi.yaml), [реализованная авторизация и ограничения](docs/api/auth-api-audit.md), [сопоставление со старым проектом](docs/api/endpoint-comparison.md).

В текущий main добавлены только профиль и защищённая синхронизация из auth. Цели, пакты, платежи, отчёты и остальные бизнес-домены из старого main пока не перенесены. Целевой YAML описывает также эти будущие функции; фактически доступные маршруты смотрите в Swagger каждого сервиса.

## Перенос полей профиля

Новые Liquibase changeset: main `003-profile-ownership.xml`, auth `009-profile-ownership.xml`. Старые changeset и их checksums сохранены. Перед обновлением существующей среды нужно дождаться доставки актуальных имён и аватаров в main, затем обновить main, auth и gateway. В этой локальной среде перед переносом сверены все существующие аккаунты; баланс и дата создания сохраняются.

Auth больше не содержит name/avatar_url в текущей таблице users. Outbox временно хранит начальное имя для надёжной доставки регистрации и недоставленные профильные данные старых миграций; после подтверждения Kafka они очищаются. Архивные таблицы auth удалены миграциями 010–011; резервные копии сохранены отдельно. Main становится единственным владельцем редактируемых имени и аватара.
