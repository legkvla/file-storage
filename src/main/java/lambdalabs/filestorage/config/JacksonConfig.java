package lambdalabs.filestorage.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;

@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        
        // Create a module for custom serializers
        SimpleModule module = new SimpleModule();
        
        // Add ObjectId serializer
        module.addSerializer(ObjectId.class, new ObjectIdSerializer());
        
        mapper.registerModule(module);
        
        return mapper;
    }
    
    /**
     * Custom serializer for ObjectId to convert it to string
     */
    public static class ObjectIdSerializer extends JsonSerializer<ObjectId> {
        @Override
        public void serialize(ObjectId objectId, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) 
                throws IOException {
            if (objectId == null) {
                jsonGenerator.writeNull();
            } else {
                jsonGenerator.writeString(objectId.toString());
            }
        }
    }
}
