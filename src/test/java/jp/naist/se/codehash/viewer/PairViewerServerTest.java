package jp.naist.se.codehash.viewer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;


public class PairViewerServerTest {

	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private PairViewerServer server;
	private HttpClient client = HttpClient.newHttpClient();

	@Before
	public void setUp() throws Exception {
		server = new PairViewerServer("result.json", PairDataTest.read(temp), new SourceLoader());
		server.start(0);
	}

	@After
	public void tearDown() {
		server.stop();
	}

	private HttpResponse<String> get(String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create(server.getURL() + path)).build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	public void testPage() throws Exception {
		HttpResponse<String> r = get("");
		Assert.assertEquals(200, r.statusCode());
		Assert.assertTrue(r.body().contains("CodeHash Pair Viewer"));
		Assert.assertEquals(404, get("unknown").statusCode());
	}

	@Test
	public void testApi() throws Exception {
		HttpResponse<String> r = get("api/info");
		Assert.assertEquals(200, r.statusCode());
		Assert.assertTrue(r.body().contains("\"metrics\":[\"exact-jaccard\",\"jaccard\"]"));
		Assert.assertTrue(r.body().contains("\"pairCount\":4"));

		r = get("api/pairs?metric=jaccard&threshold=0.8&limit=2");
		Assert.assertEquals(200, r.statusCode());
		Assert.assertTrue(r.body().startsWith("{\"total\":3,"));
		Assert.assertTrue(r.body().contains("{\"file1\":{\"index\":13,\"path\":\"/d.c\"},\"file2\":{\"index\":14,\"path\":\"/e.c\"},\"values\":{\"exact-jaccard\":0.9,\"jaccard\":0.95}}"));
		Assert.assertFalse(r.body().contains("\"/c.c\""));

		r = get("api/clusters?metric=jaccard&threshold=0.8");
		Assert.assertEquals(200, r.statusCode());
		Assert.assertTrue(r.body().startsWith("{\"total\":2,"));
		Assert.assertTrue(r.body().contains("\"pairCount\":2"));

		// The file does not exist
		r = get("api/file?index=10");
		Assert.assertEquals(200, r.statusCode());
		Assert.assertTrue(r.body().contains("\"status\":\"UNAVAILABLE\""));

		Assert.assertEquals(404, get("api/file?index=0").statusCode());
		Assert.assertEquals(400, get("api/pairs?metric=unknown&threshold=0").statusCode());
		Assert.assertEquals(400, get("api/pairs?metric=jaccard&threshold=x").statusCode());
		Assert.assertEquals(405, get("api/repositories").statusCode());
	}

	@Test
	public void testAddRepository() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create(server.getURL() + "api/repositories"))
				.POST(HttpRequest.BodyPublishers.ofString(temp.newFolder("empty").getAbsolutePath())).build();
		HttpResponse<String> r = client.send(request, HttpResponse.BodyHandlers.ofString());
		Assert.assertEquals(400, r.statusCode());
		Assert.assertTrue(r.body().contains("Not a git repository"));
	}

}
