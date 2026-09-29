# Expects a prebuilt jar: run ./gradlew bootJar first (CI does this in the release workflow).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY build/libs/app.jar app.jar
ENV DB_PATH=/app/data/october-discussion.db
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
