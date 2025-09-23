package lambdalabs.filestorage;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("test")
public class AcceptanceIntegrationTest {

	private static RestTemplate restTemplate;

	@BeforeAll
	static void setup(@Autowired RestTemplateBuilder builder) {
		boolean mongoAvailable = isMongoRunning();
		Assumptions.assumeTrue(mongoAvailable, "MongoDB must be running at mongodb://localhost:27017 for this integration test");

		restTemplate = builder.requestFactoryBuilder(factory -> {
					var simple = new org.springframework.http.client.SimpleClientHttpRequestFactory();
					simple.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
					simple.setReadTimeout((int) Duration.ofMinutes(10).toMillis());
					return simple;
				})
			.errorHandler(new ResponseErrorHandler() {
				@Override
				public boolean hasError(ClientHttpResponse response) { return false; }
			})
			.build();
	}

	@AfterAll
	static void teardown() {

	}

	@Test
	void cannotUploadSameFilename_inParallel() throws Exception {
		final String user = "acc-user-1";
		final String filename = "conflict-name.txt";
		String createdId = null;
		try {
			Callable<ResponseEntity<Map<String,Object>>> uploader1 = () -> upload(user, filename, "A first content", "text/plain", "PRIVATE");
			Callable<ResponseEntity<Map<String,Object>>> uploader2 = () -> upload(user, filename, "A second content", "text/plain", "PRIVATE");

			List<ResponseEntity<Map<String,Object>>> results = runInParallel(uploader1, uploader2);

			ResponseEntity<Map<String,Object>> r1 = results.get(0);
			ResponseEntity<Map<String,Object>> r2 = results.get(1);

			// One must succeed (200) and the other must be 409 conflict
			boolean ok1 = r1.getStatusCode() == HttpStatus.OK;
			boolean ok2 = r2.getStatusCode() == HttpStatus.OK;
			Assertions.assertTrue(ok1 ^ ok2, "Exactly one upload should succeed");

			ResponseEntity<Map<String,Object>> success = ok1 ? r1 : r2;
			Map<String,Object> body = success.getBody();
			Assertions.assertNotNull(body);

			String id = String.valueOf(body.get("id"));
			Assertions.assertNotNull(id);
			createdId = id;
		} finally {
			if (createdId != null) {
				assertDelete(user, createdId, HttpStatus.NO_CONTENT);
			}
		}
	}

	@Test
	void cannotUploadSameContents_inParallel() throws Exception {
		final String user = "acc-user-2";
		final String content = "SAME_CONTENT_PAYLOAD";
		String createdId = null;
		try {
			Callable<ResponseEntity<Map<String,Object>>> uploader1 = () -> upload(user, "name-a.txt", content, "text/plain", "PRIVATE");
			Callable<ResponseEntity<Map<String,Object>>> uploader2 = () -> upload(user, "name-b.txt", content, "text/plain", "PRIVATE");

			List<ResponseEntity<Map<String,Object>>> results = runInParallel(uploader1, uploader2);

			ResponseEntity<Map<String,Object>> r1 = results.get(0);
			ResponseEntity<Map<String,Object>> r2 = results.get(1);

			boolean ok1 = r1.getStatusCode() == HttpStatus.OK;
			boolean ok2 = r2.getStatusCode() == HttpStatus.OK;
			Assertions.assertTrue(ok1 ^ ok2, "Exactly one upload should succeed");

			ResponseEntity<Map<String,Object>> success = ok1 ? r1 : r2;
			Map<String,Object> body = success.getBody();
			Assertions.assertNotNull(body);

			String id = String.valueOf(body.get("id"));
			Assertions.assertNotNull(id);
			createdId = id;
		} finally {
			if (createdId != null) {
				assertDelete(user, createdId, HttpStatus.NO_CONTENT);
			}
		}
	}

	@Test
	void deleteFile_notOwner_forbidden() {
		final String owner = "owner-user";
		final String other = "other-user";
		String id = null;
		try {
			ResponseEntity<Map<String,Object>> upload = upload(owner, "private.txt", "secret", "text/plain", "PRIVATE");
			Assertions.assertEquals(HttpStatus.OK, upload.getStatusCode());
			Map<String,Object> uploadBody = upload.getBody();
			Assertions.assertNotNull(uploadBody);
			id = String.valueOf(uploadBody.get("id"));

			// Attempt delete as different user
			assertDelete(other, id, HttpStatus.NOT_FOUND);
		} finally {
			if (id != null) {
				// Cleanup as owner
				assertDelete(owner, id, HttpStatus.NO_CONTENT);
			}
		}
	}

	@Test
	void listAllPublicFiles_showsPublicFromOthers() {
		final String userA;
		final String userB;
		Map<String,Object> upABody = null;
		Map<String,Object> upBBody = null;
		userA = "pub-user-a";
		userB = "pub-user-b";
		try {

			final String viewer = "pub-viewer";

			ResponseEntity<Map<String,Object>> upA = upload(userA, "pub-a.txt", "A", "text/plain", "PUBLIC");
			ResponseEntity<Map<String,Object>> upB = upload(userB, "pub-b.txt", "B", "text/plain", "PUBLIC");
			Assertions.assertEquals(HttpStatus.OK, upA.getStatusCode());
			Assertions.assertEquals(HttpStatus.OK, upB.getStatusCode());
			upABody = upA.getBody();
			upBBody = upB.getBody();
			Assertions.assertNotNull(upABody);
			Assertions.assertNotNull(upBBody);

			// List as viewer
			HttpHeaders headers = new HttpHeaders();
			headers.set("User-Id", viewer);
			RequestEntity<Void> listReq = new RequestEntity<>(headers, HttpMethod.GET, URI.create("http://localhost:8080/api/files?visibility=PUBLIC"));
			ResponseEntity<List<Map<String,Object>>> listResp = restTemplate.exchange(listReq, new ParameterizedTypeReference<>() {});
			Assertions.assertEquals(HttpStatus.OK, listResp.getStatusCode());
			List<Map<String,Object>> items = listResp.getBody();
			Assertions.assertNotNull(items);
			boolean hasA = items.stream().anyMatch(m -> "pub-a.txt".equals(String.valueOf(m.get("filename"))));
			boolean hasB = items.stream().anyMatch(m -> "pub-b.txt".equals(String.valueOf(m.get("filename"))));
			Assertions.assertTrue(hasA && hasB, "Public list should include both uploaded public files");
		} finally {
			// cleanup
			if (upABody != null) {
				assertDelete(userA, String.valueOf(upABody.get("id")), HttpStatus.NO_CONTENT);
			}
			if (upBBody != null) {
				assertDelete(userB, String.valueOf(upBBody.get("id")), HttpStatus.NO_CONTENT);
			}
		}
	}

	private static ResponseEntity<Map<String,Object>> upload(String userId, String filename, String content, String contentType, String visibility) {
		InputStream dataStream = new StringStream(content);
		InputStreamResource body = new InputStreamResource(dataStream) {
			@Override
			public String getFilename() { return filename; }
			@Override
			public long contentLength() { return content.length(); }
		};

		HttpHeaders headers = new HttpHeaders();
		headers.set("User-Id", userId);
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		URI uri = URI.create("http://localhost:8080/api/files/upload?filename=" + filename + "&contentType=" + contentType + "&visibility=" + visibility);
		RequestEntity<InputStreamResource> req = new RequestEntity<>(body, headers, HttpMethod.POST, uri);
		return restTemplate.exchange(req, new ParameterizedTypeReference<>() {});
	}

	private static void assertDelete(String userId, String id, HttpStatus expected) {
		HttpHeaders headers = new HttpHeaders();
		headers.set("User-Id", userId);
		RequestEntity<Void> req = new RequestEntity<>(headers, HttpMethod.DELETE, URI.create("http://localhost:8080/api/files/" + id));
		ResponseEntity<Void> resp = restTemplate.exchange(req, Void.class);
		Assertions.assertEquals(expected, resp.getStatusCode());
	}

	private static <T> List<T> runInParallel(Callable<T> a, Callable<T> b) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch latch = new CountDownLatch(1);

		Callable<T> gatedA = () -> { latch.await(10, TimeUnit.SECONDS); return a.call(); };
		Callable<T> gatedB = () -> { latch.await(10, TimeUnit.SECONDS); return b.call(); };

		Future<T> fa = pool.submit(gatedA);
		Future<T> fb = pool.submit(gatedB);
		latch.countDown();

		List<T> out = new ArrayList<>(2);
		out.add(fa.get(1, TimeUnit.MINUTES));
		out.add(fb.get(1, TimeUnit.MINUTES));
		pool.shutdownNow();
		return out;
	}

	private static boolean isMongoRunning() {
		try (java.net.Socket ignored = new java.net.Socket("localhost", 27017)) {
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	private static class StringStream extends InputStream {
		private final byte[] bytes;
		private int index = 0;

		StringStream(String content) {
			this.bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		}

		@Override
		public int read() {
			if (index >= bytes.length) return -1;
			return bytes[index++] & 0xFF;
		}

		@Override
		public int read(byte[] b, int off, int len) {
			if (index >= bytes.length) return -1;
			int toCopy = Math.min(len, bytes.length - index);
			System.arraycopy(bytes, index, b, off, toCopy);
			index += toCopy;
			return toCopy;
		}

		@Override
		public long skip(long n) {
			long can = Math.min(n, bytes.length - index);
			index += (int) can;
			return can;
		}

		@Override
		public int available() {
			return bytes.length - index;
		}
	}
}
