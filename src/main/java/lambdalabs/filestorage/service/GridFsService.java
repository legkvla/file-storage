package lambdalabs.filestorage.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsOperations;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
public class GridFsService {

    @Autowired
    private GridFsOperations gridFsOperations;

    /**
     * Store a file in GridFS using streaming with custom parameters
     * This method streams the InputStream directly without loading it into memory
     */
    public ObjectId storeFileStreaming(InputStream inputStream, String filename, String contentType) throws IOException {
        return gridFsOperations.store(inputStream, filename, contentType);
    }

    /**
     * Retrieve a file from GridFS by ObjectId
     */
    public GridFsResource getFile(ObjectId objectId) {
        GridFSFile gridFSFile = gridFsOperations.findOne(Query.query(Criteria.where("_id").is(objectId)));
        return gridFSFile != null ? gridFsOperations.getResource(gridFSFile) : null;
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
        gridFsOperations.delete(Query.query(Criteria.where("_id").is(objectId)));
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
}
