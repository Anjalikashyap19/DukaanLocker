# DukaanLocker

DukaanLocker is a shop compliance document management platform that helps businesses securely upload, organize, validate, and manage statutory and regulatory documents from one place.

The project contains:

- Spring Boot REST API backend
- Native Android frontend
- Swagger/OpenAPI documentation
- AWS EC2 deployment support
- AWS S3 document storage integration
- AWS Textract-based document processing and OCR

---

## Supported Documents

DukaanLocker supports shop and business compliance documents such as:

- PAN
- TAN
- GST Registration Certificate
- FSSAI License
- Trade License
- MSME / Udyam Registration
- IEC Certificate
- Shop Insurance
- Other business compliance documents

---

## Project Structure

```text
DukaanLocker/
├── src/                         # Spring Boot backend source code
├── data/                        # Backend data/resources
├── Locker/                      # Supporting project resources
├── frontend/                    # Android application
│   ├── app/                     # Android app module
│   ├── gradle/                  # Gradle wrapper configuration
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   └── gradlew.bat
├── pom.xml                      # Maven backend configuration
├── Dockerfile                   # Docker configuration
├── start.sh                     # EC2 startup script
├── mvnw
├── mvnw.cmd
└── DukaanLocker.postman_collection.json




Technology Stack
Backend
Java
Spring Boot
Spring Web
Spring Data JPA
Maven
Swagger / OpenAPI
REST APIs
Android Frontend
Kotlin
Android Studio
Jetpack Compose
Gradle Kotlin DSL
Cloud and Infrastructure
AWS EC2
AWS S3
AWS Textract
Docker
Environment-based configuration