# AWS ElastiCache Setup Guide for DukaanLocker

## Overview

This guide covers setting up AWS ElastiCache for Redis to use with DukaanLocker's secure document view token caching in production.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        AWS Cloud                                │
│  ┌─────────────────┐      ┌─────────────────────────────────┐  │
│  │  DukaanLocker   │      │      ElastiCache for Redis      │  │
│  │  Backend        │◄────►│  (Private Subnet, TLS Enabled)  │  │
│  │  (ECS/EC2)      │      │  - In-transit encryption        │  │
│  └─────────────────┘      │  - At-rest encryption           │  │
│           │                │  - AUTH token authentication    │  │
│           │                └─────────────────────────────────┘  │
│           ▼                                                      │
│  ┌─────────────────┐                                            │
│  │  Amazon S3      │                                            │
│  │  (Documents)    │                                            │
│  └─────────────────┘                                            │
└─────────────────────────────────────────────────────────────────┘
```

## Prerequisites

1. AWS Account with appropriate IAM permissions
2. VPC with private subnets
3. Security groups configured
4. DukaanLocker backend deployed (ECS, EC2, or EKS)

## Step 1: Create ElastiCache Subnet Group

```bash
aws elasticache create-cache-subnet-group \
  --cache-subnet-group-name dukaanlocker-redis-subnet-group \
  --cache-subnet-group-description "Subnet group for DukaanLocker Redis" \
  --subnet-ids subnet-xxxxx subnet-yyyyy
```

## Step 2: Create Security Group

```bash
# Create security group for ElastiCache
aws ec2 create-security-group \
  --group-name dukaanlocker-redis-sg \
  --description "Security group for DukaanLocker ElastiCache" \
  --vpc-id vpc-xxxxx

# Allow inbound Redis (port 6379) from backend security group
aws ec2 authorize-security-group-ingress \
  --group-id sg-redis-xxxxx \
  --protocol tcp \
  --port 6379 \
  --source-group sg-backend-xxxxx
```

## Step 3: Create ElastiCache Cluster

### Option A: ElastiCache Serverless (Recommended)

```bash
aws elasticache create-serverless-cache \
  --serverless-cache-name dukaanlocker-redis \
  --engine redis \
  --cacheUsageLimits '{"DataStorage":{"Minimum":1,"Maximum":10,"Unit":"GB"},"ECPUPerSecond":{"Minimum":1000,"Maximum":15000}}' \
  --security-group-ids sg-redis-xxxxx \
  --subnet-ids subnet-xxxxx subnet-yyyyy
```

### Option B: Provisioned Cluster (For Steady Workloads)

```bash
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

## Step 4: Configure Authentication Token

```bash
# Generate a secure auth token (save this!)
AUTH_TOKEN=$(openssl rand -base64 32)

# Modify cluster to set auth token
aws elasticache modify-cache-cluster \
  --cache-cluster-id dukaanlocker-redis \
  --auth-token $AUTH_TOKEN
```

## Step 5: Get Connection Endpoint

```bash
# Get the endpoint
aws elasticache describe-cache-clusters \
  --cache-cluster-id dukaanlocker-redis \
  --query 'CacheClusters[0].CacheNodes[0].Endpoint.Address' \
  --output text
```

## Step 6: Configure DukaanLocker Backend

### Environment Variables

Add these to your deployment environment:

```bash
# Redis Configuration (AWS ElastiCache)
REDIS_HOST=your-cluster.xxxxx.cache.amazonaws.com
REDIS_PORT=6379
REDIS_PASSWORD=your-auth-token
REDIS_SSL_ENABLED=true
```

### For ECS Task Definition

```json
{
  "containerDefinitions": [
    {
      "name": "dukaanlocker-backend",
      "environment": [
        {"name": "REDIS_HOST", "value": "your-cluster.xxxxx.cache.amazonaws.com"},
        {"name": "REDIS_PORT", "value": "6379"},
        {"name": "REDIS_PASSWORD", "value": "your-auth-token"},
        {"name": "REDIS_SSL_ENABLED", "value": "true"}
      ]
    }
  ]
}
```

### For Docker Compose (Testing)

```yaml
version: '3.8'
services:
  backend:
    image: dukaanlocker-backend
    environment:
      - REDIS_HOST=your-cluster.xxxxx.cache.amazonaws.com
      - REDIS_PORT=6379
      - REDIS_PASSWORD=your-auth-token
      - REDIS_SSL_ENABLED=true
```

## Security Best Practices

### 1. Network Security
- ✅ Deploy ElastiCache in private subnets only
- ✅ Use security groups to restrict access
- ✅ Never expose ElastiCache to public internet

### 2. Encryption
- ✅ Enable in-transit encryption (TLS)
- ✅ Enable at-rest encryption (AWS KMS)
- ✅ Use AUTH token for authentication

### 3. Access Control
- ✅ Use IAM roles for AWS service access
- ✅ Rotate AUTH tokens periodically
- ✅ Monitor access with CloudWatch

### 4. Monitoring

Set up CloudWatch alarms:

```bash
# CPU Utilization
aws cloudwatch put-metric-alarm \
  --alarm-name "ElastiCache-CPU-High" \
  --metric-name CPUUtilization \
  --namespace AWS/ElastiCache \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --dimensions "Name=CacheClusterId,Value=dukaanlocker-redis"

# Memory Usage
aws cloudwatch put-metric-alarm \
  --alarm-name "ElastiCache-Memory-High" \
  --metric-name DatabaseMemoryUsagePercentage \
  --namespace AWS/ElastiCache \
  --statistic Average \
  --period 300 \
  --threshold 80 \
  --comparison-operator GreaterThanThreshold \
  --dimensions "Name=CacheClusterId,Value=dukaanlocker-redis"
```

## Cost Optimization

### ElastiCache Serverless
- Pay per request and storage
- Ideal for variable workloads
- No capacity planning needed

### Provisioned Clusters
- Reserved Instances for 1-3 years (up to 55% savings)
- Right-size based on actual usage
- Use read replicas for read-heavy workloads

## Troubleshooting

### Connection Issues

```bash
# Test connectivity from EC2/ECS
redis-cli -h your-cluster.xxxxx.cache.amazonaws.com -p 6379 -a your-auth-token --tls

# Check security group rules
aws ec2 describe-security-groups --group-ids sg-redis-xxxxx

# Check ElastiCache status
aws elasticache describe-cache-clusters --cache-cluster-id dukaanlocker-redis
```

### Common Errors

| Error | Cause | Solution |
|-------|-------|----------|
| `Connection refused` | Security group blocking | Update inbound rules |
| `NOAUTH Authentication required` | Missing auth token | Set REDIS_PASSWORD |
| `SSL required` | TLS not enabled | Set REDIS_SSL_ENABLED=true |
| `Connection timed out` | Network/VPC issue | Check VPC routing |

## Backup & Recovery

### Automated Backups

```bash
# Enable automated backups
aws elasticache modify-cache-cluster \
  --cache-cluster-id dukaanlocker-redis \
  --snapshot-retention-limit 7
```

### Manual Snapshot

```bash
# Create manual snapshot
aws elasticache create-snapshot \
  --cache-cluster-id dukaanlocker-redis \
  --snapshot-name dukaanlocker-redis-backup-$(date +%Y%m%d)
```

## Scaling

### Vertical Scaling (Provisioned)

```bash
# Scale to larger instance
aws elasticache modify-cache-cluster \
  --cache-cluster-id dukaanlocker-redis \
  --cache-node-type cache.m7g.xlarge
```

### Horizontal Scaling (Cluster Mode)

```bash
# Add read replicas
aws elasticache modify-cache-cluster \
  --cache-cluster-id dukaanlocker-redis \
  --num-cache-nodes 3
```

## Integration with DukaanLocker

The secure document view feature uses Redis to store one-time view tokens:

1. User requests to view a document
2. Backend generates a UUID token
3. Token stored in Redis with 15-second TTL
4. User streams document using the token
5. Token deleted immediately after use

This ensures:
- ✅ No S3 URLs exposed to client
- ✅ One-time use tokens
- ✅ Short expiration window
- ✅ Backend controls all access

## Health Check Endpoints

The application exposes the following health check endpoints (publicly accessible for load balancers):

| Endpoint | Description |
|----------|-------------|
| `GET /api/health` | Basic application health check |
| `GET /api/health/redis` | Redis connectivity check |
| `GET /api/health/detailed` | Detailed health with all dependencies |

**Note:** These endpoints are intentionally publicly accessible for load balancer health checks. The `/api/health/detailed` endpoint may expose internal configuration - consider restricting it to internal networks in production.

## Next Steps

1. Deploy ElastiCache cluster
2. Configure environment variables
3. Test secure document view feature
4. Set up monitoring and alerts
5. Implement backup strategy

## Support

For issues with this setup, check:
- AWS ElastiCache documentation
- Spring Boot Redis configuration
- DukaanLocker application logs
