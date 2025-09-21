package lambdalabs.filestorage.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class GridFsService {

    private static final Logger logger = LoggerFactory.getLogger(GridFsService.class);

    @Autowired
    private GridFsOperations gridFsOperations;

    /**
     * Store a file in GridFS using streaming with custom parameters
     * This method streams the InputStream directly without loading it into memory
     */
    public ObjectId storeFileStreaming(InputStream inputStream, String filename, String contentType) throws IOException {
        logger.debug("Storing file in GridFS: filename={}, contentType={}", filename, contentType);
        try {
            ObjectId objectId = gridFsOperations.store(inputStream, filename, contentType);
            logger.info("Successfully stored file in GridFS: filename={}, objectId={}", filename, objectId);
            return objectId;
        } catch (Exception e) {
            logger.error("Failed to store file in GridFS: filename={}, contentType={}", filename, contentType, e);
            if (e instanceof IOException) {
                throw (IOException) e;
            } else {
                throw new IOException("Failed to store file in GridFS", e);
            }
        }
    }

    /**
     * Retrieve a file from GridFS by ObjectId
     */
    public GridFsResource getFile(ObjectId objectId) {
        logger.debug("Retrieving file from GridFS: objectId={}", objectId);
        GridFSFile gridFSFile = gridFsOperations.findOne(Query.query(Criteria.where("_id").is(objectId)));
        if (gridFSFile != null) {
            logger.debug("File found in GridFS: objectId={}, filename={}", objectId, gridFSFile.getFilename());
            return gridFsOperations.getResource(gridFSFile);
        } else {
            logger.warn("File not found in GridFS: objectId={}", objectId);
            return null;
        }
    }

    /**
     * Retrieve a file from GridFS by filename
     */
    public GridFsResource getFile(String filename) {
        return gridFsOperations.getResource(filename);
    }

    /**
     * Delete a file from GridFS by ObjectId
     */
    public void deleteFile(ObjectId objectId) {
        logger.debug("Deleting file from GridFS: objectId={}", objectId);
        try {
            gridFsOperations.delete(Query.query(Criteria.where("_id").is(objectId)));
            logger.info("Successfully deleted file from GridFS: objectId={}", objectId);
        } catch (Exception e) {
            logger.error("Failed to delete file from GridFS: objectId={}", objectId, e);
            throw e;
        }
    }

    /**
     * Delete a file from GridFS by filename
     */
    public void deleteFile(String filename) {
        gridFsOperations.delete(Query.query(Criteria.where("filename").is(filename)));
    }

    /**
     * Check if a file exists in GridFS by ObjectId
     */
    public boolean fileExists(ObjectId objectId) {
        GridFSFile gridFSFile = gridFsOperations.findOne(Query.query(Criteria.where("_id").is(objectId)));
        return gridFSFile != null;
    }

    /**
     * Check if a file exists in GridFS by filename
     */
    public boolean fileExists(String filename) {
        GridFSFile gridFSFile = gridFsOperations.findOne(Query.query(Criteria.where("filename").is(filename)));
        return gridFSFile != null;
    }

    /**
     * Calculate MD5 hash from a GridFS file by ObjectId
     * This method streams the file content without loading it entirely into memory
     */
    public String calculateMD5FromGridFS(ObjectId objectId) throws IOException {
        logger.debug("Calculating MD5 hash for GridFS file: objectId={}", objectId);
        
        GridFsResource resource = getFile(objectId);
        if (resource == null) {
            throw new IOException("File not found in GridFS: " + objectId);
        }
        
        try (InputStream inputStream = resource.getInputStream()) {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192]; // 8KB buffer for efficient reading
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md5.update(buffer, 0, bytesRead);
            }
            
            byte[] digest = md5.digest();
            String md5Hash = HexFormat.of().formatHex(digest);
            
            logger.info("MD5 hash calculated for GridFS file: objectId={}, md5={}", objectId, md5Hash);
            return md5Hash;
        } catch (NoSuchAlgorithmException e) {
            logger.error("MD5 algorithm not available", e);
            throw new IOException("MD5 algorithm not available", e);
        }
    }
}
