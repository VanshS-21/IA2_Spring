# Food Ordering & Delivery Management System

An eight-deployable Spring Boot 3 / Spring Cloud food-ordering platform. It demonstrates centrally managed configuration, Eureka discovery, gateway and service-level JWT/RBAC, Redis menu caching, OpenFeign service contracts, and Kafka-based notifications.

## Prerequisites and startup

Install Java 17+ (JDK 27 is supported), Maven 3.9+, and Docker Desktop. In Windows PowerShell, run the following once from the repository root. It uses a project-local Maven repository, avoiding Windows file locks in a shared `.m2` cache:

`$env:MAVEN_OPTS = "-Duser.home=$PWD -Dmaven.repo.local=$PWD\.m2-repository"`

`mvn clean install -DskipTests`

Then run `docker compose up -d`, and start services in this order. Keep the same PowerShell window open so `MAVEN_OPTS` remains set:

1. `mvn -pl config-server spring-boot:run`
2. `mvn -pl discovery-server spring-boot:run`
3. Start `user-service`, `food-service`, `payment-service`, `order-service`, `notification-service`, and `api-gateway` in any order with `mvn -pl <module> spring-boot:run`.

| Component | Port |
|---|---:|
| Config Server | 8888 |
| Eureka | 8761 |
| Gateway | 8080 |
| User/Food/Order/Payment/Notification | 8081–8085 |
| Redis/Kafka/Kafka UI | 6379/9092/8090 |

All API requests in the demonstration use the gateway at `http://localhost:8080`.

## Quick flow

```powershell
$customer = Invoke-RestMethod http://localhost:8080/users/register -Method Post -ContentType application/json -Body '{"name":"Asha","email":"asha@example.com","password":"Password1","phone":"9999999999"}'
$login = Invoke-RestMethod http://localhost:8080/auth/login -Method Post -ContentType application/json -Body '{"email":"asha@example.com","password":"Password1"}'
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-WebRequest http://localhost:8080/foods/1 -Headers $headers # X-Cache: MISS
Invoke-WebRequest http://localhost:8080/foods/1 -Headers $headers # X-Cache: HIT
Invoke-RestMethod http://localhost:8080/orders -Method Post -Headers $headers -ContentType application/json -Body '{"restaurantId":1,"items":[{"foodId":1,"quantity":2}]}'
```

The seeded administrator is `admin@food.com` / `Admin@123`. A customer attempting `POST /foods` receives a JSON 403; requests without a bearer token receive a JSON 401.

## Config, cache, and Kafka demonstrations

Config comes exclusively from `config-repo`; service source resource files contain only service identity and Config Server import. `GET http://localhost:8888/food-service/default` displays the resolved configuration. Change `app.welcome-message` in `config-repo/food-service.yml`, call `POST /actuator/refresh`, then `GET /foods/info` to demonstrate refresh.

`GET /foods/{id}` adds `X-Cache: MISS` on its first Redis-backed call and `X-Cache: HIT` subsequently. Inspect values using `docker compose exec redis redis-cli GET foods::1`. Successful orders publish to `order-created`; inspect the three-partition topic at `http://localhost:8090` and notifications through `GET /notifications` as admin.

## Verification

Run `mvn -o verify` after the initial install. The full 11-module build has been verified with JDK 27 and Maven 3.9.16. Database URLs and credentials are intentionally confined to `config-repo/` (`rg "jdbc:mysql" */src` should have no matches).

The following checks were executed locally through the gateway:

| Requirement | Evidence |
|---|---|
| Config and discovery | Config Server and all six Eureka clients return healthy; clients fetch their config from port 8888. |
| Security | Registration/login, missing-token 401, and CUSTOMER menu-write 403 return JSON errors. Login uses `AuthenticationManager` + `DaoAuthenticationProvider` backed by the user database. |
| Cache | After `redis-cli DEL foods::1`, the first food request returns `X-Cache: MISS`; the second returns `X-Cache: HIT`. Food Service logs the cache result and elapsed time. |
| Feign, payment, Kafka | A successful order performs Food and Payment Feign calls, returns `PLACED`/`SUCCESS`, publishes `order-created`, and creates a notification. A simulated failed payment returns 402 and no success event. |
| Ownership and DB boundaries | Order identity derives from the JWT; separate MySQL schemas and per-schema users are provisioned by `docker/mysql-init/init.sql`. |
| Browser support | Gateway permits localhost CORS preflight requests and exposes `X-Cache`/`X-Correlation-Id`. |

For a complete viva sequence use [docs/demo-script.md](docs/demo-script.md). The Postman collection starts with a public register/login flow and stores the returned JWT in the collection variable.

## Assumptions

Payment failure is exposed as HTTP 402 after the order is persisted with `PAYMENT_FAILED`. The native Config Server reads the sibling `../config-repo` directory when run with Maven from `config-server/`; replace the native backend with `spring.cloud.config.server.git.uri` to use a Git configuration repository.
