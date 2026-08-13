FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY server/pom.xml .
RUN mvn dependency:go-offline -B
COPY server/src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage: pinned JRE image, non-root user. Playwright browsers are NOT
# needed at runtime (no com.microsoft.playwright code paths), so the heavy
# playwright base image is intentionally not used.
FROM eclipse-temurin:21-jre-noble
RUN useradd -m -u 1001 appuser
WORKDIR /app
COPY --from=build --chown=appuser:appuser /app/target/*.jar app.jar
USER appuser
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
