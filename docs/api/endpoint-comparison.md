# Сопоставление маршрутов CoGoal

Таблица сравнивает старый проект CoGoalOld с целевыми адресами. Авторизация и минимальный профиль уже перенесены; текущее состояние описано в `auth-api-audit.md`.

Целевые маршруты взяты из `openapi.yaml` версии 1.3.0. Реализованные маршруты взяты из контроллеров `CoGoalOld`. Имена параметров пути при сравнении приведены к одному виду. Совпадение маршрута не означает совпадение типов идентификаторов, DTO, прав доступа, кодов ответа или бизнес-правил; эти расхождения перечислены в `auth-api-audit.md`.

## Целевые маршруты

| Метод | Путь через gateway | Контроллер в CoGoalOld |
| --- | --- | --- |
| POST | `/api/v1/auth/register` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| POST | `/api/v1/auth/login` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| POST | `/api/v1/auth/refresh` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| GET | `/api/v1/auth/me` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| PATCH | `/api/v1/auth/me/username` | Маршрут отсутствует |
| POST | `/api/v1/auth/logout` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| POST | `/api/v1/auth/password/forgot` | Маршрут отсутствует |
| POST | `/api/v1/auth/password/reset` | Маршрут отсутствует |
| POST | `/api/v1/auth/email/confirm` | Маршрут отсутствует |
| GET | `/api/v1/users/me` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/UserController.java` |
| PATCH | `/api/v1/users/me` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/UserController.java` |
| POST | `/api/v1/users/me/email-change` | Маршрут отсутствует |
| PUT | `/api/v1/users/me/active-item` | Маршрут отсутствует |
| GET | `/api/v1/users/{username}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/UserController.java` |
| GET | `/api/v1/users/me/operations` | Маршрут отсутствует |
| GET | `/api/v1/users/me/operations/export` | Маршрут отсутствует |
| GET | `/api/v1/categories` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/CatalogController.java` |
| GET | `/api/v1/charities` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/CatalogController.java` |
| GET | `/api/v1/goals` | Маршрут отсутствует |
| POST | `/api/v1/goals` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/GoalController.java` |
| GET | `/api/v1/goals/my` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/GoalController.java` |
| GET | `/api/v1/goals/{goalId}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/GoalController.java` |
| PATCH | `/api/v1/goals/{goalId}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/GoalController.java` |
| DELETE | `/api/v1/goals/{goalId}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/GoalController.java` |
| POST | `/api/v1/goals/{goalId}/milestones` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/GoalController.java` |
| PATCH | `/api/v1/goals/{goalId}/milestones/{milestoneId}` | Маршрут отсутствует |
| DELETE | `/api/v1/goals/{goalId}/milestones/{milestoneId}` | Маршрут отсутствует |
| POST | `/api/v1/goals/{goalId}/invitations` | Маршрут отсутствует |
| POST | `/api/v1/goals/{goalId}/join-requests` | Маршрут отсутствует |
| GET | `/api/v1/invitations` | Маршрут отсутствует |
| GET | `/api/v1/invitations/{invitationId}` | Маршрут отсутствует |
| POST | `/api/v1/invitations/{invitationId}/accept` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/InvitationController.java` |
| POST | `/api/v1/invitations/{invitationId}/decline` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/InvitationController.java` |
| POST | `/api/v1/invitations/{invitationId}/cancel` | Маршрут отсутствует |
| POST | `/api/v1/goals/{goalId}/publish` | Маршрут отсутствует |
| GET | `/api/v1/pacts` | Маршрут отсутствует |
| GET | `/api/v1/pacts/{pactId}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| POST | `/api/v1/pacts/{pactId}/termination-votes` | Маршрут отсутствует |
| DELETE | `/api/v1/pacts/{pactId}/termination-votes` | Маршрут отсутствует |
| GET | `/api/v1/pacts/{pactId}/deposits/me` | Маршрут отсутствует |
| POST | `/api/v1/pacts/{pactId}/deposits/me/payment` | Маршрут отсутствует |
| GET | `/api/v1/pacts/{pactId}/checkins` | Маршрут отсутствует |
| POST | `/api/v1/pacts/{pactId}/checkins` | Маршрут отсутствует |
| GET | `/api/v1/checkins/{checkinId}` | Маршрут отсутствует |
| POST | `/api/v1/checkins/{checkinId}/reviews` | Маршрут отсутствует |
| POST | `/api/v1/files` | Маршрут отсутствует |
| GET | `/api/v1/pacts/{pactId}/messages` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/MessageController.java` |
| POST | `/api/v1/pacts/{pactId}/messages` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/MessageController.java` |
| GET | `/api/v1/shop/items` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/ShopController.java` |
| POST | `/api/v1/shop/items/{itemId}/purchase` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/ShopController.java` |
| GET | `/api/v1/users/me/inventory` | Маршрут отсутствует |
| GET | `/api/v1/support/tickets` | Маршрут отсутствует |
| POST | `/api/v1/support/tickets` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/SupportController.java` |
| GET | `/api/v1/support/tickets/{ticketId}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/SupportController.java` |
| GET | `/api/v1/admin/support/tickets` | Маршрут отсутствует |
| PATCH | `/api/v1/admin/support/tickets/{ticketId}` | Маршрут отсутствует |
| GET | `/api/v1/admin/transactions` | Маршрут отсутствует |
| POST | `/api/v1/admin/transactions/{transactionId}/retry` | Маршрут отсутствует |
| POST | `/api/v1/webhooks/yookassa` | Маршрут отсутствует |

## Маршруты старого проекта вне целевого контракта

| Метод | Старый путь | Контроллер |
| --- | --- | --- |
| DELETE | `/api/v1/admin/categories/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CategoryAdminController.java` |
| DELETE | `/api/v1/admin/charities/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CharityAdminController.java` |
| DELETE | `/api/v1/admin/shop-items/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/ShopItemAdminController.java` |
| DELETE | `/api/v1/milestones/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/MilestoneController.java` |
| DELETE | `/api/v1/pacts/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| DELETE | `/api/v1/proofs/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/ProofController.java` |
| GET | `/api/v1/admin/categories` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CategoryAdminController.java` |
| GET | `/api/v1/admin/categories/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CategoryAdminController.java` |
| GET | `/api/v1/admin/charities` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CharityAdminController.java` |
| GET | `/api/v1/admin/charities/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CharityAdminController.java` |
| GET | `/api/v1/admin/shop-items` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/ShopItemAdminController.java` |
| GET | `/api/v1/admin/shop-items/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/ShopItemAdminController.java` |
| GET | `/api/v1/admin/support-tickets` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/SupportTicketAdminController.java` |
| GET | `/api/v1/auth/me` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| GET | `/api/v1/check-ins/to-review` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/CheckInController.java` |
| GET | `/api/v1/check-ins/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/CheckInController.java` |
| GET | `/api/v1/goals/feed` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/GoalController.java` |
| GET | `/api/v1/invitations/my` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/InvitationController.java` |
| GET | `/api/v1/pacts/my` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| GET | `/api/v1/pacts/{id}/check-ins` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/CheckInController.java` |
| GET | `/api/v1/pacts/{id}/deposit` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| GET | `/api/v1/proofs/{id}/file` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/ProofController.java` |
| GET | `/api/v1/support/tickets/my` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/SupportController.java` |
| GET | `/api/v1/users` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/UserController.java` |
| GET | `/api/v1/users/me/coin-transactions` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/UserController.java` |
| GET | `/api/v1/users/me/purchases` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/UserController.java` |
| PATCH | `/api/v1/admin/support-tickets/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/SupportTicketAdminController.java` |
| PATCH | `/api/v1/milestones/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/MilestoneController.java` |
| POST | `/api/v1/admin/categories` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CategoryAdminController.java` |
| POST | `/api/v1/admin/charities` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CharityAdminController.java` |
| POST | `/api/v1/admin/shop-items` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/ShopItemAdminController.java` |
| POST | `/api/v1/auth/change-password` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| POST | `/api/v1/auth/logout-all` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/AuthController.java` |
| POST | `/api/v1/check-ins/{id}/proofs` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/ProofController.java` |
| POST | `/api/v1/check-ins/{id}/reviews` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/CheckInController.java` |
| POST | `/api/v1/pacts` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| POST | `/api/v1/pacts/{id}/check-ins` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/CheckInController.java` |
| POST | `/api/v1/pacts/{id}/deposit` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| POST | `/api/v1/pacts/{id}/invitations` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/InvitationController.java` |
| POST | `/api/v1/pacts/{id}/join` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| POST | `/api/v1/pacts/{id}/leave` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| POST | `/api/v1/pacts/{id}/start` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/PactController.java` |
| PUT | `/api/v1/admin/categories/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CategoryAdminController.java` |
| PUT | `/api/v1/admin/charities/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/CharityAdminController.java` |
| PUT | `/api/v1/admin/shop-items/{id}` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/admin/ShopItemAdminController.java` |
| PUT | `/api/v1/auth/admin/users/{id}/roles` | `CoGoal-auth/src/main/java/edu/stankin/cogoalauth/web/controllers/admin/AdminUserController.java` |
| PUT | `/api/v1/users/me/avatar` | `CoGoal-main/src/main/java/edu/stankin/cogoalmain/web/controllers/UserController.java` |

## Особенности совпавших маршрутов

- `GET /users/{username}` в контракте ищет по username, а `GET /users/{id}` в старом коде принимает UUID. Это разные способы поиска, хотя шаблоны маршрута совпадают.
- `/auth/register`, `/auth/login`, `/auth/refresh` и `/auth/logout` совпадают по адресу, но имеют расхождения по полям, ответам или доступу.
- `/users/me`, `/goals`, `/goals/my`, `/shop/items`, `/support/tickets` и другие совпавшие адреса требуют проверки DTO и поведения по целевому контракту.

Всего в контракте 57 операций. Для 24 найдён такой же метод и шаблон пути; для 33 точного маршрута нет. Внутренние `/internal/**` в таблицы не включены.
