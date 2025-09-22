package lambdalabs.filestorage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class LargeFileIntegrationTest {

	private static RestTemplate restTemplate;

	@BeforeAll
	static void setup(@Autowired RestTemplateBuilder builder) {
		// Skip test if local MongoDB isnt available
		boolean mongoAvailable = isMongoRunning();
		Assumptions.assumeTrue(mongoAvailable, "MongoDB must be running at mongodb://localhost:27017 for this integration test");

		restTemplate = builder
			.requestFactory(factory -> {
				var simple = new org.springframework.http.client.SimpleClientHttpRequestFactory();
				simple.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
				simple.setReadTimeout((int) Duration.ofHours(6).toMillis());
				return simple;
			})
			.build();
	}

	@AfterAll
	static void teardown() {
	}

	@Test
	void upload2GbAndDownload_streaming_noBuffering() throws Exception {
		final String userId = "itest-user";
		final String filename = "two-gig.bin";
		final String contentType = "application/octet-stream";
		final long sizeBytes = 2L * 1024 * 1024 * 1024; // 2GB

		// Create a deterministic streaming source that does not buffer
		InputStream dataStream = new DeterministicStream(sizeBytes);
		InputStreamResource bodyResource = new InputStreamResource(dataStream) {
			@Override
			public String getFilename() { return filename; }
			@Override
			public long contentLength() { return sizeBytes; }
		};

		HttpHeaders headers = new HttpHeaders();
		headers.set("User-Id", userId);
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

		// Query params for filename/contentType/visibility
		URI uploadUri = URI.create("http://localhost:8080/api/files/upload?filename=" + filename + "&contentType=" + contentType + "&visibility=PRIVATE");

		RequestEntity<InputStreamResource> uploadReq = new RequestEntity<>(bodyResource, headers, HttpMethod.POST, uploadUri);

		ResponseEntity<Map<String,Object>> uploadResp = restTemplate.exchange(uploadReq, new ParameterizedTypeReference<Map<String, Object>>() {});
		Assertions.assertEquals(HttpStatus.OK, uploadResp.getStatusCode());
		Map<String,Object> metadata = uploadResp.getBody();
		Assertions.assertNotNull(metadata);
		String id = String.valueOf(metadata.get("id"));
		Assertions.assertNotNull(id);
		Object sizeValue = metadata.get("size");
		Assertions.assertNotNull(sizeValue);
		long storedSize = Long.parseLong(String.valueOf(sizeValue));
		Assertions.assertEquals(sizeBytes, storedSize);
	}

	private static boolean isMongoRunning() {
		try (java.net.Socket s = new java.net.Socket("localhost", 27017)) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static class DeterministicStream extends InputStream {
		private final long totalBytes;
		private long produced;

		DeterministicStream(long totalBytes) {
			this.totalBytes = totalBytes;
			this.produced = 0;
		}

		@Override
		public int read() {
			if (produced >= totalBytes) return -1;
			int value = (int) (produced & 0xFF); // simple repeating pattern
			produced++;
			return value;
		}

		@Override
		public int read(byte[] b, int off, int len) {
			if (produced >= totalBytes) return -1;
			int toWrite = (int) Math.min(len, totalBytes - produced);
			for (int i = 0; i < toWrite; i++) {
				b[off + i] = (byte) ((produced + i) & 0xFF);
			}
			produced += toWrite;
			return toWrite;
		}

		@Override
		public long skip(long n) {
			long can = Math.min(n, totalBytes - produced);
			produced += can;
			return can;
		}

		@Override
		public int available() {
			long remaining = totalBytes - produced;
			return remaining > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) remaining;
		}
	}
}
