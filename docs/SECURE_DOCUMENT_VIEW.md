# Secure Document View Implementation

## Overview

This feature implements secure document viewing with one-time view tokens, ensuring that:
- Private S3 bucket (no public access)
- No S3 URL exposed to the client
- JWT-based authentication
- Document ownership/authorization check
- One-time View Token
- Token expires automatically (15 seconds)
- Token deleted after first successful use
- Backend controls all document access

## Architecture

### Backend Components

#### 1. DTOs (Data Transfer Objects)
- `ViewDocumentRequest` - Request to get a view token for a document
- `StreamDocumentRequest` - Request to stream a document using a view token
- `ViewTokenResponse` - Response containing the one-time view token

#### 2. Services
- `DocumentStreamService` - Core service handling:
  - Token generation and storage in Redis
  - Token validation and one-time use enforcement
  - Document streaming from S3
  - Access control validation

- `S3Service` - Updated with:
  - `getObject()` method for streaming documents
  - `extractObjectKeyFromFileUrl()` for parsing S3 URLs

#### 3. Controllers
- `DocumentStreamController` - REST endpoints:
  - `POST /api/documents/view` - Generate one-time view token
  - `POST /api/documents/stream` - Stream document using token

#### 4. Configuration
- `RedisConfig` - Redis configuration for token caching
- `SecurityConfig` - Updated to allow `/api/documents/**` endpoints

### Frontend Components

#### 1. API Service
- `DocumentStreamApi.kt` - Retrofit interface for:
  - `requestViewToken()` - Get one-time view token
  - `streamDocument()` - Stream document using token

#### 2. UI Components
- `DocumentViewerScreen.kt` - Document viewer with:
  - Secure document loading via view tokens
  - PDF rendering to bitmaps
  - Page navigation for multi-page documents
  - Memory-efficient bitmap handling
  - Error handling and retry functionality

## Security Flow

### 1. Request View Token
```
Frontend → POST /api/documents/view {documentId}
    ↓
Backend validates JWT
    ↓
Backend verifies user has access to document's shop
    ↓
Backend generates UUID view token
    ↓
Backend stores token in Redis with 15-second TTL
    ↓
Frontend ← ViewTokenResponse {viewToken, documentId, fileName, expiresIn}
```

### 2. Stream Document
```
Frontend → POST /api/documents/stream {viewToken}
    ↓
Backend validates JWT
    ↓
Backend retrieves token from Redis
    ↓
Backend deletes token immediately (one-time use)
    ↓
Backend validates user matches token owner
    ↓
Backend validates user has access to document
    ↓
Backend streams document from S3
    ↓
Frontend ← Binary document stream
```

## Configuration

### Redis Configuration
Add to `application.properties`:
```properties
# Redis Configuration
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
spring.data.redis.password=${REDIS_PASSWORD:}
spring.data.redis.timeout=2000ms

# Secure Document View Token Configuration
app.document.view-token.ttl-seconds=15
```

### Environment Variables
```bash
# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# AWS (existing)
AWS_REGION=us-east-1
AWS_BUCKET_NAME=dukaanlocker-documents-local
```

## API Usage

### 1. Request View Token
```http
POST /api/documents/view
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "documentId": 123
}
```

**Response:**
```json
{
  "viewToken": "550e8400-e29b-41d4-a716-446655440000",
  "documentId": 123,
  "fileName": "pan_card.pdf",
  "expiresIn": 15
}
```

### 2. Stream Document
```http
POST /api/documents/stream
Authorization: Bearer <jwt_token>
Content-Type: application/json

{
  "viewToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
- Content-Type: application/pdf (or image/jpeg, image/png)
- Body: Binary document content

## Error Handling

### Error Codes
- `VIEW_TOKEN_EXPIRED` - Token has expired or is invalid
- `VIEW_TOKEN_USED` - Token has already been used
- `DOCUMENT_NOT_FOUND` - Document not found
- `S3_OBJECT_NOT_FOUND` - Document not found in S3
- `FORBIDDEN` - User doesn't have access to the document

## Frontend Integration

### Android Usage
```kotlin
// 1. Get DocumentStreamApi
val documentStreamApi = ApiClient.getDocumentStreamApi(context)

// 2. Request view token
val tokenResponse = documentStreamApi.requestViewToken(
    ViewDocumentRequest(documentId = documentId)
)

if (tokenResponse.isSuccessful) {
    val tokenData = tokenResponse.body()
    
    // 3. Stream document
    val streamResponse = documentStreamApi.streamDocument(
        StreamDocumentRequest(viewToken = tokenData.viewToken)
    )
    
    if (streamResponse.isSuccessful) {
        val responseBody = streamResponse.body()
        // Handle document stream
    }
}
```

## Security Features

✅ **Private S3 bucket** - No public access to stored documents
✅ **No S3 URL exposure** - Client never sees S3 URLs
✅ **JWT authentication** - All requests require valid JWT
✅ **Authorization check** - User must have access to document's shop
✅ **One-time tokens** - Tokens can only be used once
✅ **Short TTL** - Tokens expire after 15 seconds
✅ **Immediate cleanup** - Tokens deleted after first use
✅ **Audit logging** - All access attempts are logged

## Deployment Notes

### Redis Setup
For production, ensure Redis is properly configured:
1. Use a managed Redis service (AWS ElastiCache, Redis Cloud, etc.)
2. Enable authentication
3. Configure appropriate memory limits
4. Set up monitoring

### S3 Bucket Configuration
Ensure the S3 bucket is configured for private access:
1. Block all public access
2. Use bucket policies to allow access only from the application
3. Enable server-side encryption
4. Enable access logging

## Testing

### Unit Tests
- Test token generation and validation
- Test one-time use enforcement
- Test TTL expiration
- Test access control

### Integration Tests
- Test full flow from token request to document streaming
- Test error scenarios (expired token, invalid token, no access)
- Test concurrent access attempts

### Security Tests
- Test token replay attacks
- Test unauthorized access attempts
- Test token expiration handling
