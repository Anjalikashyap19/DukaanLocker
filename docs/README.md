# DukaanLocker

DukaanLocker is a modern business compliance and document management platform that helps shop owners securely upload, organize, validate, and manage statutory and regulatory documents from one centralized system.

The platform consists of a Spring Boot REST API backend and a native Android application, with secure cloud storage, OCR-based document processing, and role-based business management.

---

# Features

- Secure document upload and storage
- Business profile management
- Multi-shop management
- Owner and Manager roles
- JWT Authentication
- Document validation workflow
- AWS S3 document storage
- AWS Textract OCR integration
- Swagger/OpenAPI documentation
- RESTful APIs
- Android application built with Jetpack Compose

---

# Supported Documents

DukaanLocker currently supports:

- GST Registration Certificate
- PAN
- IEC Certificate
- MSME / Udyam Registration
- Trade License
- Shop & Establishment License
- FSSAI Food License
- Labour License
- Fire Safety Certificate
- Pollution Control Certificate
- Property Tax
- Professional Tax
- Trademark Certificate
- Drug License
- Shop Insurance

More compliance documents can be added easily through the modular backend architecture.

---

# Project Structure

```
DukaanLocker/
│
├── android/                    # Native Android application
│   ├── app/
│   ├── gradle/
│   ├── build.gradle.kts
│   └── settings.gradle.kts
│
├── server/                     # Spring Boot Backend
│   ├── src/
│   ├── pom.xml
│   ├── Dockerfile
│   ├── mvnw
│   ├── mvnw.cmd
│   └── start.sh
│
├── docs/                       # Documentation
│
├── .github/                    # GitHub Actions workflows
│
├── .gitignore
└── README.md
```

---

# Technology Stack

## Backend

- Java 21
- Spring Boot
- Spring Security
- Spring Data JPA
- JWT Authentication
- Maven
- REST APIs
- Swagger / OpenAPI

---

## Android

- Kotlin
- Jetpack Compose
- Material 3
- Android Studio
- Retrofit
- Gradle Kotlin DSL

---

## Database

- H2 Database (Development)
- PostgreSQL / MySQL (Production Ready)

---

## Cloud Services

- AWS EC2
- AWS S3
- AWS Textract
- Docker

---

# API Documentation

Swagger UI is available after running the backend.

```
http://localhost:8080/swagger-ui/index.html
```

---

# Getting Started

## Clone Repository

```bash
git clone https://github.com/Anjalikashyap19/DukaanLocker.git
cd DukaanLocker
```

---

# Run Backend

```bash
cd server

chmod +x mvnw

./mvnw spring-boot:run
```

Windows

```cmd
cd server
mvnw.cmd spring-boot:run
```

---

# Run Android App

Open the `android` folder in Android Studio and run the application.

---

# Build Backend

```bash
cd server
./mvnw clean package
```

Generated JAR:

```
server/target/
```

---

# Environment Configuration

Configure the following environment variables before running in production.

| Variable | Description |
|----------|-------------|
| DB_URL | Database URL |
| DB_USERNAME | Database username |
| DB_PASSWORD | Database password |
| JWT_SECRET | JWT signing key |
| AWS_ACCESS_KEY | AWS Access Key |
| AWS_SECRET_KEY | AWS Secret Key |
| AWS_REGION | AWS Region |
| S3_BUCKET_NAME | AWS S3 Bucket |
| TEXTRACT_REGION | AWS Textract Region |

---

# CI/CD

GitHub Actions automatically:

- Builds the Spring Boot application
- Runs tests
- Packages the application
- Uploads the generated artifact

Workflow configuration is available in:

```
.github/workflows/ci.yml
```

---

# Deployment

Production deployment supports:

- Docker
- AWS EC2
- Nginx Reverse Proxy
- Environment-based configuration

---

# Future Roadmap

- OCR document auto-classification
- AI-powered compliance reminders
- Push notifications
- Multi-language support
- Document expiry alerts
- Web dashboard
- Admin Portal

---

# License

This project is licensed under the MIT License.

---

# Author

**Anjali Kashyap**

DukaanLocker – Business Compliance Made Simple.