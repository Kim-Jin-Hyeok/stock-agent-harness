FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew dependencies --no-daemon

COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring

COPY --from=builder --chown=spring:spring /workspace/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
