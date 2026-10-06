# Viva demo script

All application requests below go through the API Gateway at `http://localhost:8080`.

1. Start the infrastructure with `docker compose up -d`, then Config Server, Eureka, and the six application services. Confirm `GET http://localhost:8761` lists every service as `UP`. Confirm the Config Server by requesting `http://localhost:8888/food-service/default` with `config/configpass`.
2. Import `postman/FoodOrdering.postman_collection.json`. Run **Register customer** and **Login customer**; the collection saves the bearer token automatically. A supplied `role: ADMIN` on public registration must still result in a `CUSTOMER` account.
3. Run **Food cache check** twice. The first response has `X-Cache: MISS`, and the next has `X-Cache: HIT`. Redis contains the `foods::1` cache key.
4. Run **Login seeded admin**, then **Admin view all food** and **Create food as admin**. A customer call to `GET /foods?all=true` or `POST /foods` must be rejected with 403; the same admin calls succeed.
5. Run **Place successful order**, **Get my orders**, and **Get payment**. The order is `PLACED`, payment is `SUCCESS`, and the `order.placed` message is visible in Kafka UI at `http://localhost:8090`. Use **Admin notifications** to show the persisted notification.
6. Run **Place failed order**. It returns 402 and persists `PAYMENT_FAILED`; because the transaction does not commit as placed, no new `order.placed` Kafka event is produced.
7. Change `app.welcome-message` in `config-repo/food-service.yml`, POST to `/actuator/refresh` on Food Service, then show the new value from `GET /foods/info`.
