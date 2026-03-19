# 🌸 Flower Marketplace Backend

A production-ready Spring Boot 3 backend for a Flower Marketplace platform built with Clean Architecture + Modular Monolith.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.2 |
| Security | Spring Security + JWT |
| Database | MySQL 8 + Spring Data JPA + Hibernate |
| Cache | Redis |
| Messaging | RabbitMQ |
| Search | Elasticsearch 8 |
| Real-time | WebSocket (STOMP) |
| API Docs | Swagger / OpenAPI 3 |
| Mapping | MapStruct |

## Module Structure

```
src/main/java/com/flowermarketplace/
├── common/                  # Cross-cutting concerns
│   ├── config/              # Security, Redis, RabbitMQ, WebSocket, Swagger
│   ├── enums/               # Role, OrderStatus, ListingStatus, etc.
│   ├── exception/           # Global exception handler + custom exceptions
│   ├── response/            # ApiResponse<T>, PagedResponse<T>
│   └── security/            # JwtUtil, JwtAuthenticationFilter
├── auth/                    # JWT auth, register, login, refresh token
├── user/                    # Profile management, address management
├── listing/                 # Flower listings, image upload, Elasticsearch
├── order/                   # Order lifecycle, status management
├── payment/                 # Payment processing
├── chat/                    # Real-time WebSocket messaging
├── review/                  # Ratings and reviews
├── notification/            # Email + system notifications via RabbitMQ
└── admin/                   # Admin dashboard endpoints
```

## Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ (for local dev)
- Maven 3.9+

### Run with Docker Compose

```bash
# Start all services
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop all
docker-compose down
```

### Run Locally

```bash
# Start infrastructure only
docker-compose up -d mysql redis rabbitmq elasticsearch

# Run the app
./mvnw spring-boot:run
```

### API Documentation

Once running, visit:
- Swagger UI: http://localhost:8080/api/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/api/v3/api-docs

### RabbitMQ Management UI
- URL: http://localhost:15672
- Username: `guest` / Password: `guest`

## API Overview

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/register` | Register new user |
| POST | `/api/auth/login` | Login |
| POST | `/api/auth/refresh-token` | Refresh access token |
| POST | `/api/auth/logout` | Logout |

### Users
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users/me` | Get own profile |
| PUT | `/api/users/me` | Update profile |
| GET/POST/PUT/DELETE | `/api/users/me/addresses` | Address management |

### Listings
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/listings` | Create listing |
| GET | `/api/listings/{id}` | Get listing |
| PUT | `/api/listings/{id}` | Update listing |
| DELETE | `/api/listings/{id}` | Archive listing |
| PATCH | `/api/listings/{id}/publish` | Publish listing |
| GET | `/api/listings/public` | Browse active listings |

### Orders
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/orders` | Create order |
| GET | `/api/orders/{id}` | Get order |
| GET | `/api/orders/history` | Order history |
| PATCH | `/api/orders/{id}/cancel` | Cancel order |

### Chat (WebSocket)
- Connect to: `ws://localhost:8080/api/ws`
- Send message: `/app/chat.send`
- Subscribe to messages: `/user/queue/messages`

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | MySQL host |
| `DB_PASSWORD` | password | MySQL password |
| `REDIS_HOST` | localhost | Redis host |
| `RABBITMQ_HOST` | localhost | RabbitMQ host |
| `ELASTICSEARCH_URI` | http://localhost:9200 | Elasticsearch URL |
| `JWT_SECRET` | (set in yml) | JWT signing secret (change in prod!) |

## Architecture

The project follows **Clean Architecture** principles within a **Modular Monolith**:

- Each module has its own `controller → service → repository → entity` layers
- Modules communicate via service interfaces, not direct DB access
- RabbitMQ decouples notifications from business logic
- Elasticsearch powers full-text listing search
- Redis is available for caching sessions and frequently-read data
- WebSocket enables real-time chat and push notifications
