package lambdalabs.filestorage.config;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import jakarta.servlet.MultipartConfigElement;

@Configuration
public class MultipartConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();

        factory.setMaxFileSize(DataSize.ofGigabytes(1));

        factory.setMaxRequestSize(DataSize.ofGigabytes(1));
        
        // Set file size threshold to 0 (don't write to disk until necessary)
        // This forces streaming for all files
        factory.setFileSizeThreshold(DataSize.ofBytes(0));
        
        // Set location for temporary files (if needed)
        factory.setLocation(System.getProperty("java.io.tmpdir"));
        
        return factory.createMultipartConfig();
    }
}
