COMPOSE = docker-compose
GRADLE  = ./gradlew

.PHONY: all build build-no-cache start stop down clean fclean re deploy logs ps test

all: up

up:
	$(GRADLE) build -x test
	$(COMPOSE) up --build -d

build:
	$(GRADLE) build -x test

start:
	$(COMPOSE) up -d

stop:
	$(COMPOSE) stop

down:
	$(COMPOSE) down

clean:
	$(COMPOSE) down -v

fclean:
	$(COMPOSE) down -v --rmi all

re: fclean up

deploy:
	$(GRADLE) build -x test
	$(COMPOSE) up --build --no-deps -d backend

logs:
	$(COMPOSE) logs -f

logs-%:
	$(COMPOSE) logs -f $*

ps:
	$(COMPOSE) ps

test:
	$(GRADLE) test
