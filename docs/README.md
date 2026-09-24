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

Swagger UI is available after running the backend. The backend listens on port `8081`.

For local development, enable the dev-tools gate before starting the server:

```bash
EXPOSE_DEVTOOLS=true ./mvnw spring-boot:run
```

Then open:

```
http://localhost:8081/swagger-ui/index.html
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

**CI** (`.github/workflows/ci.yml`) — on every PR / push to `main`:
- Backend: compile, test, package (Maven, JDK 21), upload JAR
- Android: unit tests + assemble debug APK
- Docker: multi-stage image build (no push) with layer cache
- OWASP: dependency-check report (non-blocking; set `NVD_API_KEY` secret to lift rate limits)

**CD** (`.github/workflows/cd.yml`) — on push to `main` (or manual dispatch):
- Push backend image to GitHub Container Registry (`ghcr.io`)
- Optional deploy when `ENABLE_DEPLOY=true` (SSH to EC2/VM or AWS ECS)
- Assemble + upload Android APK artifact

Workflow configuration:

```
.github/workflows/ci.yml
.github/workflows/cd.yml
```

---

# Deployment

Production deployment supports:

- Docker (image published to GHCR by CD)
- AWS EC2 (+ optional SSH deploy job)
- AWS ECS Fargate (optional; configure `DEPLOY_TARGET=ecs`)
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



---

# Author

**Anjali Kashyap**

DukaanLocker – Business Compliance Made Simple.