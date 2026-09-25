FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY server/pom.xml .
RUN mvn dependency:go-offline -B
COPY server/src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage: pinned JRE image, non-root user.
FROM eclipse-temurin:25-jre-noble
RUN useradd -m -u 1001 appuser
WORKDIR /app
RUN mkdir -p /app/Documents && chown -R appuser:appuser /app/Documents
COPY --from=build --chown=appuser:appuser /app/target/*.jar app.jar
USER appuser
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
