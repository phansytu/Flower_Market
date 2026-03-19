# 🌸 FlowerMarket — Full-Stack App

**React 18 + Vite + Spring Boot 3 + MySQL + Redis + RabbitMQ + WebSocket**

---

## 🗂 Cấu trúc dự án

```
flower-marketplace/
├── flower-marketplace-backend/     # Spring Boot 3 (port 8080)
│   ├── src/main/java/com/flowermarketplace/
│   │   ├── auth/           JWT login, register, refresh token
│   │   ├── user/           UserService, UserController, Address
│   │   ├── listing/        ListingService, Elasticsearch search
│   │   ├── order/          OrderService, order lifecycle
│   │   ├── payment/        PaymentService (COD, MoMo, ZaloPay ready)
│   │   ├── review/         ReviewService, RatingStats
│   │   ├── chat/           STOMP WebSocket chat
│   │   ├── notification/   RabbitMQ events, email templates
│   │   └── admin/          Admin panel, AuditLog, BanRecord
│   ├── docker-compose.yml
│   └── Dockerfile
│
├── flower-marketplace-frontend/    # React 18 + Vite (port 3000)
│   ├── src/
│   │   ├── FlowerMarket.jsx   Main app (single-file, 9 pages)
│   │   ├── api/               Axios + API calls per module
│   │   ├── context/           AuthContext (JWT state)
│   │   ├── hooks/             useApi.js (React Query hooks)
│   │   └── hooks/useWebSocket.js  STOMP chat hook
│   ├── vite.config.js         Proxy /api → localhost:8080
│   └── package.json
│
├── Makefile                   Full-stack run commands
└── README.md
```

---

## 🚀 Cách chạy nhanh

### 1. Yêu cầu hệ thống
- **Java 17+** và **Maven 3.8+**
- **Node.js 18+** và **npm 9+**
- **Docker** và **Docker Compose**

### 2. Khởi động infrastructure
```bash
# Chạy MySQL, Redis, RabbitMQ, Elasticsearch bằng Docker
make infra

# Hoặc thủ công:
cd flower-marketplace-backend
docker-compose up -d mysql redis rabbitmq elasticsearch
```

### 3. Cấu hình backend
```bash
cd flower-marketplace-backend
cp .env.example .env

# Chỉnh sửa .env (các giá trị mặc định đã hoạt động với Docker local):
# DB_HOST=localhost, DB_PORT=3306
# DB_USERNAME=floweruser, DB_PASSWORD=flowerpass
# JWT_SECRET=<any-base64-string>
```

### 4. Chạy backend
```bash
cd flower-marketplace-backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# API: http://localhost:8080/api
# Swagger: http://localhost:8080/api/swagger-ui.html
```

### 5. Chạy frontend
```bash
cd flower-marketplace-frontend
npm install
npm run dev

# App: http://localhost:3000
```

### 6. Tất cả trong 1 lệnh
```bash
make dev
```

---

## 🔌 API Integration Map

| Trang Frontend | API Endpoint | Phương thức |
|---|---|---|
| HomePage | `GET /api/listings/public` | Public |
| Category Listing | `GET /api/listings/public` + `GET /api/listings/search` | Public |
| Product Detail | `GET /api/listings/public/:id`, `GET /api/reviews/listing/:id/stats` | Public |
| Create Listing | `POST /api/listings` (multipart) | 🔐 SELLER |
| Chat | WebSocket `/api/ws` (STOMP) | 🔐 Auth |
| Order Page | `POST /api/orders`, `POST /api/payments` | 🔐 Auth |
| User Profile | `GET /api/users/me`, `GET /api/listings/my`, `GET /api/orders/my` | 🔐 Auth |
| Seller Dashboard | `GET /api/listings/my`, `PATCH /api/orders/:id/status` | 🔐 SELLER |
| Admin Dashboard | `GET /api/admin/dashboard`, `/users`, `/listings` | 🔐 ADMIN |

---

## 🔐 Xác thực (JWT)

```
POST /api/auth/register  →  { fullName, email, phone, password, role }
POST /api/auth/login     →  { email, password }
                         ←  { accessToken, refreshToken, id, fullName, role }

# Tất cả request sau dùng header:
Authorization: Bearer <accessToken>

# Khi token hết hạn (24h), tự động refresh:
POST /api/auth/refresh  →  { refreshToken }
                        ←  { accessToken }
```

### Tài khoản mặc định (sau khi seed DB):
| Email | Password | Role |
|---|---|---|
| admin@flower.vn | password123 | ADMIN |
| seller@flower.vn | password123 | SELLER |
| buyer@flower.vn | password123 | BUYER |

---

## 💬 WebSocket Chat

```javascript
// Frontend kết nối STOMP:
const client = new Client({
  webSocketFactory: () => new SockJS('/api/ws'),
  connectHeaders: { Authorization: `Bearer ${token}` },
})

// Gửi tin nhắn:
client.publish({
  destination: '/app/chat.send',
  body: JSON.stringify({ conversationId, content })
})

// Nhận tin nhắn:
client.subscribe('/user/queue/messages', (frame) => {
  const message = JSON.parse(frame.body)
})
```

---

## 🐳 Docker Services

| Service | Port | URL |
|---|---|---|
| Spring Boot App | 8080 | http://localhost:8080/api |
| MySQL 8.0 | 3306 | localhost:3306 |
| Redis 7.2 | 6379 | localhost:6379 |
| RabbitMQ | 5672 / 15672 | http://localhost:15672 (guest/guest) |
| Elasticsearch | 9200 | http://localhost:9200 |
| Kibana (optional) | 5601 | http://localhost:5601 |
| MailHog (dev) | 8025 | http://localhost:8025 |

---

## 🏗 Build Production

```bash
# Build frontend:
cd flower-marketplace-frontend
npm run build   # → dist/ folder

# Build backend JAR:
cd flower-marketplace-backend
mvn clean package -DskipTests

# Run với Docker Compose (full stack):
docker-compose up -d
# App: http://localhost:8080/api
```

---

## 📦 Tech Stack

**Frontend:**
- React 18 + Vite 5
- TanStack React Query (data fetching + cache)
- @stomp/stompjs + sockjs-client (WebSocket)
- Axios (JWT interceptor + auto-refresh)
- react-hot-toast (notifications)
- date-fns (date formatting)

**Backend:**
- Spring Boot 3.2 + Spring Security (JWT stateless)
- Spring Data JPA + MySQL 8
- Spring Data Redis (Lettuce)
- Spring AMQP + RabbitMQ
- Spring WebSocket (STOMP)
- Spring Data Elasticsearch
- MapStruct + Lombok
- SpringDoc OpenAPI (Swagger)
# Flower_Market
