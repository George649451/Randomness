.PHONY: help install run check docker-build docker-run up down logs fly-deploy

help:
	@echo "Targets:"
	@echo "  install       pip install -r requirements.txt"
	@echo "  run           run the bot locally (needs .env)"
	@echo "  check         syntax + import smoke test"
	@echo "  docker-build  build the container image"
	@echo "  up / down     docker compose up -d --build / down"
	@echo "  logs          follow docker compose logs"
	@echo "  fly-deploy    deploy to Fly.io"

install:
	pip install -r requirements.txt

run:
	python bot.py

check:
	python -m compileall -q bot.py config.py claude_client.py
	python -c "import ast; [ast.parse(open(f).read(), f) for f in ('bot.py','config.py','claude_client.py')]; print('syntax OK')"

docker-build:
	docker build -t inline-claude-bot:latest .

docker-run:
	docker run --rm --env-file .env inline-claude-bot:latest

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f

fly-deploy:
	fly deploy
