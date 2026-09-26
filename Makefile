.PHONY: help up debug down restart logs test smoke load clean ps build

# Default target
help: ## Show this help message
	@echo ""
	@echo "  Document Intelligence Studio — Developer Commands"
	@echo ""
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""

# ─── Docker Compose ────────────────────────────────────────────

up: ## Build images and start all 7 containers in detached mode
	docker compose up --build -d

debug: ## Start with remote debug agents (api=5005, worker=5006, query=5007)
	docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build -d

down: ## Stop and remove all containers (keeps volumes)
	docker compose down

restart: ## Restart all containers without rebuilding
	docker compose restart

logs: ## Tail logs from all services
	docker compose logs -f

ps: ## Show running container status
	docker compose ps

# ─── Build & Test ──────────────────────────────────────────────

build: ## Compile all modules with Maven (skip tests)
	./mvnw package -DskipTests --no-transfer-progress

test: ## Run all unit tests
	./mvnw verify -DskipITs --no-transfer-progress

# ─── Smoke & Load Tests ────────────────────────────────────────

smoke: ## Run end-to-end smoke test against running cluster
	./scripts/smoke-test.sh

load: ## Run load/rate-limit benchmark (usage: make load USERS=20)
	./scripts/load-test.sh http://localhost:8080 $(or $(USERS),10)

# ─── Cleanup ───────────────────────────────────────────────────

clean: ## Stop containers AND remove all volumes (full reset)
	docker compose down -v
	docker system prune -f --filter "label=com.docker.compose.project=document-intelligence"
