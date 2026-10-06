# PRD: Online Food Ordering & Delivery Management System (Spring Boot Microservices)

**Audience:** an autonomous coding agent that will build the whole project end to end.
**Goal:** a runnable, demonstrable system that satisfies every mandatory item of the assignment and can be defended in a viva (each technology must have a visible, demonstrable reason to exist).

---

## 0. How to use this document (agent instructions)

1. Read the whole PRD before writing code. Section 3 (Decisions) resolves ambiguities in the assignment; follow it.
2. Build in the phases of Section 15. Each phase ends with a verification step; do not move on until it passes.
3. Never hard-code DB URL/username/password, Kafka, Redis, ports or JWT secret in a service. They live only in the Config Server repo (Section 5).
4. Never let a service touch another service's database. Inter-service data goes through Feign (sync) or Kafka (async).
5. Produce the deliverables in Section 17 (README, Postman collection, demo script, docker-compose).
6. If something is underspecified, choose the simplest option consistent with Section 3 and record it in `README.md` under "Assumptions".

---

## 1. Understanding of the assignment

Build a distributed food ordering platform. Customers register, log in (JWT), browse food, place orders, pay (simulated). Admins manage food and see all orders. After an order is successfully placed, an event is published to Kafka and a Notification Service consumes it and prints a simulated confirmation.

**The evaluator will check these seven demonstrations** (every design choice below serves them):

| # | Demonstration | Where satisfied |
|---|---|---|
| D1 | Config loaded from Config Server | Sec 5; startup logs + `/actuator/env` |
| D2 | JWT auth + role-based authorization, 403 for wrong role | Sec 6 |
| D3 | Feign inter-service calls | Sec 9, 13 |
| D4 | Redis cache hit vs miss | Sec 8 |
| D5 | Kafka producer → topic → consumer | Sec 10, 11 |
| D6 | Independent database ownership | Sec 3, 4, DB script |
| D7 | Full flow: request → notification | Sec 12, 16 |

Also required: explain and justify Feign, Config Server, Kafka, Redis, Spring Security (Sec 18 gives the text for README).

---

## 2. Technology stack and versions

- Java 17, Maven (multi-module parent POM or independent modules in one repo; prefer a parent POM with module per service).
- Spring Boot 3.3.x, Spring Cloud 2023.0.x (compatible release train; verify compatibility if you change either).
- Starters: `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation`, `-actuator`, `-data-redis`, `spring-kafka`, `spring-cloud-starter-config`, `spring-cloud-starter-openfeign`, `spring-cloud-starter-netflix-eureka-client/server`, `spring-cloud-starter-gateway` (reactive; gateway module must NOT include `spring-boot-starter-web`), `spring-cloud-starter-loadbalancer`.
- JWT: `io.jsonwebtoken:jjwt-api/impl/jackson` 0.12.x (HS256).
- MySQL 8 (driver `mysql-connector-j`), Redis 7, Kafka 3.x (KRaft mode, no Zookeeper), Lombok, springdoc-openapi (optional Swagger UI per service), Testcontainers or Mockito/JUnit 5 for tests.
- Everything infrastructure-related runs through `docker-compose.yml` (MySQL, Redis, Kafka). Microservices run locally via IDE/`mvn spring-boot:run` (optionally also containerised, see Sec 16).

---

## 3. Key design decisions (resolving ambiguity)

1. **Service discovery: include Eureka.** Assignment calls it optional, but Feign `name = "food-service"` and gateway `lb://` routes need it. Fallback if Eureka is skipped: `url=` in `@FeignClient` from config. Chosen: Eureka.
2. **Mandatory services:** config-server, discovery-server (Eureka), api-gateway, user-service, food-service, order-service, payment-service, notification-service. (8 deployables.)
3. **Database per service** (one MySQL server, separate schemas AND separate DB users with grants only on their own schema, so D6 is provable):
   `user_db`, `food_db`, `order_db`, `payment_db`. Notification service has no DB (or optional `notification_db` storing sent notifications; recommended, small table, makes the demo visible via an API).
4. **No cross-service foreign keys.** `Order.customerId`, `OrderItem.foodId`, `Food.restaurantId` (same service so FK is fine for restaurant) are plain IDs. Order stores a **price snapshot** per item.
5. **JWT is issued by User Service, validated at two layers:**
   - API Gateway validates signature/expiry (global filter), rejects invalid/missing tokens with 401 on protected routes, and forwards the original `Authorization` header (plus `X-User-Id`, `X-User-Role` convenience headers).
   - Each downstream service also validates the JWT with Spring Security (shared secret from Config Server) and enforces roles. This keeps role-based 403 working when a service is called directly and makes the security demo robust. Do **not** trust `X-User-*` headers for authorization; they are informational only.
6. **Identity from the token, not the body.** Order creation derives `customerId` from the JWT. A CUSTOMER can only read/cancel their own orders (otherwise 403). ADMIN can read all.
7. **Order creation algorithm** (resolves the circular need for `orderId` in payment):
   save order `PENDING` → Feign food check → Feign payment → update to `PLACED` or `PAYMENT_FAILED` → publish Kafka event only on `PLACED`. Details in Sec 12.
8. **Roles in token claim** `role` (single string `CUSTOMER` | `ADMIN`); Spring authority `ROLE_CUSTOMER` / `ROLE_ADMIN`; use `hasRole`.
9. **Registration always creates CUSTOMER.** Admin is created by a seed (`DataLoader`) on User Service startup (`admin@food.com` / `Admin@123`, password from config property, BCrypt-hashed). A request body `role=ADMIN` on public register must be ignored.
10. **Passwords** stored with BCrypt, never returned in responses (use DTOs, never expose entities).
11. **Uniform error format** across services (Sec 14).

---

## 4. Architecture

```
                     +------------------+
                     |  Config Server   | 8888  (config-repo: native/git)
                     +--------+---------+
                              | all services fetch config at startup
+--------+     +--------------v---------------+        +-------------+
| Client |---->|  API Gateway 8080 (JWT check)|<------>| Eureka 8761 |
+--------+     +--+-----+------+------+-------+        +-------------+
                  |     |      |      |
        +---------+  +--+---+ +--+---+ +--+-------+
        |User 8081| |Food  | |Order | |Payment   |
        |user_db  | |8082  | |8083  | |8084      |
        +---------+ |food_db |order_db |payment_db|
                    |+Redis| +--+--+--+ +----^-----+
                    +--^---+    |  |         |
                       |  Feign |  | Feign   |
                       +--------+  +---------+
                                |
                      Kafka topic "order-created"
                                |
                     +----------v-----------+
                     | Notification 8085     |
                     | (consumer, notification_db optional)
                     +-----------------------+
```

Ports: config 8888, eureka 8761, gateway 8080, user 8081, food 8082, order 8083, payment 8084, notification 8085. MySQL 3306, Redis 6379, Kafka 9092.

Repository layout:

```
food-ordering-system/
├── pom.xml                     (parent)
├── config-repo/                (central config files, see Sec 5)
├── config-server/
├── discovery-server/
├── api-gateway/
├── user-service/
├── food-service/
├── order-service/
├── payment-service/
├── notification-service/
├── docker-compose.yml
├── docker/mysql-init/init.sql  (schemas + per-service DB users)
├── postman/FoodOrdering.postman_collection.json
├── docs/ (architecture.md, demo-script.md, justification.md)
└── README.md
```

Package convention per service: `controller, service, repository, entity, dto, config, security, client (feign), event, exception`.

---

## 5. Config Server (mandatory demo D1)

**config-server** (`@EnableConfigServer`, port 8888).

- Backend: `spring.profiles.active=native` pointing to `classpath:/config` or a filesystem path to `../config-repo` (simplest, works offline). README must also explain how to switch to a Git repo (`spring.cloud.config.server.git.uri`).
- Secure the config server with HTTP basic (`spring.security.user`), clients send credentials via `spring.cloud.config.username/password`. (Optional but good for viva.)

**config-repo files** (name = `spring.application.name` of each client):
`application.yml` (shared by all), `user-service.yml`, `food-service.yml`, `order-service.yml`, `payment-service.yml`, `notification-service.yml`, `api-gateway.yml`, `discovery-server.yml`.

`application.yml` (shared) contains: Eureka client URL, `management.endpoints.web.exposure.include=health,info,env,refreshscope`, `jwt.secret` (>= 256-bit base64 string), `jwt.expiration-ms=3600000`, Kafka bootstrap servers, logging pattern.

Per-service files contain: `server.port`, `spring.datasource.url/username/password`, `spring.jpa.hibernate.ddl-auto=update`, `spring.jpa.show-sql=true`, `spring.jpa.properties.hibernate.dialect`, plus service-specific (Redis for food-service, Kafka producer for order-service, Kafka consumer for notification-service, Feign timeouts for order-service).

Example `food-service.yml`:
```yaml
server:
  port: 8082
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/food_db
    username: food_user
    password: food_pass
  jpa:
    hibernate.ddl-auto: update
    show-sql: true
  data.redis:
    host: localhost
    port: 6379
  cache:
    type: redis
    redis.time-to-live: 600000   # 10 min
```

**Client side** (every service except config-server): only these in local `application.yml`/`bootstrap`:
```yaml
spring:
  application:
    name: food-service
  config:
    import: "optional:configserver:http://localhost:8888"   # use non-optional ("configserver:...") so startup FAILS if config is missing; this proves dependency in the demo
```
Use `fail-fast` behaviour: non-optional import. No datasource/Kafka/Redis/port/JWT values may exist in service source trees. Start order: config-server → discovery-server → everything else.

**Demo D1:** (a) show service log line "Fetching config from server at: http://localhost:8888"; (b) `GET http://localhost:8888/food-service/default` returns JSON; (c) change a property (e.g. Redis TTL or a `app.greeting`) in config-repo, call `POST /actuator/refresh` on the service, show new value at `GET /actuator/env/...`. Implement `@RefreshScope` demo property in food-service (`app.welcome-message`, exposed on `GET /foods/info`, public).

---

## 6. Security (mandatory demo D2)

### 6.1 User Service authentication
Flow exactly as the assignment diagram: `POST /auth/login` → `AuthenticationManager` → `DaoAuthenticationProvider` with `CustomUserDetailsService` (loads user by email from DB) + `BCryptPasswordEncoder` → on success `JwtService.generateToken` → return token.

JWT claims: `sub` = email, `userId`, `role`, `iat`, `exp`. HS256 with `jwt.secret` from config.

### 6.2 Common security module
Create a small shared library module `common-security` (jar) used by user/food/order/payment services OR duplicate minimal code per service (acceptable). Contents: `JwtUtil` (parse/validate), `JwtAuthenticationFilter extends OncePerRequestFilter` (reads `Authorization: Bearer`, builds `UsernamePasswordAuthenticationToken` with authority `ROLE_<role>` and principal info incl. userId), `SecurityConfig` (stateless, CSRF off, `SessionCreationPolicy.STATELESS`), custom `AuthenticationEntryPoint` (401 JSON) and `AccessDeniedHandler` (403 JSON). Enable `@EnableMethodSecurity`.

### 6.3 Authorization matrix (enforce at service level; gateway only authenticates)

| Endpoint | Public | CUSTOMER | ADMIN |
|---|---|---|---|
| POST /users/register, POST /auth/login | yes | | |
| GET /users/{id} | | own id only | any |
| PUT /users/{id} | | own id only (cannot change role) | any |
| DELETE /users/{id} | | no (403) | yes |
| GET /foods, GET /foods/{id} | | yes | yes |
| POST/PUT/DELETE /foods | | **403** | yes |
| POST /orders | | yes | no (403) |
| GET /orders/{id} | | own order only | any |
| GET /orders/customer/{cid} | | only if cid == self | any |
| GET /orders (all, admin) | | 403 | yes |
| PUT /orders/{id}/cancel | | own order | any |
| POST /payments | internal: any authenticated (token forwarded by Feign) | | |
| GET /payments/order/{orderId} | | | yes |
| GET /notifications | | | yes |

Explicitly: an authenticated CUSTOMER calling `POST /foods` must get **HTTP 403** with JSON body. Missing/invalid token → **401**.

### 6.4 Gateway security
Reactive `GlobalFilter` (order -1) : skip paths `/users/register`, `/auth/login`, `/actuator/health`, swagger paths; otherwise require valid JWT else 401; on success add `X-User-Id`, `X-User-Role`; keep `Authorization`. Do not use Spring MVC classes in the gateway.

---

## 7. User Service (port 8081, `user_db`)

Entity `User` (table `users`): `customerId` (Long, PK, auto) , `name`, `email` (unique, not null), `password` (BCrypt), `phone`, `role` (enum `CUSTOMER`,`ADMIN`), `createdAt`.
(Use `customerId` as field/column name per assignment; map it as `customer_id`.)

Endpoints:
- `POST /users/register` body `{name,email,password,phone}` → 201 `UserResponse{customerId,name,email,phone,role}`; validation (`@Email`, `@NotBlank`, password min 8); duplicate email → 409.
- `POST /auth/login` body `{email,password}` → 200 `{token, tokenType:"Bearer", expiresIn, userId, role}`; bad credentials → 401.
- `GET /users/{id}`, `PUT /users/{id}` (name, phone, optionally password), `DELETE /users/{id}` per matrix. 404 if absent.
- `GET /users/me` (convenience, from token).

Seed admin via `CommandLineRunner` if not present.

---

## 8. Food/Restaurant Service (port 8082, `food_db`, Redis) — demo D4

Entities:
- `Restaurant`: `restaurantId`, `restaurantName`, `location`.
- `Food`: `foodId`, `foodName`, `price` (BigDecimal), `category`, `availability` (boolean), `restaurantId` (ManyToOne to Restaurant, expose `restaurantId` in DTO).

Endpoints (per assignment):
- `POST /foods` (ADMIN) → 201
- `GET /foods/{id}` (authenticated) → cached
- `GET /foods` (authenticated; query params `category`, `restaurantId`, `availableOnly=true` default behaviour: return available items, `?all=true` for ADMIN)
- `PUT /foods/{id}` (ADMIN) → updates DB and cache
- `DELETE /foods/{id}` (ADMIN) → deletes DB and evicts cache
- Also (small, for data setup): `POST /restaurants` (ADMIN), `GET /restaurants`. Seed 2 restaurants and ~8 foods at startup.
- `GET /foods/info` (public) returns the `@RefreshScope` message.

**Cache design (Spring Cache + Redis):**
- `@EnableCaching`; `RedisCacheManager` bean with `GenericJackson2JsonRedisSerializer` (or Jackson serializer on `FoodResponse`) so values are readable in `redis-cli`; cache name `foods`; key `#id` (prefix `food::`); TTL from config (10 min).
- `getFood(id)`: `@Cacheable(value="foods", key="#id")` on the service method returning `FoodResponse` DTO (must be Serializable/JSON-friendly; avoid caching JPA entities with lazy proxies).
- `updateFood`: `@CachePut(value="foods", key="#id")`.
- `deleteFood`: `@CacheEvict(value="foods", key="#id")`.
- Do not self-invoke cached methods inside the same class (proxy bypass).
- Do **not** cache `GET /foods` list with `@Cacheable` unless evicted on every write (`allEntries=true`); simplest: do not cache the list.
- **Hit/Miss visibility:** inside the *method body* (which only runs on a miss) log `"CACHE MISS - loading food {id} from MySQL"` at INFO; on hit nothing is logged by the body, so add an AOP/`CacheInterceptor` or a custom `CacheErrorHandler`-independent approach: simplest is a response header. Implement an `@Aspect` around the controller-level call: check `redisTemplate.hasKey("foods::"+id)` BEFORE calling service; log `CACHE HIT`/`CACHE MISS` and add response header `X-Cache: HIT|MISS`. Also record elapsed ms in a log line. Demo shows MISS (slower, SQL printed because `show-sql=true`) then HIT (no SQL printed).
- Feign calls from Order Service to `GET /foods/{id}` also benefit from this cache.

**Staleness demo (Sec 12 of assignment):** GET (MISS) → GET (HIT) → admin PUT price → GET returns new price (`@CachePut`) → admin DELETE → `redis-cli GET foods::{id}` is nil → GET returns 404.

Availability rule: Order Service treats `availability=false` as unavailable.

---

## 9. Order Service (port 8083, `order_db`) — Feign demo D3

Entities:
- `Order` (table `orders`): `orderId`, `customerId`, `restaurantId`, `orderDate` (LocalDateTime), `totalAmount` (BigDecimal), `status` (enum `PENDING`, `PLACED`, `PAYMENT_FAILED`, `CANCELLED`), `paymentId` (nullable), `OneToMany` items (cascade ALL).
- `OrderItem`: `orderItemId`, `orderId` (FK), `foodId`, `foodName` (snapshot), `quantity`, `price` (unit price snapshot).

DTOs:
- `CreateOrderRequest { restaurantId, items:[{foodId, quantity}], paymentMethod (optional string, default "CARD"), simulateFailure (optional boolean, default false, for demo of FAILED payment) }`
- `OrderResponse { orderId, customerId, restaurantId, orderDate, totalAmount, status, paymentStatus, items[...] }`

Endpoints: `POST /orders`, `GET /orders/{orderId}`, `GET /orders/customer/{customerId}`, `PUT /orders/{orderId}/cancel`, `GET /orders` (ADMIN, all orders, paged).

Feign clients (`@EnableFeignClients`):
```java
@FeignClient(name = "food-service")
public interface FoodClient { @GetMapping("/foods/{id}") FoodResponse getFood(@PathVariable("id") Long id); }

@FeignClient(name = "payment-service")
public interface PaymentClient { @PostMapping("/payments") PaymentResponse pay(@RequestBody PaymentRequest req); }
```
- `FeignAuthInterceptor implements RequestInterceptor`: copies the incoming `Authorization` header (from `RequestContextHolder`) to outgoing calls so downstream services authenticate the same user. Without this Feign calls get 401.
- `FeignErrorDecoder`: map 404 → `FoodNotFoundException`, 5xx/other → `ServiceUnavailableException` (HTTP 503 to client).
- Timeouts (from Config Server): connect 2s, read 5s. Optional resilience: Resilience4j circuit breaker/fallback on PaymentClient (bonus; if added, fallback marks payment FAILED).
- Add a short `docs/justification.md` section "Why Feign instead of DB sharing" (Sec 18).

Cancel rules: only `PLACED` orders cancellable (PENDING/PAYMENT_FAILED/CANCELLED → 409). Cancel sets status `CANCELLED`. (Refund/Kafka cancel event is optional bonus: publish `order-cancelled` to a second topic; notification service consumes it too.)

---

## 10. Payment Service (port 8084, `payment_db`)

Entity `Payment`: `paymentId`, `orderId`, `amount`, `paymentStatus` (enum `SUCCESS`,`FAILED`), `paymentDate`, `paymentMethod`.
- `POST /payments` body `{orderId, amount, paymentMethod, simulateFailure}` → 201 `{paymentId, orderId, amount, paymentStatus, paymentDate}`.
- Simulation logic: `simulateFailure=true` → FAILED; amount <= 0 → FAILED; amount > configurable `payment.max-amount` (config, e.g. 50000) → FAILED; otherwise SUCCESS. Optional deterministic rule only (no randomness, so demos are repeatable).
- Idempotency: if a payment for the same `orderId` already exists with SUCCESS, return it instead of creating another.
- `GET /payments/order/{orderId}` (ADMIN) for verification.
A FAILED payment returns HTTP 200/201 with `paymentStatus=FAILED` (not an exception), so Order Service can handle it as business outcome.

---

## 11. Kafka and Notification Service (port 8085) — demo D5

**Topic:** `order-created`, 3 partitions, replication factor 1. Create via `NewTopic` bean in order-service (and/or compose init). Key = `orderId` as string.

**Event** (`OrderCreatedEvent`): `{ "orderId":101, "customerId":10, "totalAmount":850, "status":"PLACED" }` (+ optional `eventTime`). Keep same class definition in both services (copy or shared `common-events` module). Serialization: `JsonSerializer`/`JsonDeserializer`, with consumer property `spring.json.trusted.packages=*` (or exact package) and `spring.json.use.type.headers=false` + `spring.json.value.default.type=<fqcn of consumer's event class>` to avoid class-not-found if packages differ.

**Producer (order-service):** `KafkaTemplate<String, OrderCreatedEvent>`; send AFTER the DB transaction commits (use `@TransactionalEventListener(phase = AFTER_COMMIT)` or call publish after `save` returns outside the transaction); log `"Published order-created event for order {id} partition {p} offset {o}"` from the send callback. Order Service must have **no** Feign client or HTTP call to Notification Service.

**Consumer (notification-service):** `@KafkaListener(topics="order-created", groupId="notification-group")`; logs:
`[NOTIFICATION] Your order #101 has been successfully placed.` plus customerId and amount. Persist to `notification_db.notifications` (`id, orderId, customerId, message, sentAt`) and expose `GET /notifications` (ADMIN) so the evaluator can see the result without reading logs. Handle bad messages with `DefaultErrorHandler` (retry 3 times, then log/skip, optionally dead-letter topic `order-created.DLT`). Consumer config: `auto-offset-reset=earliest`.

**Optional second consumer (assignment diagram shows "Other Consumer"):** add a small `@KafkaListener` with a different `groupId` (e.g. `analytics-group`) inside notification-service (or a tiny analytics-service) that logs `"[ANALYTICS] order {id} amount {x}"`. Demonstrates fan-out via consumer groups.

**Kafka infra:** compose service `apache/kafka` (KRaft single node) with advertised listener `localhost:9092`. Add Kafka UI (`provectuslabs/kafka-ui`) on 8090 (optional but excellent for demo: shows topic, partitions, messages).

---

## 12. Order placement flow (precise algorithm — demo D7)

`POST /orders` (CUSTOMER) at Gateway → Order Service:

1. Resolve `customerId` from JWT principal. Validate request (non-empty items, quantity >= 1).
2. For each item: `FoodClient.getFood(foodId)` (Feign → Food Service → Redis/MySQL).
   - not found → 404 `Food {id} not found`; `availability=false` → 400/409 `Food {name} is not available`.
   - all foods must have the same `restaurantId` as the request's `restaurantId` (else 400).
   - Price comes from Food Service response, **never from the client**.
3. Compute `totalAmount = Σ price × quantity`.
4. Persist `Order` (status `PENDING`) and items (snapshot of name/price) in one transaction → obtain `orderId`.
5. `PaymentClient.pay({orderId, amount, method, simulateFailure})`.
   - Feign/network error → set status `PAYMENT_FAILED`, return 503 with message.
   - `paymentStatus=FAILED` → status `PAYMENT_FAILED`, store `paymentId`, respond 402 Payment Required (or 200 with status; pick 402 and document it).
   - `SUCCESS` → status `PLACED`, store `paymentId`.
6. After commit, if `PLACED`: publish `OrderCreatedEvent` to Kafka (`order-created`). Never publish for failed payments.
7. Return 201 `OrderResponse` (status PLACED).

Logs at each step with a correlation id (use `X-Correlation-Id` header generated by gateway, propagated by Feign interceptor and put in MDC) so the demo log trail reads: gateway → order → food (cache hit/miss) → payment → kafka produce → notification consume.

---

## 13. API Gateway (port 8080)

Spring Cloud Gateway with Eureka discovery. Routes (explicit YAML in config-repo `api-gateway.yml`):

| Path predicate | Route |
|---|---|
| `/users/**`, `/auth/**` | `lb://user-service` |
| `/foods/**`, `/restaurants/**` | `lb://food-service` |
| `/orders/**` | `lb://order-service` |
| `/payments/**` | `lb://payment-service` |
| `/notifications/**` | `lb://notification-service` |

Include: global JWT filter (Sec 6.4), correlation-id filter, CORS config (allow localhost origins), default JSON 401. Clients must only ever call port 8080 in demos/Postman.

---

## 14. Cross-cutting requirements

- **Error response format (all services):** `{ "timestamp", "status", "error", "message", "path" }` via `@RestControllerAdvice`. Handle validation (400 with field errors), not found (404), conflict (409), forbidden (403), unauthorized (401), downstream unavailable (503).
- DTO ↔ entity mapping (MapStruct or manual); never return entities.
- Bean validation on all request DTOs.
- Actuator `/actuator/health` on all services; Eureka dashboard shows all 6 clients UP.
- Logging: INFO for business events; include service name in pattern.
- OpenAPI/Swagger UI per service (optional); if included, permit its paths.
- Unit tests (JUnit5 + Mockito): `OrderService` (success, food unavailable, payment failed, kafka not sent on failure), `PaymentService` rules, `JwtUtil`, `FoodService` cache annotations (with `@SpringBootTest` + embedded/Testcontainers Redis if feasible). Controller slice tests with `@WebMvcTest` for 403/401 cases. At least these tests must pass with `mvn test`.

---

## 15. Build phases (with verification gates)

1. **Skeleton + infra:** parent POM, `docker-compose.yml` (MySQL with `init.sql`, Redis, Kafka, Kafka UI), schemas and per-service MySQL users. *Verify:* `docker compose up -d`, connect to each schema with its own user; confirm food_user cannot read `order_db`.
2. **config-server + config-repo + discovery-server.** *Verify:* `/food-service/default` returns config; Eureka dashboard loads.
3. **user-service** (register, login, JWT, seed admin, security). *Verify:* register, login, call `GET /users/{id}` with and without token.
4. **food-service** (CRUD, roles, seed data, Redis caching, hit/miss aspect). *Verify:* the 4-step cache demo from Sec 8; CUSTOMER `POST /foods` → 403.
5. **payment-service.** *Verify:* success, failure via flag, idempotency.
6. **order-service** (entities, Feign, auth interceptor, flow minus Kafka). *Verify:* PLACED order; unavailable food; PAYMENT_FAILED; ownership 403; cancel.
7. **Kafka:** producer in order-service, consumer in notification-service. *Verify:* order → notification log line + `GET /notifications`; message visible in Kafka UI; no event for failed payment.
8. **api-gateway** with all routes + JWT filter. *Verify:* full flow only via :8080; invalid token → 401 at gateway.
9. **Hardening + tests + docs:** error handlers, tests, Postman collection, README, demo script, justification doc, optional Dockerfiles.

Definition of done = all acceptance criteria (Sec 19) pass from a clean checkout following only the README.

---

## 16. Infrastructure and runbook

`docker-compose.yml` services: `mysql` (env root password, mounted `init.sql`), `redis`, `kafka` (KRaft), `kafka-ui` (optional), `redis-insight` (optional).

`init.sql`:
```sql
CREATE DATABASE IF NOT EXISTS user_db; CREATE DATABASE IF NOT EXISTS food_db;
CREATE DATABASE IF NOT EXISTS order_db; CREATE DATABASE IF NOT EXISTS payment_db;
CREATE DATABASE IF NOT EXISTS notification_db;
CREATE USER IF NOT EXISTS 'user_user'@'%' IDENTIFIED BY 'user_pass';
GRANT ALL ON user_db.* TO 'user_user'@'%';
-- repeat for food_user/food_db, order_user/order_db, payment_user/payment_db, notification_user/notification_db
FLUSH PRIVILEGES;
```
(Config repo contains these per-service credentials; the assignment's example `root/root` is acceptable only if the agent documents why per-service users are preferred. Default to per-service users.)

README must contain: prerequisites, start order, a table of ports, `curl` commands for the whole flow through the gateway, how to run tests, how to view Redis keys (`redis-cli KEYS *`, `GET foods::1`), how to view Kafka messages, troubleshooting (Kafka advertised listeners, JSON trusted packages, Feign 401 due to missing header, gateway/MVC conflict).

---

## 17. Deliverables

1. Source for all 8 modules + `config-repo/`.
2. `docker-compose.yml`, `init.sql`.
3. `postman/` collection with environment (`baseUrl=http://localhost:8080`, token variables set automatically by login test scripts) covering every demo step.
4. `README.md`, `docs/architecture.md` (diagram + sequence diagram of order flow, Mermaid ok), `docs/demo-script.md`, `docs/justification.md`.
5. Passing unit tests.

---

## 18. Justification text to include in `docs/justification.md` (viva answers)

- **Spring Cloud Config Server:** one place for DB credentials, ports, Kafka/Redis settings, JWT secret; no secrets in code or jars; same artifact runs in any environment by changing profile; supports runtime refresh without rebuild; a rotated JWT secret/DB password is a single change.
- **Spring Security + JWT:** stateless auth that scales horizontally (no shared session store between services); token carries identity/role so each service authorizes locally; BCrypt protects stored passwords; RBAC enforces least privilege (customers cannot change menu).
- **OpenFeign:** declarative, type-safe REST clients with Eureka load balancing, timeouts, error decoding; keeps service boundaries - Order Service depends on Food Service's *contract (API)*, not its *schema*. Direct DB access would couple services to table structures, bypass business rules (availability, pricing logic, caching), break independent deployability/ownership, and make schema changes dangerous.
- **Kafka:** order placement must not fail or slow down because notification is slow/down (temporal and failure decoupling); new consumers (analytics, email, SMS) can subscribe without changing Order Service; messages are durable and replayable; partitions + consumer groups give scaling.
- **Redis:** menu reads are frequent and rarely change; cache cuts DB load and latency; trade-off is staleness, handled by `@CachePut`/`@CacheEvict` on admin writes plus TTL as safety net.

---

## 19. Acceptance criteria (agent must self-verify and report results in README "Verification" section)

D1. Stopping config-server and starting a service fails fast; with it running, logs show config fetched; `grep -r "jdbc:mysql" */src` returns nothing; refresh demo works.
D2. (a) Register customer → 201; duplicate → 409. (b) Login → token. (c) Customer `POST /foods` → 403 JSON. (d) No token on `GET /foods` → 401. (e) Admin `POST /foods` → 201. (f) Customer reading another customer's order → 403. (g) Public register with `role=ADMIN` still yields CUSTOMER.
D3. Placing an order logs Feign calls to food-service and payment-service in order-service logs, and request logs appear in those services; `food-service` DB credentials cannot access `order_db`.
D4. First `GET /foods/{id}` → `X-Cache: MISS` + SQL log; second → `X-Cache: HIT` + no SQL; `redis-cli GET foods::{id}` shows JSON; after admin update returns new price; after delete key is gone and API returns 404.
D5. Successful order produces Kafka message visible in Kafka UI/console consumer on `order-created`, and notification-service logs `Your order #<id> has been successfully placed.`; failed payment produces no message; stopping notification-service does not break order creation, and on restart it consumes the missed message.
D6. Four distinct schemas each containing only their own tables; no service config references another service's schema; cross-schema grant tests fail.
D7. One scripted run (Postman runner or `demo.sh`): register → login → view food (MISS) → view food (HIT) → place order → payment SUCCESS → Kafka event → notification, followed by admin viewing `GET /orders` and `GET /notifications`.
Plus: `mvn clean verify` passes; Eureka shows all services UP; all error responses follow Sec 14 format.

---

## 20. Out of scope / non-goals
Real payment gateway, delivery tracking maps, frontend UI (Postman/Swagger suffices), Kubernetes, distributed tracing (optional bonus), saga compensation beyond marking `PAYMENT_FAILED`.

## 21. Optional bonuses (only after all mandatory items pass)
Resilience4j circuit breaker on PaymentClient; `order-cancelled` event + notification; Dockerfiles + compose profile for all services; Zipkin/Micrometer tracing; encrypted config values (`{cipher}`) for DB passwords; paging/sorting on list endpoints; Testcontainers integration tests.
