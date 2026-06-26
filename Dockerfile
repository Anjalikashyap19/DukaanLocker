FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libnss3 libnspr4 libatk1.0-0t64 libatk-bridge2.0-0t64 \
        libcups2t64 libdrm2 libdbus-1-3 libxkbcommon0 \
        libxcomposite1 libxdamage1 libxfixes3 libxrandr2 \
        libgbm1 libpango-1.0-0 libcairo2 libasound2t64 \
        libatspi2.0-0t64 && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
