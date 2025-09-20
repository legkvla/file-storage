# File Storage API Endpoints

This document describes the REST API endpoints for file upload, download, and management using MongoDB GridFS.

## File Operations

### Upload File
- **POST** `/api/files/upload`
- **Content-Type**: `multipart/form-data`
- **Parameters**:
  - `file` (required): The file to upload
  - `visibility` (optional): `PRIVATE` or `PUBLIC` (default: `PRIVATE`)
  - `tags` (optional): Comma-separated list of tags
- **Response**: FileMetadata object with generated ID and GridFS reference

### Download File
- **GET** `/api/files/{id}/download`
- **Response**: File stream with appropriate headers
- **Headers**: Content-Type, Content-Disposition, Content-Length

### Get File Metadata
- **GET** `/api/files/{id}`
- **Response**: FileMetadata object

### List Files
- **GET** `/api/files`
- **Query Parameters**:
  - `visibility` (optional): Filter by visibility (`PRIVATE` or `PUBLIC`)
  - `tag` (optional): Filter by tag
- **Response**: List of FileMetadata objects

### Update File Metadata
- **PUT** `/api/files/{id}`
- **Body**: FileMetadata object (only visibility and tags can be updated)
- **Response**: Updated FileMetadata object

### Delete File
- **DELETE** `/api/files/{id}`
- **Response**: 204 No Content
- **Note**: Deletes both the file from GridFS and its metadata

## Metadata Operations (Legacy)

### List All Metadata
- **GET** `/api/metadata`
- **Response**: List of all FileMetadata objects

### Create Metadata Only
- **POST** `/api/metadata`
- **Body**: FileMetadata object
- **Response**: Created FileMetadata object
- **Note**: This creates metadata without uploading a file

## File Metadata Structure

```json
{
  "id": "string",
  "filename": "string",
  "size": "number",
  "visibility": "PRIVATE" | "PUBLIC",
  "tags": ["string"],
  "gridFsId": "ObjectId"
}
```

## Configuration

- Maximum file size: 100MB
- MongoDB GridFS bucket: `fs` (default)
- File metadata collection: `file_metadata`

## Example Usage

### Upload a file:
```bash
curl -X POST http://localhost:8080/api/files/upload \
  -F "file=@example.pdf" \
  -F "visibility=PUBLIC" \
  -F "tags=document,pdf"
```

### Download a file:
```bash
curl -X GET http://localhost:8080/api/files/{id}/download \
  -o downloaded_file.pdf
```

### List public files:
```bash
curl -X GET "http://localhost:8080/api/files?visibility=PUBLIC"
```
