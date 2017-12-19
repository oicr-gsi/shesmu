package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import ca.on.oicr.gsi.shesmu.compiler.NameDefinitions;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;

@SuppressWarnings("restriction")
public final class Server {

	private static final LatencyHistogram responseTime = new LatencyHistogram("shesmu_http_request_time",
			"The time to respond to an HTTP request.", "url");

	public static void main(String[] args) throws Exception {
		DefaultExports.initialize();

		final Server s = new Server(8081);
		s.start();
	}

	private final CachedRepository<ActionRepository, ActionDefinition> actionRepository = new CachedRepository<>(
			ActionRepository.class, ActionRepository::query);
	private final CompiledGenerator compiler = new CompiledGenerator(
			Paths.get(System.getenv("SHESMU_DATA"), "main.shesmu"), this::lookups, this::actionDefinitions);
	private final CachedRepository<LookupRepository, Lookup> lookupRepository = new CachedRepository<>(
			LookupRepository.class, LookupRepository::query);
	private final ActionProcessor processor = new ActionProcessor();
	private final HttpServer server;
	private final Instant startTime = Instant.now();

	private final MasterRunner z_master = new MasterRunner(compiler::generator, lookupRepository::stream, processor);

	public Server(int port) throws IOException {
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.setExecutor(null);

		add("/", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody(); Writer writer = new PrintWriter(os)) {
				writer.write("<html><head><title>Shesmu</title></head><body><table>");
				final Map<String, String> properties = new TreeMap<>();
				properties.put("Uptime", Duration.between(startTime, Instant.now()).toString());
				properties.put("Start Time", startTime.toString());
				Stream.concat(Stream.of(new Pair<>("shesmu core", properties)),
						Stream.concat(actionRepository.implementations(), lookupRepository.implementations())
								.flatMap(LoadedConfiguration::listConfiguration))
						.forEach(config -> {
							try {
								writer.write("<tr><td colspan=\"2\">");
								writer.write(config.first());
								writer.write("</td></tr>");
							} catch (final IOException e) {
							}
							config.second().forEach((k, v) -> {
								try {
									writer.write("<tr><td>");
									writer.write(k);
									writer.write("</td><td>");
									writer.write(v);
									writer.write("</td></tr>");
								} catch (final IOException e) {
								}
							});
						});
				writer.write("</table><h1>Compile Errors</h1><p>");
				writer.write(compiler.errorHtml());
				writer.write("</p><h1>Variables</h1><table>");
				NameDefinitions.baseStreamVariables().forEach(variable -> {
					try {
						writer.write("<tr><td>");
						writer.write(variable.name());
						writer.write("</td><td>");
						writer.write(variable.type().name().replace("<", "&lt;").replace(">", "&gt;"));
						writer.write("</td></tr>");
					} catch (final IOException e) {
					}
				});
				writer.write("</table></body></html>");
			}
		});

		addJson("/actions", mapper -> {
			final ArrayNode array = mapper.createArrayNode();
			actionRepository.stream().forEach(actionDefinition -> {
				final ObjectNode obj = array.addObject();
				obj.put("name", actionDefinition.name());
				final ArrayNode parameters = obj.putArray("parameters");
				actionDefinition.parameters().forEach(param -> {
					final ObjectNode paramInfo = parameters.addObject();
					paramInfo.put("name", param.name());
					paramInfo.put("type", param.type().toString());
				});
			});
			return array;
		});

		addJson("/lookups", mapper -> {
			final ArrayNode array = mapper.createArrayNode();
			lookupRepository.stream().forEach(lookup -> {
				final ObjectNode obj = array.addObject();
				obj.put("name", lookup.name());
				lookup.types().map(Object::toString).forEach(obj.putArray("types")::add);
			});
			return array;
		});

		add("/metrics", t -> {
			t.getResponseHeaders().set("Content-type", TextFormat.CONTENT_TYPE_004);
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody(); Writer writer = new PrintWriter(os)) {
				TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
			}
		});

		add("/query", t -> {
			final ObjectMapper mapper = new ObjectMapper();
			final Query query = mapper.readValue(t.getRequestBody(), Query.class);
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				query.perform(os, mapper, processor);
			}
		});

		addJson("/variables", mapper -> {
			final ObjectNode node = mapper.createObjectNode();
			NameDefinitions.baseStreamVariables().forEach(variable -> {
				node.put(variable.name(), variable.type().signature());
			});
			return node;
		});
	}

	private Stream<ActionDefinition> actionDefinitions() {
		return actionRepository.stream();
	}

	private void add(String url, HttpHandler handler) {
		server.createContext(url, t -> {
			try (AutoCloseable timer = responseTime.start(url)) {
				handler.handle(t);
			} catch (final Exception e) {
				throw new IOException(e);
			}
		});
	}

	private void addJson(String url, Function<ObjectMapper, JsonNode> fetcher) {
		add(url, t -> {
			final ObjectMapper mapper = new ObjectMapper();
			final JsonNode node = fetcher.apply(mapper);
			t.getResponseHeaders().set("Content-type", "application/json");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				mapper.writeValue(os, node);
			}
		});
	}

	private Stream<Lookup> lookups() {
		return lookupRepository.stream();
	}

	public void start() {
		System.out.println("Starting server...");
		server.start();
		System.out.println("Finding actions...");
		final long actionCount = actionRepository.stream().count();
		System.out.printf("Found %d actions\n", actionCount);
		System.out.println("Finding lookups...");
		final long lookupCount = lookupRepository.stream().count();
		System.out.printf("Found %d looks\n", lookupCount);
		System.out.println("Compiling script...");
		compiler.start();
		System.out.println("Starting action processor...");
		processor.start();
		System.out.println("Starting scheduler...");
		z_master.start();
	}
}
