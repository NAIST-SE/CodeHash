package jp.naist.se.codehash.viewer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import jp.naist.se.codehash.viewer.PairData.Cluster;
import jp.naist.se.codehash.viewer.PairData.FileInfo;
import jp.naist.se.codehash.viewer.SourceLoader.Source;

/**
 * An HTTP server of the pair viewer.  It accepts only connections from the local host.
 *
 * - GET / returns the viewer page.
 * - GET /api/info returns the summary of the data.
 * - GET /api/pairs?metric=[NAME]&amp;threshold=[VALUE]&amp;limit=[N] returns pairs whose values are greater than or equal to the threshold.
 * - GET /api/clusters?metric=[NAME]&amp;threshold=[VALUE]&amp;limit=[N] returns clusters of files connected by the pairs.
 * - GET /api/file?index=[N] returns the content of a file.
 * - POST /api/repositories adds a git repository whose path is the request body.
 */
public class PairViewerServer {

	private static final int DEFAULT_LIMIT = 1000;

	private String jsonPath;
	private PairData data;
	private SourceLoader loader;
	private HttpServer server;
	private JsonFactory factory = new JsonFactory();

	private interface Handler {
		/**
		 * Write a response body in JSON.
		 * @throws RequestException if the request is invalid.
		 */
		void handle(HttpExchange exchange, HashMap<String, String> params, JsonGenerator gen) throws IOException, RequestException;
	}

	/**
	 * The status code and message of an invalid request.
	 */
	private static class RequestException extends Exception {
		private static final long serialVersionUID = 1L;
		private int status;

		public RequestException(int status, String message) {
			super(message);
			this.status = status;
		}
	}

	/**
	 * @param jsonPath is the path of the JSON file shown in the page.
	 * @param data is the content of the JSON file.
	 * @param loader loads files.
	 */
	public PairViewerServer(String jsonPath, PairData data, SourceLoader loader) {
		this.jsonPath = jsonPath;
		this.data = data;
		this.loader = loader;
	}

	/**
	 * Start the server on the loopback address.
	 * @param port specifies a port number.  0 selects an available port.
	 * @throws IOException if the server cannot be started.
	 */
	public void start(int port) throws IOException {
		server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
		server.createContext("/", this::handlePage);
		server.createContext("/api/info", e -> handleJson(e, "GET", this::writeInfo));
		server.createContext("/api/pairs", e -> handleJson(e, "GET", this::writePairs));
		server.createContext("/api/clusters", e -> handleJson(e, "GET", this::writeClusters));
		server.createContext("/api/file", e -> handleJson(e, "GET", this::writeFile));
		server.createContext("/api/repositories", e -> handleJson(e, "POST", this::addRepository));
		server.start();
	}

	public void stop() {
		server.stop(0);
	}

	/**
	 * @return the URL of the viewer page.
	 */
	public String getURL() {
		return "http://localhost:" + server.getAddress().getPort() + "/";
	}

	/**
	 * Reject a request whose Host header is not the local host, e.g. a request from a web page using DNS rebinding.
	 */
	private boolean isLocalHost(HttpExchange exchange) {
		String host = exchange.getRequestHeaders().getFirst("Host");
		if (host == null) return false;
		int port = server.getAddress().getPort();
		return host.equals("localhost:" + port) || host.equals("127.0.0.1:" + port) || host.equals("[::1]:" + port);
	}

	private void handlePage(HttpExchange exchange) throws IOException {
		try {
			if (!isLocalHost(exchange)) {
				send(exchange, 403, "text/plain; charset=utf-8", "Forbidden".getBytes(StandardCharsets.UTF_8));
			} else if (!exchange.getRequestURI().getPath().equals("/")) {
				send(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8));
			} else {
				try (InputStream in = PairViewerServer.class.getResourceAsStream("index.html")) {
					send(exchange, 200, "text/html; charset=utf-8", in.readAllBytes());
				}
			}
		} finally {
			exchange.close();
		}
	}

	private void handleJson(HttpExchange exchange, String method, Handler handler) throws IOException {
		try {
			if (!isLocalHost(exchange)) {
				throw new RequestException(403, "Forbidden");
			}
			if (!exchange.getRequestMethod().equals(method)) {
				throw new RequestException(405, "Method not allowed");
			}
			ByteArrayOutputStream buffer = new ByteArrayOutputStream();
			try (JsonGenerator gen = factory.createGenerator(buffer)) {
				handler.handle(exchange, parseQuery(exchange.getRequestURI().getRawQuery()), gen);
			}
			send(exchange, 200, "application/json; charset=utf-8", buffer.toByteArray());
		} catch (RequestException e) {
			sendError(exchange, e.status, e.getMessage());
		} catch (RuntimeException e) {
			sendError(exchange, 500, e.toString());
		} finally {
			exchange.close();
		}
	}

	private void sendError(HttpExchange exchange, int status, String message) throws IOException {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();
		try (JsonGenerator gen = factory.createGenerator(buffer)) {
			gen.writeStartObject();
			gen.writeStringField("error", message);
			gen.writeEndObject();
		}
		send(exchange, status, "application/json; charset=utf-8", buffer.toByteArray());
	}

	private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private static HashMap<String, String> parseQuery(String query) {
		HashMap<String, String> params = new HashMap<>();
		if (query == null) return params;
		for (String param: query.split("&")) {
			int index = param.indexOf('=');
			if (index > 0) {
				params.put(URLDecoder.decode(param.substring(0, index), StandardCharsets.UTF_8), URLDecoder.decode(param.substring(index+1), StandardCharsets.UTF_8));
			}
		}
		return params;
	}

	private static double getDouble(HashMap<String, String> params, String name) throws RequestException {
		try {
			return Double.parseDouble(params.getOrDefault(name, ""));
		} catch (NumberFormatException e) {
			throw new RequestException(400, "Invalid " + name);
		}
	}

	private static int getInt(HashMap<String, String> params, String name, int defaultValue) throws RequestException {
		if (!params.containsKey(name)) return defaultValue;
		try {
			return Integer.parseInt(params.get(name));
		} catch (NumberFormatException e) {
			throw new RequestException(400, "Invalid " + name);
		}
	}

	private int getMetric(HashMap<String, String> params) throws RequestException {
		int metric = data.getMetricIndex(params.getOrDefault("metric", ""));
		if (metric < 0) throw new RequestException(400, "Unknown metric");
		return metric;
	}

	private void writeInfo(HttpExchange exchange, HashMap<String, String> params, JsonGenerator gen) throws IOException {
		gen.writeStartObject();
		gen.writeStringField("json", jsonPath);
		gen.writeStringField("similarity", data.getSimilarity());
		gen.writeArrayFieldStart("metrics");
		for (String m: data.getMetrics()) {
			gen.writeString(m);
		}
		gen.writeEndArray();
		gen.writeNumberField("fileCount", data.getFiles().size());
		gen.writeNumberField("pairCount", data.getPairCount());
		writeRepositories(gen);
		gen.writeEndObject();
	}

	private void writeRepositories(JsonGenerator gen) throws IOException {
		gen.writeArrayFieldStart("repositories");
		for (String path: loader.getRepositoryPaths()) {
			gen.writeString(path);
		}
		gen.writeEndArray();
	}

	private void writePairs(HttpExchange exchange, HashMap<String, String> params, JsonGenerator gen) throws IOException, RequestException {
		int[] pairs = data.select(getMetric(params), getDouble(params, "threshold"));
		gen.writeStartObject();
		gen.writeNumberField("total", pairs.length);
		writePairList(gen, pairs, getInt(params, "limit", DEFAULT_LIMIT));
		gen.writeEndObject();
	}

	private void writeClusters(HttpExchange exchange, HashMap<String, String> params, JsonGenerator gen) throws IOException, RequestException {
		List<Cluster> clusters = data.cluster(getMetric(params), getDouble(params, "threshold"));
		int limit = getInt(params, "limit", DEFAULT_LIMIT);
		gen.writeStartObject();
		gen.writeNumberField("total", clusters.size());
		gen.writeArrayFieldStart("clusters");
		for (Cluster c: clusters.subList(0, Math.min(limit, clusters.size()))) {
			gen.writeStartObject();
			gen.writeArrayFieldStart("files");
			for (int f: c.getFiles()) {
				writeFileSummary(gen, f);
			}
			gen.writeEndArray();
			gen.writeNumberField("pairCount", c.getPairs().length);
			writePairList(gen, c.getPairs(), limit);
			gen.writeEndObject();
		}
		gen.writeEndArray();
		gen.writeEndObject();
	}

	private void writePairList(JsonGenerator gen, int[] pairs, int limit) throws IOException {
		gen.writeArrayFieldStart("pairs");
		for (int i=0; i<Math.min(limit, pairs.length); i++) {
			int p = pairs[i];
			gen.writeStartObject();
			gen.writeFieldName("file1");
			writeFileSummary(gen, data.getFirst(p));
			gen.writeFieldName("file2");
			writeFileSummary(gen, data.getSecond(p));
			gen.writeObjectFieldStart("values");
			for (int m=0; m<data.getMetrics().size(); m++) {
				double v = data.getValue(m, p);
				if (!Double.isNaN(v)) gen.writeNumberField(data.getMetrics().get(m), v);
			}
			gen.writeEndObject();
			gen.writeEndObject();
		}
		gen.writeEndArray();
	}

	private void writeFileSummary(JsonGenerator gen, int index) throws IOException {
		FileInfo f = data.getFile(index);
		gen.writeStartObject();
		gen.writeNumberField("index", index);
		gen.writeStringField("path", f != null ? f.getPath() : null);
		gen.writeEndObject();
	}

	private void writeFile(HttpExchange exchange, HashMap<String, String> params, JsonGenerator gen) throws IOException, RequestException {
		FileInfo f = data.getFile(getInt(params, "index", -1));
		if (f == null) throw new RequestException(404, "Unknown file index");
		Source s = loader.load(f);
		gen.writeStartObject();
		gen.writeNumberField("index", f.getIndex());
		gen.writeStringField("path", f.getPath());
		gen.writeStringField("group", f.getGroup());
		gen.writeStringField("lang", f.getLanguage());
		gen.writeStringField("status", s.getStatus().name());
		gen.writeStringField("message", s.getMessage());
		gen.writeStringField("text", s.getText());
		gen.writeEndObject();
	}

	private void addRepository(HttpExchange exchange, HashMap<String, String> params, JsonGenerator gen) throws IOException, RequestException {
		String path = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8).trim();
		try {
			loader.addRepository(path);
		} catch (IOException e) {
			throw new RequestException(400, "Not a git repository: " + path);
		}
		gen.writeStartObject();
		writeRepositories(gen);
		gen.writeEndObject();
	}

}
