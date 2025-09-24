# File Storage REST API (Spring Boot + MongoDB/GridFS)

Lightweight file storage service backed by MongoDB GridFS. Supports streaming uploads/downloads, deduplication by MD5, per-user ownership, visibility (public/private), tags, pagination/sorting, and file metadata updates.

This architecture enables efficient uploading and downloading of large files by streaming data directly to and from MongoDB GridFS, minimizing memory usage and supporting files of virtually any size.

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (optional, for MongoDB and running app from docker image)

## Restrictions

- The `UserId` header must be included with each request. The service is intended to be used behind an API gateway.
- The service does not currently support resumable uploads.
- The API Gateway in front of this service **must** support sticky (session-affinity) sessions. This is required because the service uses in-memory per-user locks to ensure safe concurrent file operations. For more details, see the comments in [FileController.java](src/main/java/lambdalabs/filestorage/controller/FileController.java).

## Quick start

Start MongoDB via Docker:

```bash
docker compose up -d mongo
```

Run the application:

```bash
./mvnw spring-boot:run
```

OpenAPI/Swagger UI: `http://localhost:8080/swagger-ui.html` (or `http://localhost:8080/swagger-ui/index.html`).

OpenAPI JSON: `http://localhost:8080/v3/api-docs`.

## Run with Docker

You can run the application and MongoDB with Docker Compose (builds the image locally):

```bash
docker compose build && docker compose up -d
```

This will start:
- `file-storage` app on `http://localhost:8080`
- `mongo` database on `localhost:27018`

To view logs:

```bash
docker compose logs -f file-storage
```

### Environment

- `SPRING_DATA_MONGODB_URI` (default: `mongodb://localhost:27017/file_storage`)
- `SERVER_PORT` (default: `8080`)

## Testing

Use the built-in test profile (uses `file_storage_test` db):

```bash
SPRING_PROFILES_ACTIVE=test ./mvnw test
```

## Roadmap

* [ ] Get rid of session-affinity requirement by using mongo findAndModify for locks
* [ ] Better test coverage
* [ ] Improved error handling and API responses.
* [ ] Collect errors information in mongo collection to analyze and react
* [ ] Adding production profiling using Spring Micrometer (or may be adopt Quarkus?)
* [ ] Resumable uploads
* [ ] Sharing links for private files
* [ ] Add own user authentication and authorization (OAuth2/JWT)
* [ ] S3 or cloud storage backend support
* [ ] File versioning support
* [ ] Optimize file copying/forking for minimal storage usage
* [ ] File/folder organization (virtual folders)
* [ ] Quota/limits per user/org and multi-tenancy support (including user management)
* [ ] Admin api and metrics
* [ ] CLI or SDK for file operations
* [ ] Scheduled cleanup features

_Contributions and suggestions welcome!_

