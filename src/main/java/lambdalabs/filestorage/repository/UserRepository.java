package lambdalabs.filestorage.repository;

import lambdalabs.filestorage.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class UserRepository {

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME = "users";

    /**
     * Save a user
     */
    public User save(User user) {
        return mongoTemplate.save(user, COLLECTION_NAME);
    }

    /**
     * Find user by ID
     */
    public Optional<User> findById(String id) {
        User user = mongoTemplate.findById(id, User.class, COLLECTION_NAME);
        return Optional.ofNullable(user);
    }

    /**
     * Find user by identity (unique key)
     */
    public Optional<User> findByIdentity(String identity) {
        Query query = new Query(Criteria.where("identity").is(identity));
        User user = mongoTemplate.findOne(query, User.class, COLLECTION_NAME);
        return Optional.ofNullable(user);
    }

    /**
     * Find all users
     */
    public List<User> findAll() {
        return mongoTemplate.findAll(User.class, COLLECTION_NAME);
    }


    /**
     * Delete user by ID
     */
    public void deleteById(String id) {
        Query query = new Query(Criteria.where("id").is(id));
        mongoTemplate.remove(query, User.class, COLLECTION_NAME);
    }

    /**
     * Check if user exists by identity
     */
    public boolean existsByIdentity(String identity) {
        Query query = new Query(Criteria.where("identity").is(identity));
        return mongoTemplate.exists(query, User.class, COLLECTION_NAME);
    }

    /**
     * Count all users
     */
    public long count() {
        return mongoTemplate.count(new Query(), User.class, COLLECTION_NAME);
    }
}
