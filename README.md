# File Storage REST API (Spring Boot + MongoDB)

A minimal REST API to manage file metadata: filename, size, visibility (public/private), and tags.

## Prerequisites
- Java 21
- Maven 3.9+
- Docker (optional, for MongoDB)

## Quick start

Start MongoDB via Docker:

```bash
docker compose up -d
```

Run the application:

```bash
./mvnw spring-boot:run
```

The API is available at `http://localhost:8080`.

## Endpoints

- `GET /api/files` — list all file metadata
- `POST /api/files` — create new file metadata

### Sample request

```bash
curl -X POST http://localhost:8080/api/files \
  -H 'Content-Type: application/json' \
  -d '{
    "filename": "doc.txt",
    "size": 12345,
    "visibility": "PUBLIC",
    "tags": ["docs", "text"]
  }'
```

### Environment

- `SPRING_DATA_MONGODB_URI` (default: `mongodb://localhost:27017/file_storage`)
- `SERVER_PORT` (default: `8080`)

## Testing

Use the built-in test profile (uses `file_storage_test` db):

```bash
SPRING_PROFILES_ACTIVE=test ./mvnw test
```
