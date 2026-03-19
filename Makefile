# ─── FlowerMarket Full-Stack Makefile ────────────────────────────────────────
# Yêu cầu: Docker, Node.js 18+, Java 17+, Maven 3.8+

.PHONY: help install dev build infra stop logs clean

help:
	@echo ""
	@echo "  🌸 FlowerMarket — Hướng dẫn chạy dự án"
	@echo ""
	@echo "  make infra       Khởi động MySQL, Redis, RabbitMQ, Elasticsearch (Docker)"
	@echo "  make backend     Chạy Spring Boot backend (port 8080)"
	@echo "  make frontend    Cài npm + chạy Vite dev server (port 3000)"
	@echo "  make dev         Chạy toàn bộ stack (infra + backend + frontend)"
	@echo "  make stop        Dừng tất cả Docker containers"
	@echo "  make logs        Xem logs backend"
	@echo ""

# ── Infrastructure (Docker) ───────────────────────────────────────────────────
infra:
	@echo "🐳 Khởi động infrastructure..."
	cd flower-marketplace-backend && docker-compose up -d mysql redis rabbitmq elasticsearch
	@echo "⏳ Chờ MySQL sẵn sàng (15s)..."
	sleep 15
	@echo "✅ Infrastructure đã sẵn sàng!"
	@echo "   MySQL    → localhost:3306"
	@echo "   Redis    → localhost:6379"
	@echo "   RabbitMQ → localhost:15672 (guest/guest)"
	@echo "   ES       → localhost:9200"

# ── Backend ───────────────────────────────────────────────────────────────────
backend:
	@echo "☕ Khởi động Spring Boot backend..."
	cd flower-marketplace-backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev &
	@echo "✅ Backend: http://localhost:8080/api"
	@echo "   Swagger: http://localhost:8080/api/swagger-ui.html"

# ── Frontend ──────────────────────────────────────────────────────────────────
frontend:
	@echo "⚛️  Cài đặt dependencies frontend..."
	cd flower-marketplace-frontend && npm install
	@echo "🚀 Khởi động Vite dev server..."
	cd flower-marketplace-frontend && npm run dev

# ── Full stack ────────────────────────────────────────────────────────────────
dev: infra
	@echo "🌸 Khởi động full stack..."
	$(MAKE) backend
	sleep 8
	$(MAKE) frontend

# ── Utilities ─────────────────────────────────────────────────────────────────
stop:
	cd flower-marketplace-backend && docker-compose down
	@echo "🛑 Đã dừng tất cả containers"

logs:
	cd flower-marketplace-backend && docker-compose logs -f app

build-frontend:
	cd flower-marketplace-frontend && npm run build
	@echo "✅ Frontend build xong: flower-marketplace-frontend/dist/"

clean:
	cd flower-marketplace-backend && docker-compose down -v
	rm -rf flower-marketplace-frontend/node_modules
	rm -rf flower-marketplace-frontend/dist
	@echo "🧹 Đã dọn sạch"
