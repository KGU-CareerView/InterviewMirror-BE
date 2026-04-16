COMPOSE = docker-compose
GRADLE  = ./gradlew

.PHONY: all clean fclean re test

all:
	$(COMPOSE) up --build -d

clean:
	$(COMPOSE) down

fclean:
	$(COMPOSE) down -v --rmi all

re: fclean all

test:
	$(GRADLE) test
