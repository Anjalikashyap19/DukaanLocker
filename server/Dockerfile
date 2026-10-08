FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY server/pom.xml .
RUN mvn dependency:go-offline -B
COPY server/src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage: pinned JRE image, non-root user.
FROM eclipse-temurin:25-jre-noble
# tesseract-ocr(+eng): OCR for expiry-date extraction at upload. tess4j loads the
#   system libtesseract.so and findTessDataPath() resolves
#   /usr/share/tesseract-ocr/5/tessdata, so no TESSDATA_PATH is needed.
# tzdata: the notification schedulers and their crons are written for IST, so the
#   container needs a real zone database rather than defaulting to UTC.
# fonts-liberation(+fontconfig): the generated certificates (FSSAI/GST/MSME) are set
#   in a serif face registered as "Times New Roman"; Liberation Serif is the
#   metric-compatible stand-in so production PDFs match what dev boxes render.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-eng \
        tzdata \
        fonts-liberation \
        fontconfig \
    && rm -rf /var/lib/apt/lists/*
RUN useradd -m -u 1001 appuser
WORKDIR /app
RUN mkdir -p /app/Documents && chown -R appuser:appuser /app/Documents
COPY --from=build --chown=appuser:appuser /app/target/*.jar app.jar
USER appuser
EXPOSE 8081
CMD ["java", "-jar", "app.jar"]
