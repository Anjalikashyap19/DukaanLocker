# DukaanLocker Deployment Environment Guide

## Overview

This document lists all required environment variables for deploying DukaanLocker to production.

## Environment Variables

### Required Variables

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profile | `prod` | `dev` |
| `AWS_REGION` | AWS region for services | `us-east-1` | `us-east-1` |
| `AWS_BUCKET_NAME` | S3 bucket name | `dukaanlocker-documents-prod` | `dukaanlocker-documents-local` |
| `JWT_SECRET` | Secret key for JWT tokens (min 32 chars) | `your-super-secret-key-here...` | None |
| `JWT_EXPIRATION_MS` | JWT token expiration in milliseconds | `86400000` (24 hours) | `86400000` |
| `EXTERNAL_URL` | Public URL for the application | `https://api.dukaanlocker.com` | `http://localhost:8081` |
| `REDIS_HOST` | Redis/ElastiCache endpoint | `your-cluster.xxxxx.cache.amazonaws.com` | `localhost` |
| `REDIS_PORT` | Redis port | `6379` | `6379` |
| `REDIS_PASSWORD` | Redis authentication token | `your-auth-token` | None |
| `REDIS_SSL_ENABLED` | Enable SSL/TLS for Redis | `true` | `false` |

### Optional Variables

| Variable | Description | Example | Default |
|----------|-------------|---------|---------|
| `OLA_MAPS_API_KEY` | Ola Maps API key | `your-api-key` | None |
| `OLA_MAPS_BASE_URL` | Ola Maps API base URL | `https://api.olamaps.io` | `https://api.olamaps.io` |
| `SERVER_PORT` | Application port | `8081` | `8081` |

## AWS Services Configuration

### Amazon S3

```bash
# Create S3 bucket
aws s3 mb s3://dukaanlocker-documents-prod --region us-east-1

# Block public access
aws s3api put-public-access-block \
  --bucket dukaanlocker-documents-prod \
  --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# Enable server-side encryption
aws s3api put-bucket-encryption \
  --bucket dukaanlocker-documents-prod \
  --server-side-encryption-configuration '{
    "Rules": [
      {
        "ApplyServerSideEncryptionByDefault": {
          "SSEAlgorithm": "aws:kms"
        }
      }
    ]
  }'
```

### AWS ElastiCache (Redis)

```bash
# Create ElastiCache cluster
aws elasticache create-cache-cluster \
  --cache-cluster-id dukaanlocker-redis \
  --cache-node-type cache.m7g.large \
  --engine redis \
  --engine-version 7.0 \
  --num-cache-nodes 1 \
  --cache-subnet-group-name dukaanlocker-redis-subnet-group \
  --security-group-ids sg-redis-xxxxx \
  --at-rest-encryption-enabled \
  --transit-encryption-enabled \
  --auth-token $(openssl rand -base64 32)
```

## Deployment Platforms

### AWS ECS (Fargate)

Create `task-definition.json`:

```json
{
  "family": "dukaanlocker-backend",
  "networkMode": "awsvpc",
  "requiresCompatibilities": ["FARGATE"],
  "cpu": "512",
  "memory": "1024",
  "containerDefinitions": [
    {
      "name": "dukaanlocker-backend",
      "image": "your-account.dkr.ecr.us-east-1.amazonaws.com/dukaanlocker-backend:latest",
      "portMappings": [
        {
          "containerPort": 8081,
          "protocol": "tcp"
        }
      ],
      "environment": [
        {"name": "SPRING_PROFILES_ACTIVE", "value": "prod"},
        {"name": "AWS_REGION", "value": "us-east-1"},
        {"name": "AWS_BUCKET_NAME", "value": "dukaanlocker-documents-prod"},
        {"name": "EXTERNAL_URL", "value": "https://api.dukaanlocker.com"},
        {"name": "REDIS_HOST", "value": "your-cluster.xxxxx.cache.amazonaws.com"},
        {"name": "REDIS_PORT", "value": "6379"},
        {"name": "REDIS_SSL_ENABLED", "value": "true"}
      ],
      "secrets": [
        {
          "name": "JWT_SECRET",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxxxx:secret:dukaanlocker/jwt-secret"
        },
        {
          "name": "REDIS_PASSWORD",
          "valueFrom": "arn:aws:secretsmanager:us-east-1:xxxxx:secret:dukaanlocker/redis-auth-token"
        }
      ],
      "logConfiguration": {
        "logDriver": "awslogs",
        "options": {
          "awslogs-group": "/ecs/dukaanlocker-backend",
          "awslogs-region": "us-east-1",
          "awslogs-stream-prefix": "ecs"
        }
      }
    }
  ]
}
```

### Docker Compose (Development)

```yaml
version: '3.8'

services:
  backend:
    build:
      context: .
      dockerfile: server/Dockerfile
    ports:
      - "8081:8081"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - AWS_REGION=us-east-1
      - AWS_BUCKET_NAME=dukaanlocker-documents-local
      - JWT_SECRET=dk-dev-default-secret-minimum-32-characters-long-for-hs256
      - JWT_EXPIRATION_MS=86400000
      - EXTERNAL_URL=http://localhost:8081
      - REDIS_HOST=redis
      - REDIS_PORT=6379
      - REDIS_SSL_ENABLED=false
    depends_on:
      - redis
    networks:
      - dukaan-net

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data
    command: redis-server --appendonly yes
    networks:
      - dukaan-net

volumes:
  redis-data:

networks:
  dukaan-net:
    driver: bridge
```

### Kubernetes

Create `deployment.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dukaanlocker-backend
spec:
  replicas: 2
  selector:
    matchLabels:
      app: dukaanlocker-backend
  template:
    metadata:
      labels:
        app: dukaanlocker-backend
    spec:
      containers:
      - name: backend
        image: your-account.dkr.ecr.us-east-1.amazonaws.com/dukaanlocker-backend:latest
        ports:
        - containerPort: 8081
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: AWS_REGION
          value: "us-east-1"
        - name: AWS_BUCKET_NAME
          valueFrom:
            secretKeyRef:
              name: dukaanlocker-secrets
              key: aws-bucket-name
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: dukaanlocker-secrets
              key: jwt-secret
        - name: REDIS_HOST
          valueFrom:
            configMapKeyRef:
              name: dukaanlocker-config
              key: redis-host
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: dukaanlocker-secrets
              key: redis-password
        - name: REDIS_SSL_ENABLED
          value: "true"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1024Mi"
            cpu: "500m"
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 30
```

## Security Best Practices

### 1. Secrets Management

**AWS Secrets Manager:**

```bash
# Store JWT secret
aws secretsmanager create-secret \
  --name dukaanlocker/jwt-secret \
  --secret-string "your-super-secret-jwt-key-minimum-32-characters-long"

# Store Redis auth token
aws secretsmanager create-secret \
  --name dukaanlocker/redis-auth-token \
  --secret-string "your-redis-auth-token"
```

### 2. IAM Roles

Create IAM role for ECS task:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject"
      ],
      "Resource": "arn:aws:s3:::dukaanlocker-documents-prod/*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "secretsmanager:GetSecretValue"
      ],
      "Resource": [
        "arn:aws:secretsmanager:us-east-1:xxxxx:secret:dukaanlocker/*"
      ]
    }
  ]
}
```

### 3. VPC Configuration

```
┌─────────────────────────────────────────────────────────────┐
│                        VPC (10.0.0.0/16)                    │
├─────────────────────────────────────────────────────────────┤
│  Public Subnets                                             │
│  ├─ 10.0.1.0/24 (AZ-1)                                     │
│  └─ 10.0.2.0/24 (AZ-2)                                     │
│                                                             │
│  Private Subnets                                            │
│  ├─ 10.0.3.0/24 (AZ-1) ← ECS Tasks                        │
│  └─ 10.0.4.0/24 (AZ-2) ← ElastiCache                      │
└─────────────────────────────────────────────────────────────┘
```

## Monitoring

### CloudWatch Alarms

```bash
# High CPU alarm
aws cloudwatch put-metric-alarm \
  --alarm-name "DukaanLocker-High-CPU" \
  --metric-name CPUUtilization \
  --namespace AWS/ECS \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --dimensions "Name=ClusterName,Value=dukaanlocker-cluster"

# ElastiCache memory alarm
aws cloudwatch put-metric-alarm \
  --alarm-name "DukaanLocker-Redis-Memory" \
  --metric-name DatabaseMemoryUsagePercentage \
  --namespace AWS/ElastiCache \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --dimensions "Name=CacheClusterId,Value=dukaanlocker-redis"
```

### Health Check Endpoint

The application exposes a health check endpoint at:

```
GET /actuator/health
```

Response:

```json
{
  "status": "UP",
  "components": {
    "redis": {
      "status": "UP"
    },
    "db": {
      "status": "UP"
    }
  }
}
```

## Deployment Checklist

- [ ] AWS S3 bucket created with proper permissions
- [ ] AWS ElastiCache cluster created with TLS enabled
- [ ] JWT_SECRET stored in AWS Secrets Manager
- [ ] REDIS_PASSWORD stored in AWS Secrets Manager
- [ ] IAM roles configured with least privilege
- [ ] VPC with private subnets configured
- [ ] Security groups allow traffic between services
- [ ] CloudWatch alarms configured
- [ ] Health check endpoint accessible
- [ ] Application logs configured

## Troubleshooting

### Common Issues

1. **Connection refused to Redis**
   - Check security group rules
   - Verify ElastiCache is in private subnet
   - Ensure REDIS_SSL_ENABLED=true for ElastiCache

2. **S3 Access Denied**
   - Verify IAM role has S3 permissions
   - Check bucket policy

3. **JWT Token Invalid**
   - Ensure JWT_SECRET is at least 32 characters
   - Check token expiration settings

### Logs

View application logs:

```bash
# ECS
aws logs get-log-events \
  --log-group-name /ecs/dukaanlocker-backend \
  --log-stream-name ecs/dukaanlocker-backend/xxxxx

# Docker
docker logs dukaanlocker-backend
```

## Cost Estimation

| Service | Configuration | Monthly Cost (Approx) |
|---------|---------------|----------------------|
| ECS Fargate | 2 tasks, 512 CPU, 1GB RAM | $30-50 |
| ElastiCache | cache.m7g.large, 1 node | $100-150 |
| S3 | 10GB storage | $1-2 |
| Secrets Manager | 2 secrets | $1-2 |
| CloudWatch | Alarms + Logs | $10-20 |
| **Total** | | **$150-230** |

## Next Steps

1. Set up AWS infrastructure
2. Configure environment variables
3. Deploy application
4. Test secure document view feature
5. Set up monitoring and alerts
