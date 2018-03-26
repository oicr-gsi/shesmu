package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import ca.on.oicr.gsi.shesmu.compiler.NameDefinitions;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;

@SuppressWarnings("restriction")
public final class Server {

	private static final LatencyHistogram responseTime = new LatencyHistogram("shesmu_http_request_time",
			"The time to respond to an HTTP request.", "url");

	private static String htmlEscape(Imyhat type) {
		return type.name().replace("<", "&lt;").replace(">", "&gt;");
	}

	public static void main(String[] args) throws Exception {
		DefaultExports.initialize();

		final Server s = new Server(8081);
		s.start();
	}

	private final CachedRepository<ActionRepository, ActionDefinition> actionRepository = new CachedRepository<>(
			ActionRepository.class, ActionRepository::query);
	private final CompiledGenerator compiler = new CompiledGenerator(Paths.get(System.getenv("SHESMU_SCRIPT")),
			this::lookups, this::actionDefinitions, ConstantSource::all);
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
			try (OutputStream os = t.getResponseBody(); PrintStream writer = new PrintStream(os, false, "UTF-8")) {
				writePageHeader(writer);
				writeHeader(writer, "Core");
				writeRow(writer, "Uptime", Duration.between(startTime, Instant.now()).toString());
				writeRow(writer, "Start Time", startTime.toString());
				writeFinish(writer);
				if (!compiler.errorHtml().isEmpty()) {
					writer.print("<h1>Compile Errors</h1><p>");
					writer.print(compiler.errorHtml());
					writer.print("</p>");
				}
				Stream.<Supplier<Stream<? extends LoadedConfiguration>>>of(//
						VariablesSource::sources, //
						actionRepository::implementations, //
						lookupRepository::implementations, //
						Throttler::services)//
						.flatMap(Supplier::get)//
						.flatMap(LoadedConfiguration::listConfiguration).forEach(config -> {
							writeHeader(writer, config.first());
							config.second().forEach((k, v) -> writeRow(writer, k, v));
							writeFinish(writer);
						});

				writePageFooter(writer);
			}
		});

		add("/definitions", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody(); PrintStream writer = new PrintStream(os, false, "UTF-8")) {
				writePageHeader(writer);

				writeHeader(writer, "Lookups");
				lookupRepository.stream().sorted(Comparator.comparing(Lookup::name)).forEach(lookup -> {
					writeBlock(writer, lookup.name());
					writeRow(writer, "Return", lookup.returnType().name());
					lookup.types().map(Pair.number()).forEach(p -> writeRow(writer,
							"Argument " + Integer.toString(p.first() + 1), htmlEscape(p.second())));

				});
				writeFinish(writer);

				writeHeader(writer, "Actions");
				actionRepository.stream().sorted(Comparator.comparing(ActionDefinition::name)).forEach(action -> {
					writeBlock(writer, action.name());
					action.parameters().sorted((a, b) -> a.name().compareTo(b.name()))
							.forEach(p -> writeRow(writer, p.name(), htmlEscape(p.type())));

				});
				writeFinish(writer);

				writeHeader(writer, "Variables");
				NameDefinitions.baseStreamVariables().sorted(Comparator.comparing(Target::name)).forEach(variable -> {
					writeRow(writer, variable.name(), htmlEscape(variable.type()));
				});
				writeFinish(writer);

				writeHeader(writer, "Constants");
				ConstantSource.all().sorted(Comparator.comparing(Target::name)).forEach(constant -> {
					writeRow(writer, constant.name(), htmlEscape(constant.type()));
				});
				writeFinish(writer);

				writePageFooter(writer);
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
			final Query query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), Query.class);
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				query.perform(os, RuntimeSupport.MAPPER, processor);
			}
		});

		addJson("/variables", mapper -> {
			final ObjectNode node = mapper.createObjectNode();
			NameDefinitions.baseStreamVariables().forEach(variable -> {
				node.put(variable.name(), variable.type().signature());
			});
			return node;
		});

		add("/main.css", "text/css");
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

	private void add(String url, String type) {
		server.createContext(url, t -> {
			t.getResponseHeaders().set("Content-type", type);
			t.sendResponseHeaders(200, 0);
			final byte[] b = new byte[1024];
			try (OutputStream output = t.getResponseBody(); InputStream input = getClass().getResourceAsStream(url)) {
				int count;
				while ((count = input.read(b)) > 0) {
					output.write(b, 0, count);
				}
			} catch (final IOException e) {
				e.printStackTrace();
			}
		});
	}

	private void addJson(String url, Function<ObjectMapper, JsonNode> fetcher) {
		add(url, t -> {
			final JsonNode node = fetcher.apply(RuntimeSupport.MAPPER);
			t.getResponseHeaders().set("Content-type", "application/json");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				RuntimeSupport.MAPPER.writeValue(os, node);
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
		System.out.printf("Found %d lookups\n", lookupCount);
		System.out.println("Compiling script...");
		compiler.start();
		System.out.println("Starting action processor...");
		processor.start();
		System.out.println("Starting scheduler...");
		z_master.start();
	}

	private void writeBlock(PrintStream writer, String title) {
		writer.print("<tr><th colspan=\"2\">");
		writer.print(title);
		writer.print("</th></tr>");
	}

	private void writeFinish(PrintStream writer) {
		writer.print("</table>");

	}

	private void writeHeader(PrintStream writer, String title) {
		writer.print("<h1>");
		writer.print(title);
		writer.print("</h1><table>");
	}

	private void writePageFooter(PrintStream writer) {
		writer.print("</div></body></html>");
	}

	private void writePageHeader(PrintStream writer) {
		writer.print(
				"<html><head><link type=\"text/css\" rel=\"stylesheet\" href=\"main.css\"/><title>Shesmu</title></head><body><nav><a href=\"/\">Status</a><a href=\"/definitions\">Definitions</a></nav><div><table>");
	}

	private void writeRow(PrintStream writer, String key, String value) {
		writer.print("<tr><td>");
		writer.print(key);
		writer.print("</td><td>");
		writer.print(value);
		writer.print("</td></tr>");

	}
}
