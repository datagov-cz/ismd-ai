FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:17-jre

WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app

COPY --from=build /workspace/target/*.jar app.jar

RUN mkdir -p /data && chown -R app:app /app /data

USER app

ENV APP_FEEDBACK_DB_PATH=/data/feedback.sqlite \
    APP_LLM_ENABLED=false \
    APP_LLM_PROVIDER=OPENAI \
    APP_LLM_MODEL=gpt-4o-mini \
    APP_LLM_API_KEY="" \
    APP_LLM_ENDPOINT_URL="" \
    APP_LLM_MAX_TOKENS=1024 \
    APP_LLM_TEMPERATURE=0.2 \
    APP_LLM_TIMEOUT=60s

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
