package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import ca.on.oicr.gsi.shesmu.Constant.ConstantLoader;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;

@SuppressWarnings("restriction")
public final class Server {
	private static class EmergencyThrottlerHandler implements HttpHandler {
		private final boolean state;

		public EmergencyThrottlerHandler(boolean state) {
			super();
			this.state = state;
		}

		@Override
		public void handle(HttpExchange t) throws IOException {
			EmergencyThrottler.set(state);
			t.getResponseHeaders().set("Location", "/");
			t.sendResponseHeaders(302, -1);
		}

	}

	private static final LatencyHistogram responseTime = new LatencyHistogram("shesmu_http_request_time",
			"The time to respond to an HTTP request.", "url");

	public static void main(String[] args) throws Exception {
		DefaultExports.initialize();

		final Server s = new Server(8081);
		s.start();
	}

	private final CachedRepository<ActionRepository, ActionDefinition> actionRepository = new CachedRepository<>(
			ActionRepository.class, 15, ActionRepository::queryActions);
	private final CompiledGenerator compiler = new CompiledGenerator(this::functions, this::actionDefinitions,
			ConstantSource::all);
	private final Map<String, ConstantLoader> constantLoaders = new HashMap<>();
	private final CachedRepository<FunctionRepository, FunctionDefinition> functionpRepository = new CachedRepository<>(
			FunctionRepository.class, 15, FunctionRepository::queryFunctions);
	private final Map<String, FunctionRunner> functionRunners = new HashMap<>();
	private final Semaphore inputDownloadSemaphore = new Semaphore(Runtime.getRuntime().availableProcessors() / 2 + 1);
	private final ActionProcessor processor = new ActionProcessor();
	private final HttpServer server;
	private final Instant startTime = Instant.now();

	private final StaticActions staticActions = new StaticActions(processor::accept, this::actionDefinitions);

	private final MasterRunner z_master = new MasterRunner(compiler, processor);

	public Server(int port) throws IOException {
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));

		add("/", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody(); PrintStream writer = new PrintStream(os, false, "UTF-8")) {
				writePageHeader(writer);
				writeHeaderedTable(writer, "Core");
				writeRow(writer, "Uptime", Duration.between(startTime, Instant.now()).toString());
				writeRow(writer, "Start Time", startTime.toString());
				writeRow(writer, "Emergency Stop",
						String.format("<a class=\"load\" href=\"%s\">%s</a>",
								EmergencyThrottler.stopped() ? "/resume" : "/stopstopstop",
								EmergencyThrottler.stopped() ? "▶ Resume" : "⏹ STOP ALL ACTIONS"));
				writeFinish(writer);
				if (!compiler.errorHtml().isEmpty()) {
					writer.print("<h1>Compile Errors</h1><p>");
					writer.print(compiler.errorHtml());
					writer.print("</p>");
				}
				Stream.<Supplier<Stream<? extends LoadedConfiguration>>>of(//
						InputFormatDefinition::allConfiguration, //
						actionRepository::implementations, //
						functionpRepository::implementations, //
						Throttler::services, //
						ConstantSource::sources, //
						() -> Stream.of(staticActions)//
				)//
						.flatMap(Supplier::get)//
						.flatMap(LoadedConfiguration::listConfiguration)//
						.sorted(Comparator.comparing(Pair::first))//
						.forEach(config -> {
							writeHeaderedTable(writer, config.first());
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

				writeHeaderedTable(writer, "Type");
				writeRow(writer, "Signature",
						"<input type=\"text\" id=\"uglySignature\"></input> <span class=\"load\" onclick=\"prettyType();\">💅 Beautify</span>");
				writeRow(writer, "Pretty Type", "<span id=\"prettyType\"></span>");
				writeFinish(writer);

				writeHeader(writer, "Functions");
				functionpRepository.stream().sorted(Comparator.comparing(FunctionDefinition::name))
						.forEach(function -> {
							writeTable(writer, function.name());
							function.parameters().map(Pair.number())
									.forEach(p -> writeRow(writer,
											"Argument " + Integer.toString(p.first() + 1) + ": " + p.second().name(),
											String.format("%s <input type=\"text\" id=\"%s$%d\"></input>",
													p.second().type().name(), function.name(), p.first())));
							writeRow(writer, "Result: " + function.returnType().name(), String.format(
									"<span class=\"load\" onclick=\"runFunction('%s', this, %s)\">▶ Run</span><span></span>",
									function.name(), function.parameters().map(p -> p.type().javaScriptParser())
											.collect(Collectors.joining(",", "[", "]"))));
							writeDescription(writer, function.description());

							writeFinish(writer);
						});

				writeHeader(writer, "Actions");
				actionRepository.stream().sorted(Comparator.comparing(ActionDefinition::name)).forEach(action -> {
					writeTable(writer, action.name());
					action.parameters().sorted((a, b) -> a.name().compareTo(b.name()))
							.forEach(p -> writeRow(writer, p.name(), p.type().name()));
					writeDescription(writer, action.description());
					writeFinish(writer);
				});

				InputFormatDefinition.formats().forEach(format -> {
					writeHeaderedTable(writer, "Variables: " + format.name());
					format.baseStreamVariables().sorted(Comparator.comparing(Target::name)).forEach(variable -> {
						writeRow(writer, variable.name(), variable.type().name());
					});
					writeFinish(writer);
				});

				writeHeader(writer, "Constants");
				ConstantSource.all().sorted(Comparator.comparing(Target::name)).forEach(constant -> {
					writeTable(writer, constant.name());
					writeRow(writer, "Type", constant.type().name());
					writeRow(writer, "Value", String.format(
							"<span class=\"load\" onclick=\"fetchConstant('%s', this)\">▶ Get</span><span></span>",
							constant.name()));
					writeDescription(writer, constant.description());
					writeFinish(writer);
				});

				writePageFooter(writer);
			}
		});

		addJson("/actions", mapper -> {
			final ArrayNode array = mapper.createArrayNode();
			actionRepository.stream().forEach(actionDefinition -> {
				final ObjectNode obj = array.addObject();
				obj.put("name", actionDefinition.name());
				obj.put("description", actionDefinition.description());
				final ArrayNode parameters = obj.putArray("parameters");
				actionDefinition.parameters().forEach(param -> {
					final ObjectNode paramInfo = parameters.addObject();
					paramInfo.put("name", param.name());
					paramInfo.put("type", param.type().toString());
					paramInfo.put("required", param.required());
				});
			});
			return array;
		});

		addJson("/constants", mapper -> {
			final ArrayNode array = mapper.createArrayNode();
			ConstantSource.all().forEach(constant -> {
				final ObjectNode obj = array.addObject();
				obj.put("name", constant.name());
				obj.put("description", constant.description());
				obj.put("type", constant.type().signature());
			});
			return array;
		});

		addJson("/functions", mapper -> {
			final ArrayNode array = mapper.createArrayNode();
			functionpRepository.stream().forEach(function -> {
				final ObjectNode obj = array.addObject();
				obj.put("name", function.name());
				obj.put("description", function.description());
				obj.put("return", function.returnType().signature());
				final ArrayNode parameters = obj.putArray("parameters");
				function.parameters().forEach(p -> {
					final ObjectNode parameter = parameters.addObject();
					parameter.put("type", p.type().signature());
					parameter.put("name", p.name());
				});
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

		add("/constant", t -> {
			final String query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
			ConstantLoader loader;
			if (constantLoaders.containsKey(query)) {
				loader = constantLoaders.get(query);
			} else {
				loader = ConstantSource.all()//
						.filter(c -> c.name().equals(query))//
						.findFirst()//
						.map(Constant::compile)//
						.orElseGet(() -> target -> target.put("error", String.format("No such constant.", query)));
				constantLoaders.put(query, loader);
			}
			t.getResponseHeaders().set("Content-type", "application/json");
			t.sendResponseHeaders(200, 0);
			final ObjectNode node = RuntimeSupport.MAPPER.createObjectNode();
			try {
				loader.load(node);
			} catch (final Exception e) {
				node.put("error", e.getMessage());
			}
			try (OutputStream os = t.getResponseBody()) {
				RuntimeSupport.MAPPER.writeValue(os, node);
			}
		});

		add("/function", t -> {
			final FunctionRequest query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), FunctionRequest.class);
			FunctionRunner runner;
			if (functionRunners.containsKey(query.getName())) {
				runner = functionRunners.get(query.getName());
			} else {
				runner = functionpRepository.stream()//
						.filter(f -> f.name().equals(query.getName()))//
						.findFirst()//
						.map(FunctionRunnerCompiler::compile)//
						.orElseGet(
								() -> (args, target) -> target.put("error", String.format("No such function.", query)));
				functionRunners.put(query.getName(), runner);
			}
			t.getResponseHeaders().set("Content-type", "application/json");
			t.sendResponseHeaders(200, 0);
			final ObjectNode node = RuntimeSupport.MAPPER.createObjectNode();
			try {
				runner.run(query.getArgs(), node);
			} catch (final Exception e) {
				node.put("error", e.getMessage());
			}
			try (OutputStream os = t.getResponseBody()) {
				RuntimeSupport.MAPPER.writeValue(os, node);
			}
		});

		addJson("/variables", mapper -> {
			final ObjectNode node = mapper.createObjectNode();
			InputFormatDefinition.formats().forEach(source -> {
				final ObjectNode sourceNode = node.putObject(source.name());

				source.baseStreamVariables().forEach(variable -> {
					sourceNode.put(variable.name(), variable.type().signature());
				});
			});
			return node;
		});

		InputFormatDefinition.formats().forEach(format -> {
			add(String.format("/input/%s", format.name()), t -> {
				if (!inputDownloadSemaphore.tryAcquire()) {
					t.sendResponseHeaders(503, 0);
					try (OutputStream os = t.getResponseBody()) {
					}
					return;
				}
				t.getResponseHeaders().set("Content-type", "application/json");
				t.sendResponseHeaders(200, 0);
				final JsonFactory jfactory = new JsonFactory();
				try (OutputStream os = t.getResponseBody();
						JsonGenerator jGenerator = jfactory.createGenerator(os, JsonEncoding.UTF8)) {
					jGenerator.writeStartArray();
					format.write(jGenerator);
					jGenerator.writeEndArray();
					jGenerator.close();
				} catch (final IOException e) {
					e.printStackTrace();
				} finally {
					inputDownloadSemaphore.release();
				}
			});
		});

		add("/type", t -> {
			final Imyhat type = Imyhat.parse(RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class));
			t.getResponseHeaders().set("Content-type", "application/json");
			if (type.isBad()) {
				t.sendResponseHeaders(400, 0);
				try (OutputStream os = t.getResponseBody()) {
				}
			} else {
				t.sendResponseHeaders(200, 0);
				try (OutputStream os = t.getResponseBody()) {
					RuntimeSupport.MAPPER.writeValue(os, type.name());
				}
			}
		});

		add("/resume", new EmergencyThrottlerHandler(false));
		add("/stopstopstop", new EmergencyThrottlerHandler(true));
		add("/main.css", "text/css");
		add("/shesmu.js", "text/javascript");
		add("/shesmu.svg", "image/svg+xml");
		add("/favicon.png", "image/png");
	}

	private Stream<ActionDefinition> actionDefinitions() {
		return actionRepository.stream();
	}

	/**
	 * Add a new service endpoint with Prometheus monitoring
	 */
	private void add(String url, HttpHandler handler) {
		server.createContext(url, t -> {
			try (AutoCloseable timer = responseTime.start(url)) {
				handler.handle(t);
			} catch (final Exception e) {
				throw new IOException(e);
			}
		});
	}

	/**
	 * Add a file backed by a class resource
	 */
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

	/**
	 * Add a new service endpoint with Prometheus monitoring that handles JSON
	 */
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

	private Stream<FunctionDefinition> functions() {
		return functionpRepository.stream();
	}

	public void start() {
		System.out.println("Starting server...");
		server.start();
		System.out.println("Waiting for files to be scanned...");
		actionRepository.implementations().count();
		ConstantSource.sources().count();
		functionpRepository.implementations().count();
		Throttler.services().count();
		try {
			Thread.sleep(5000);
		} catch (final InterruptedException e) {
			// Meh.
		}
		System.out.println("Finding actions...");
		final long actionCount = actionRepository.stream().count();
		System.out.printf("Found %d actions\n", actionCount);
		System.out.println("Finding functions...");
		final long functionCount = functionpRepository.stream().count();
		System.out.printf("Found %d functions\n", functionCount);
		final long constantCount = ConstantSource.all().count();
		System.out.printf("Found %d constants\n", constantCount);
		final long throttlerCount = Throttler.services().count();
		System.out.printf("Found %d throttler\n", throttlerCount);
		System.out.println("Compiling script...");
		compiler.start();
		staticActions.start();
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

	private void writeDescription(PrintStream writer, String info) {
		writer.print("<tr><td colspan=\"2\">");
		writer.print(info);
		writer.print("</td></tr>");
	}

	private void writeFinish(PrintStream writer) {
		writer.print("</table>");

	}

	private void writeHeader(PrintStream writer, String title) {
		writer.print("<h1>");
		writer.print(title);
		writer.print("</h1>");
	}

	private void writeHeaderedTable(PrintStream writer, String title) {
		writer.print("<h1>");
		writer.print(title);
		writer.print("</h1><table>");
	}

	private void writePageFooter(PrintStream writer) {
		writer.print("</div></body></html>");
	}

	private void writePageHeader(PrintStream writer) {
		writer.print(
				"<html><head><link type=\"text/css\" rel=\"stylesheet\" href=\"main.css\"/><link rel=\"icon\" href=\"favicon.png\" sizes=\"16x16\" type=\"image/png\"><script type=\"text/javascript\" src=\"shesmu.js\"></script><title>Shesmu</title></head><body><nav><img src=\"shesmu.svg\" /><a href=\"/\">Status</a><a href=\"/definitions\">Definitions</a></nav><div><table>");
	}

	private void writeRow(PrintStream writer, String key, String value) {
		writer.print("<tr><td>");
		writer.print(key);
		writer.print("</td><td>");
		writer.print(value);
		writer.print("</td></tr>");

	}

	private void writeTable(PrintStream writer, String title) {
		writer.print("<table>");
		writeBlock(writer, title);
	}
}
