# === ЭТАП 1: Сборка приложения ===
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Копируем только дескриптор сборки для кэширования зависимостей
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Копируем исходный код и собираем JAR
COPY src ./src
RUN mvn clean package -DskipTests

# === ЭТАП 2: Минимальный образ для запуска ===
# Для чисто сетевого шлюза идеально подходит Alpine Linux (образ весит около 140 МБ)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Создаем не-root пользователя для безопасности шлюза
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Копируем собранный jar-файл из этапа сборки
COPY --from=build /app/target/Gateway-microservice-0.0.1-SNAPSHOT.jar app.jar

# Главный входной порт шлюза из твоих пропертей
EXPOSE 5555

# Запуск с поддержкой лимитов Docker и ZGC для ультра-низкого сетевого пинга
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:+UseZGC", "-jar", "app.jar"]
