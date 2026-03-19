# ─────────────────────────────────────────────────────────────────
# Flower Marketplace – Makefile
# ─────────────────────────────────────────────────────────────────

.PHONY: help build run run-dev stop logs clean test infra infra-stop

help:
	@echo ""
	@echo "  make build        Build Docker image"
	@echo "  make run          Start full stack (prod mode)"
	@echo "  make run-dev      Start with dev profile (MailHog included)"
	@echo "  make run-monitor  Start with monitoring profile (Kibana)"
	@echo "  make infra        Start only infrastructure (no app)"
	@echo "  make infra-stop   Stop infrastructure"
	@echo "  make stop         Stop all containers"
	@echo "  make logs         Tail application logs"
	@echo "  make test         Run tests"
	@echo "  make clean        Stop and remove all volumes"
	@echo ""

build:
	docker compose build --no-cache app

run:
	docker compose up -d

run-dev:
	docker compose --profile dev up -d

run-monitor:
	docker compose --profile monitoring up -d

infra:
	docker compose up -d mysql redis rabbitmq elasticsearch

infra-stop:
	docker compose stop mysql redis rabbitmq elasticsearch

stop:
	docker compose down

logs:
	docker compose logs -f app

test:
	./mvnw test

clean:
	docker compose down -v --remove-orphans
	docker image prune -f
