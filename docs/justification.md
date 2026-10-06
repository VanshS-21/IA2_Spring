# Technology justification

- **Config Server:** puts credentials, ports, JWT and infrastructure endpoints in one environment-specific location, enables refresh, and keeps secrets out of services.
- **Spring Security + JWT:** provides stateless authentication, BCrypt password protection, and locally enforced RBAC even when a service is called directly.
- **OpenFeign:** gives typed, Eureka-load-balanced HTTP contracts. Order depends on Food and Payment APIs rather than their tables, business rules, or database schemas.
- **Kafka:** decouples order completion from notification availability. Consumer groups enable replay, durability, independent analytics, and future email/SMS consumers.
- **Redis:** menu reads are comparatively frequent. TTL plus cache put/evict on menu writes limits staleness while reducing MySQL work.
