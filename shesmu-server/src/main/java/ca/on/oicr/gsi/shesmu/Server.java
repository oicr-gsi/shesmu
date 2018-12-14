package ca.on.oicr.gsi.shesmu;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.invoke.MethodType;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.ConstantDefinition.ConstantLoader;
import ca.on.oicr.gsi.shesmu.compiler.ImyhatNode;
import ca.on.oicr.gsi.shesmu.compiler.NameDefinitions;
import ca.on.oicr.gsi.shesmu.compiler.Parser;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.FileWatcher;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedArbitraryDefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.RuntimeBinding;
import ca.on.oicr.gsi.shesmu.util.server.ActionProcessor;
import ca.on.oicr.gsi.shesmu.util.server.ActionProcessor.Filter;
import ca.on.oicr.gsi.shesmu.util.server.CurrentAlerts;
import ca.on.oicr.gsi.shesmu.util.server.EmergencyThrottler;
import ca.on.oicr.gsi.shesmu.util.server.FunctionRequest;
import ca.on.oicr.gsi.shesmu.util.server.FunctionRunner;
import ca.on.oicr.gsi.shesmu.util.server.FunctionRunnerCompiler;
import ca.on.oicr.gsi.shesmu.util.server.MasterRunner;
import ca.on.oicr.gsi.shesmu.util.server.MetroDiagram;
import ca.on.oicr.gsi.shesmu.util.server.Query;
import ca.on.oicr.gsi.shesmu.util.server.Query.FilterJson;
import ca.on.oicr.gsi.shesmu.util.server.StaticActions;
import ca.on.oicr.gsi.shesmu.util.server.TypeParseRequest;
import ca.on.oicr.gsi.shesmu.util.server.TypeParseResponse;
import ca.on.oicr.gsi.status.BasePage;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.Header;
import ca.on.oicr.gsi.status.NavigationMenu;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.gsi.status.ServerConfig;
import ca.on.oicr.gsi.status.StatusPage;
import ca.on.oicr.gsi.status.TablePage;
import ca.on.oicr.gsi.status.TableRowWriter;
import ca.on.oicr.gsi.status.TableWriter;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;

@SuppressWarnings("restriction")
public final class Server implements ServerConfig {

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

	public static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private final CompiledGenerator compiler;
	private final Map<String, ConstantLoader> constantLoaders = new HashMap<>();

	private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(
			10 * Runtime.getRuntime().availableProcessors());

	private final Map<String, FunctionRunner> functionRunners = new HashMap<>();

	private final Semaphore inputDownloadSemaphore = new Semaphore(Runtime.getRuntime().availableProcessors() / 2 + 1);

	private final MasterRunner master;

	private final ActionProcessor processor = new ActionProcessor(localname());

	private final HttpServer server;

	private final StaticActions staticActions = new StaticActions(processor);

	public Server(int port) throws IOException {
		server = HttpServer.create(new InetSocketAddress(port), 0);
		server.setExecutor(executor);
		compiler = new CompiledGenerator(executor);
		master = new MasterRunner(compiler, processor);

		add("/", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new StatusPage(this) {

					@Override
					protected void emitCore(SectionRenderer renderer) throws XMLStreamException {
						renderer.link("Emergency Stop", EmergencyThrottler.stopped() ? "/resume" : "/stopstopstop",
								EmergencyThrottler.stopped() ? "▶ Resume" : "⏹ STOP ALL ACTIONS");
						FileWatcher.DATA_DIRECTORY.paths()
								.forEach(path -> renderer.line("Data Directory", path.toString()));
						compiler.errorHtml(renderer);
					}

					@Override
					public Stream<ConfigurationSection> sections() {
						return Stream.<Supplier<Stream<? extends LoadedConfiguration>>>of(//
								InputFormatDefinition::allConfiguration, //
								Throttler::services, //
								DefinitionRepository::sources, //
								DumperSource::sources, //
								SourceLocation::configuration, //
								AlertSink::sinks, //
								() -> Stream.of(staticActions)//
						)//
								.flatMap(Supplier::get)//
								.flatMap(LoadedConfiguration::listConfiguration);
					}
				}.renderPage(os);
			}
		});

		add("/olivedash", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						compiler.dashboard().forEach(fileTable -> {
							try {
								writer.writeStartElement("h1");
								writer.writeCharacters(fileTable.filename());
								writer.writeEndElement();
								TableWriter.render(writer, (row) -> {
									row.write(false, "Input format", fileTable.format().name());
									row.write(false, "Last Compiled", fileTable.timestamp().toString());
									if (CompiledGenerator.didFileTimeout(fileTable.filename())) {
										row.write(
												Collections
														.singleton(new Pair<>("style", "color:red; font-weight:bold;")),
												"Run time", "TIMED OUT");
									}
								});
								writer.writeStartElement("p");
								writer.writeAttribute("onclick", "toggleBytecode(this)");
								writer.writeCharacters("⊞ Bytecode");
								writer.writeEndElement();
								writer.writeStartElement("pre");
								writer.writeAttribute("class", "json");
								writer.writeAttribute("style", "display:none");
								writer.writeCharacters(fileTable.bytecode());
								writer.writeEndElement();
							} catch (XMLStreamException e) {
								throw new RuntimeException(e);
							}
							long inputCount = (long) CompiledGenerator.INPUT_RECORDS.labels(fileTable.format().name())
									.get();

							fileTable.olives().forEach(olive -> {
								try {
									if (olive.producesActions()) {
										writer.writeStartElement("p");
										writer.writeAttribute("id",
												String.format("%1$s:%2$d:%3$d:%4$d", fileTable.filename(), olive.line(),
														olive.column(), fileTable.timestamp().toEpochMilli()));
										writer.writeAttribute("class", "olive");

										String filterForOlive = String.format(
												"filterForOlive('%1$s', %2$d, %3$d, %4$d)", fileTable.filename(),
												olive.line(), olive.column(), fileTable.timestamp().toEpochMilli());

										for (Pair<String, String> button : Arrays.asList(
												new Pair<>("listActionsPopup", "🔍 List Actions"),
												new Pair<>("queryStatsPopup", "📈 Stats on Actions"))) {
											writer.writeStartElement("span");
											writer.writeAttribute("class", "load");
											writer.writeAttribute("onclick",
													button.first() + "(" + filterForOlive + ")");
											writer.writeCharacters(button.second());
											writer.writeEndElement();
										}
										writer.writeEndElement();
									}

									writer.writeStartElement("div");
									writer.writeAttribute("class", "indent");
									writer.writeAttribute("style", "overflow-x:auto");
									MetroDiagram.draw(writer, fileTable.filename(), fileTable.timestamp(), olive,
											inputCount, fileTable.format());
									writer.writeEndElement();
								} catch (XMLStreamException e) {
									throw new RuntimeException(e);
								}
							});
						});

					}
				}.renderPage(os);
			}
		});

		add("/actiondash", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					@Override
					public Stream<Header> headers() {
						return Stream.of(Header.jsModule("import {" + //
						"addLocationForm," + //
						"addTypeForm," + //
						"clearActionStates," + //
						"clearLocations," + //
						"clearTypes," + //
						"fillNewTypeSelect," + //
						"listActions," + //
						"queryStats," + //
						"showQuery" + //
						"} from \"./shesmu.js\";" + //
						"fillNewTypeSelect();" + //
						"document.getElementById(\"newLocation\").addEventListener(\"keyup\", function(ev) {" + //
						"  if (ev.keyCode === 13) {" + //
						"    addLocationForm();" + //
						"  }" + //
						"});" + //
						"document.getElementById(\"newLocationButton\").addEventListener(\"click\", addLocationForm);" + //
						"document.getElementById(\"addTypeButton\").addEventListener(\"click\", addTypeForm);" + //
						"document.getElementById(\"clearActionStatesButton\").addEventListener(\"click\", clearActionStates);"
								+ //
						"document.getElementById(\"clearLocationsButton\").addEventListener(\"click\", clearLocations);"
								+ //
						"document.getElementById(\"clearTypesButton\").addEventListener(\"click\", clearTypes);" + //
						"document.getElementById(\"listActionsButton\").addEventListener(\"click\", listActions);" + //
						"document.getElementById(\"queryStatsButton\").addEventListener(\"click\", queryStats);" + //
						"document.getElementById(\"showQueryButton\").addEventListener(\"click\", showQuery);"));
					}

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						writer.writeStartElement("h1");
						writer.writeCharacters("Search");
						writer.writeEndElement();
						writer.writeStartElement("p");
						writer.writeCharacters("For dates, use the same formats used in specifying dates in olives: ");
						writer.writeStartElement("tt");
						writer.writeCharacters("Date");
						writer.writeEndElement();
						writer.writeCharacters(", ");
						writer.writeStartElement("tt");
						writer.writeCharacters("EpochSecond");
						writer.writeEndElement();
						writer.writeCharacters(", ");
						writer.writeStartElement("tt");
						writer.writeCharacters("EpochMilli");
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("table");
						writer.writeAttribute("class", "even");
						writeDateRange(writer, "added", "Time Since Action was Last Generated by an Olive");
						writeDateRange(writer, "checked", "Last Time Action was Last Run");
						writeDateRange(writer, "statuschanged", "Last Time Action's Status Changed");

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters("Status");
						writer.writeEndElement();
						writer.writeStartElement("td");
						for (ActionState state : ActionState.values()) {
							writer.writeStartElement("label");
							writer.writeAttribute("class", "state_" + state.name().toLowerCase());
							writer.writeStartElement("input");
							writer.writeAttribute("type", "checkbox");
							writer.writeAttribute("id", "include_" + state.name());
							writer.writeCharacters(
									state.name().substring(0, 1) + state.name().substring(1).toLowerCase());
							writer.writeEndElement();
							writer.writeEndElement();
						}
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "clearActionStatesButton");
						writer.writeCharacters("⌫");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters("Type");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("span");
						writer.writeAttribute("id", "types");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "clearTypesButton");
						writer.writeCharacters("⌫");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("select");
						writer.writeAttribute("id", "newType");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "addTypeButton");
						writer.writeCharacters("+");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters("Source Olive");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("span");
						writer.writeAttribute("id", "locations");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "clearLocationsButton");
						writer.writeCharacters("⌫");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("select");
						writer.writeAttribute("id", "newLocation");
						writer.writeComment("");
						final Instant now = Instant.now();
						processor.sources()//
								.collect(Collectors.groupingBy(SourceLocation::fileName)).entrySet().stream()//
								.flatMap(entry -> {
									final Query.LocationJson file = new Query.LocationJson();
									file.setFile(entry.getKey());
									final Instant max = entry.getValue().stream().map(SourceLocation::time)
											.max(Comparator.naturalOrder()).get();
									return Stream.concat(Stream.of(new Pair<>(entry.getKey(), file)),
											entry.getValue().stream()//
													.flatMap(location -> {
														final Query.LocationJson fileLineColTime = new Query.LocationJson();
														fileLineColTime.setFile(location.fileName());
														fileLineColTime.setLine(location.line());
														fileLineColTime.setColumn(location.column());
														fileLineColTime.setTime(location.time().toEpochMilli());

														final String timeLabel = location.time().equals(max) ? "current"
																: Duration.between(location.time(), now).toString();

														return Stream.of(new Pair<>(
																location.fileName() + ":" + location.line() + ":"
																		+ location.column() + "[" + timeLabel + "]",
																fileLineColTime));
													}));
								})//
								.sorted(Comparator.comparing(Pair::first))//
								.forEach(p -> {
									try {
										writer.writeStartElement("option");
										writer.writeAttribute("value",
												RuntimeSupport.MAPPER.writeValueAsString(p.second()));
										writer.writeCharacters(p.first());
										writer.writeEndElement();
									} catch (XMLStreamException | JsonProcessingException e) {
										e.printStackTrace();
									}

								});
						writer.writeEndElement();
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "newLocationButton");
						writer.writeCharacters("+");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "listActionsButton");
						writer.writeCharacters("🔍 List");
						writer.writeEndElement();
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "queryStatsButton");
						writer.writeCharacters("📈 Stats");
						writer.writeEndElement();
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("id", "showQueryButton");
						writer.writeCharacters("🛈 Show Request");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeEndElement();

						writer.writeStartElement("div");
						writer.writeAttribute("id", "results");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeStartElement("p");
						writer.writeCharacters("Click any cell or heading in summary tables to view matching results.");
						writer.writeEndElement();
					}

					private void writeDateRange(XMLStreamWriter writer, String name, String description)
							throws XMLStreamException {
						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters(description);
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("input");
						writer.writeAttribute("type", "text");
						writer.writeAttribute("id", name + "Start");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeCharacters(" to ");
						writer.writeStartElement("input");
						writer.writeAttribute("type", "text");
						writer.writeAttribute("id", name + "End");
						writer.writeComment("");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();
					}
				}.renderPage(os);
			}
		});

		add("/actiondefs", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						DefinitionRepository.allActions()//
								.sorted(Comparator.comparing(ActionDefinition::name))//
								.forEach(action -> {
									try {
										writer.writeStartElement("h1");
										writer.writeCharacters(action.name());
										writer.writeEndElement();
										writer.writeStartElement("p");
										writer.writeCharacters(action.description());
										writer.writeEndElement();

										writer.writeStartElement("table");
										writer.writeAttribute("class", "even");
										TableRowWriter row = new TableRowWriter(writer);
										action.parameters()//
												.sorted(Comparator.comparing(ActionParameterDefinition::name))//
												.forEach(p -> row.write(false, p.name(),
														p.type().name() + (p.required() ? " Required" : " Optional")));
										writer.writeEndElement();
									} catch (XMLStreamException e) {
										throw new RuntimeException(e);
									}
								});
					}
				}.renderPage(os);
			}
		});
		add("/inputdefs", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						InputFormatDefinition.formats()//
								.sorted(Comparator.comparing(InputFormatDefinition::name))//
								.forEach(format -> {
									try {
										writer.writeStartElement("h1");
										writer.writeCharacters(format.name());
										writer.writeEndElement();

										writer.writeStartElement("table");
										format.baseStreamVariables().sorted(Comparator.comparing(Target::name))
												.forEach(variable -> {
													try {
														writer.writeStartElement("tr");
														writer.writeStartElement("td");
														writer.writeCharacters(variable.name());
														writer.writeEndElement();
														writer.writeStartElement("td");
														writer.writeCharacters(variable.type().name());
														writer.writeEndElement();
														writer.writeStartElement("td");
														if (variable.flavour() == Flavour.STREAM_SIGNABLE) {
															writer.writeAttribute("title", "Included in signatures");
															writer.writeCharacters("✍️");
														}
														writer.writeEndElement();
														writer.writeEndElement();
													} catch (XMLStreamException e) {
														throw new RuntimeException(e);
													}
												});
										writer.writeEndElement();
									} catch (XMLStreamException e) {
										throw new RuntimeException(e);
									}
								});
					}
				}.renderPage(os);
			}
		});
		add("/signaturedefs", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new TablePage(this) {

					@Override
					protected void writeRows(TableRowWriter row) {
						NameDefinitions.signatureVariables()//
								.sorted(Comparator.comparing(SignatureVariable::name))//
								.forEach(variable -> {
									row.write(false, variable.name(), variable.type().name());
								});
					}
				}.renderPage(os);
			}
		});
		add("/constantdefs", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						DefinitionRepository.allConstants()//
								.sorted(Comparator.comparing(Target::name))//
								.forEach(constant -> {
									try {
										writer.writeStartElement("h1");
										writer.writeCharacters(constant.name());
										writer.writeEndElement();
										writer.writeStartElement("p");
										writer.writeCharacters(constant.description());
										writer.writeEndElement();
										writer.writeStartElement("table");
										writer.writeAttribute("class", "even");
										writer.writeStartElement("tr");
										writer.writeStartElement("td");
										writer.writeCharacters("Type");
										writer.writeEndElement();
										writer.writeStartElement("td");
										writer.writeCharacters(constant.type().name());
										writer.writeEndElement();
										writer.writeEndElement();
										writer.writeStartElement("tr");
										writer.writeStartElement("td");
										writer.writeCharacters("Value");
										writer.writeEndElement();
										writer.writeStartElement("td");
										writer.writeStartElement("span");
										writer.writeAttribute("class", "load");
										writer.writeAttribute("onclick",
												String.format("fetchConstant('%s', this)", constant.name()));
										writer.writeCharacters("▶ Get");
										writer.writeEndElement();
										writer.writeStartElement("span");
										writer.writeEndElement();
										writer.writeEndElement();
										writer.writeEndElement();
										writer.writeEndElement();
									} catch (XMLStreamException e) {
										throw new RuntimeException(e);
									}
								});
					}
				}.renderPage(os);
			}
		});

		add("/functiondefs", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						DefinitionRepository.allFunctions()//
								.sorted(Comparator.comparing(FunctionDefinition::name))//
								.forEach(function -> {
									try {
										writer.writeStartElement("h1");
										writer.writeCharacters(function.name());
										writer.writeEndElement();
										writer.writeStartElement("p");
										writer.writeCharacters(function.description());
										writer.writeEndElement();
										writer.writeStartElement("table");
										writer.writeAttribute("class", "even");
										function.parameters().map(Pair.number()).forEach(p -> {
											try {
												writer.writeStartElement("tr");
												writer.writeStartElement("td");
												writer.writeCharacters("Argument " + Integer.toString(p.first() + 1)
														+ ": " + p.second().description());
												writer.writeEndElement();
												writer.writeStartElement("td");
												writer.writeCharacters(p.second().type().name());
												writer.writeStartElement("input");
												writer.writeAttribute("type", "text");
												writer.writeAttribute("id", function.name() + "$" + p.first());
												writer.writeEndElement();
												writer.writeEndElement();
												writer.writeEndElement();
											} catch (XMLStreamException e) {
												throw new RuntimeException(e);
											}
										});
										writer.writeStartElement("tr");
										writer.writeStartElement("td");
										writer.writeCharacters("Result: " + function.returnType().name());
										writer.writeEndElement();
										writer.writeStartElement("td");
										writer.writeStartElement("span");
										writer.writeAttribute("class", "load");
										writer.writeAttribute("onclick",
												String.format("runFunction('%s', this, %s)", function.name(),
														function.parameters().map(p -> p.type().javaScriptParser())
																.collect(Collectors.joining(",", "[", "]"))));
										writer.writeCharacters("▶ Run");
										writer.writeEndElement();
										writer.writeStartElement("span");
										writer.writeEndElement();
										writer.writeEndElement();
										writer.writeEndElement();
										writer.writeEndElement();
									} catch (XMLStreamException e) {
										throw new RuntimeException(e);
									}
								});

					}
				}.renderPage(os);
			}
		});
		add("/dumpdefs", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new TablePage(this) {

					@Override
					protected void writeRows(TableRowWriter row) {
						DefinitionRepository.sources()//
								.map(x -> x.getClass().getName())//
								.sorted()//
								.forEach(x -> {
									row.write(false, x);
								});
					}
				}.renderPage(os);
			}
		});
		add("/dumprtb", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new TablePage(this) {

					@Override
					protected void writeRows(TableRowWriter row) {
						RuntimeBinding.dump()//
								.sorted(Comparator
										.<Pair<Pair<String, Class<?>>, MethodType>, String>comparing(
												x -> x.first().first())
										.thenComparing(x -> x.first().second().getName()))//
								.forEach(x -> {
									row.write(false, x.first().first(), x.first().second().getName(),
											x.second().toMethodDescriptorString());
								});
					}
				}.renderPage(os);
			}
		});
		add("/dumpadr", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new TablePage(this) {

					@Override
					protected void writeRows(TableRowWriter row) {
						FileBackedArbitraryDefinitionRepository.dump()//
								.sorted(Comparator.comparing(Pair::first))//
								.forEach(x -> {
									row.write(false, x.first(), x.second().toMethodDescriptorString());
								});
					}
				}.renderPage(os);
			}
		});
		add("/typedefs", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						writer.writeStartElement("p");
						writer.writeCharacters(
								"Shesmu's type can be written two ways: human-friendly (e.g., “[string]”) and machine-friendly (e.g., “as”). The machine-friendly format is called a descriptor. When writing olives, the human-friendly format is used. When writing plugins for Shesmu, the descriptors are used. Use this page to convert or validate types in either format. For any input format, every variable's type is available as the name of the variable suffixed by “_type” (e.g., “ius” has “ius_type”).");
						writer.writeEndElement();

						writer.writeStartElement("table");

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters("Format");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("select");
						writer.writeAttribute("id", "format");

						writer.writeStartElement("option");
						writer.writeAttribute("value", "0");
						writer.writeCharacters("Descriptor");
						writer.writeEndElement();
						writer.writeStartElement("option");
						writer.writeAttribute("value", "");
						writer.writeCharacters("Human-friendly");
						writer.writeEndElement();

						InputFormatDefinition.formats()//
								.sorted(Comparator.comparing(InputFormatDefinition::name))
								//
								.forEach(format -> {
									try {
										writer.writeStartElement("option");
										writer.writeAttribute("value", format.name());
										writer.writeCharacters("Human-field with types from " + format.name());
										writer.writeEndElement();
									} catch (XMLStreamException e) {
										throw new RuntimeException(e);
									}

								});

						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters("Text to parse");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("input");
						writer.writeAttribute("type", "text");
						writer.writeAttribute("id", "typeValue");
						writer.writeEndElement();
						writer.writeStartElement("span");
						writer.writeAttribute("class", "load");
						writer.writeAttribute("onclick", "parseType();");
						writer.writeCharacters("Parse");
						writer.writeEndElement();

						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters("Human-friendly Type");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("span");
						writer.writeAttribute("id", "humanType");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeStartElement("tr");
						writer.writeStartElement("td");
						writer.writeCharacters("Descriptor");
						writer.writeEndElement();
						writer.writeStartElement("td");
						writer.writeStartElement("span");
						writer.writeAttribute("id", "descriptorType");
						writer.writeEndElement();
						writer.writeEndElement();
						writer.writeEndElement();

						writer.writeEndElement();
					}
				}.renderPage(os);
			}
		});

		add("/alerts", t -> {
			t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				new BasePage(this) {

					private void labelsToHtml(XMLStreamWriter writer, Map<String, String> labels)
							throws XMLStreamException {
						for (Entry<String, String> entry : labels.entrySet()) {
							writer.writeStartElement("span");
							writer.writeAttribute("class", "label");
							writer.writeCharacters(entry.getKey());
							writer.writeCharacters(" = ");
							writer.writeCharacters(entry.getValue());
							writer.writeEndElement();
							writer.writeEmptyElement("br");
						}
					}

					@Override
					protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
						writer.writeStartElement("table");
						processor.alerts(a -> {
							try {
								writer.writeStartElement("tr");
								writer.writeAttribute("id", "alert-" + a.id());
								writer.writeAttribute("class", a.isLive() ? "live alert" : "expired alert");
								writer.writeStartElement("td");
								writer.writeCharacters(a.id());
								writer.writeEndElement();
								writer.writeStartElement("td");
								labelsToHtml(writer, a.getLabels());
								writer.writeEndElement();
								writer.writeStartElement("td");
								labelsToHtml(writer, a.getAnnotations());
								writer.writeEndElement();
								writer.writeStartElement("td");
								writer.writeCharacters(a.getStartsAt());
								writer.writeEndElement();
								writer.writeStartElement("td");
								writer.writeCharacters(a.expiryTime());
								writer.writeEndElement();
								writer.writeEndElement();
							} catch (XMLStreamException e) {
								throw new RuntimeException(e);
							}
						});
						writer.writeEndElement();
					}

				}.renderPage(os);
			}
		});

		addJson("/actions", (mapper, query) -> {
			final ArrayNode array = mapper.createArrayNode();
			DefinitionRepository.allActions().forEach(actionDefinition -> {
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

		addJson("/constants", (mapper, query) -> {
			final ArrayNode array = mapper.createArrayNode();
			DefinitionRepository.allConstants().forEach(constant -> {
				final ObjectNode obj = array.addObject();
				obj.put("name", constant.name());
				obj.put("description", constant.description());
				obj.put("type", constant.type().descriptor());
			});
			return array;
		});

		addJson("/functions", (mapper, query) -> {
			final ArrayNode array = mapper.createArrayNode();
			DefinitionRepository.allFunctions().forEach(function -> {
				final ObjectNode obj = array.addObject();
				obj.put("name", function.name());
				obj.put("description", function.description());
				obj.put("return", function.returnType().descriptor());
				final ArrayNode parameters = obj.putArray("parameters");
				function.parameters().forEach(p -> {
					final ObjectNode parameter = parameters.addObject();
					parameter.put("type", p.type().descriptor());
					parameter.put("description", p.description());
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
			final Query query;
			try {
				query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), Query.class);
			} catch (final Exception e) {
				e.printStackTrace();
				t.sendResponseHeaders(400, 0);
				try (OutputStream os = t.getResponseBody()) {
				}
				return;
			}
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				RuntimeSupport.MAPPER.writeValue(os, query.perform(RuntimeSupport.MAPPER, processor));
			}
		});

		add("/stats", t -> {
			final FilterJson[] filters;
			try {
				filters = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), FilterJson[].class);
			} catch (final Exception e) {
				t.sendResponseHeaders(400, 0);
				try (OutputStream os = t.getResponseBody()) {
				}
				return;
			}
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				RuntimeSupport.MAPPER.writeValue(os, processor.stats(RuntimeSupport.MAPPER,
						Stream.of(filters).filter(Objects::nonNull).map(FilterJson::convert).toArray(Filter[]::new)));
			}
		});

		add("/currentalerts", t -> {
			t.getResponseHeaders().set("Content-type", "application/json");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				CurrentAlerts.pump(os);
			}
		});

		add("/constant", t -> {
			final String query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
			ConstantLoader loader;
			if (constantLoaders.containsKey(query)) {
				loader = constantLoaders.get(query);
			} else {
				loader = DefinitionRepository.allConstants()//
						.filter(c -> c.name().equals(query))//
						.findFirst()//
						.map(ConstantDefinition::compile)//
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
				runner = DefinitionRepository.allFunctions()//
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

		addJson("/variables", (mapper, query) -> {
			final ObjectNode node = mapper.createObjectNode();
			InputFormatDefinition.formats().forEach(source -> {
				final ObjectNode sourceNode = node.putObject(source.name());

				source.baseStreamVariables().forEach(variable -> {
					sourceNode.put(variable.name(), variable.type().descriptor());
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
			final TypeParseRequest request = RuntimeSupport.MAPPER.readValue(t.getRequestBody(),
					TypeParseRequest.class);
			Imyhat type;
			if (request.getFormat() == null || request.getFormat().equals("0")) {
				type = Imyhat.parse(request.getValue());
				t.getResponseHeaders().set("Content-type", "application/json");
			} else {
				Optional<Function<String, Imyhat>> existingTypes = request.getFormat().isEmpty()
						? Optional.of(n -> null)
						: InputFormatDefinition.formats()//
								.filter(format -> format.name().equals(request.getFormat()))//
								.findAny()//
								.map(format -> Stream.<Target>concat(//
										format.baseStreamVariables(), //
										NameDefinitions.signatureVariables())//
										.collect(Collectors.toMap(udt -> udt.name() + "_type", Target::type))::get);
				type = existingTypes.flatMap(types -> {
					AtomicReference<ImyhatNode> node = new AtomicReference<>();
					Parser parser = Parser.start(request.getValue(), (l, c, m) -> {
					})//
							.whitespace()//
							.then(ImyhatNode::parse, node::set)//
							.whitespace();
					if (parser.isGood()) {
						return Optional.of(node.get().render(types, m -> {
						}));
					}
					return Optional.empty();
				}).orElse(Imyhat.BAD);
			}
			if (type.isBad()) {
				t.sendResponseHeaders(400, 0);
				try (OutputStream os = t.getResponseBody()) {
				}
			} else {
				t.sendResponseHeaders(200, 0);
				try (OutputStream os = t.getResponseBody()) {
					RuntimeSupport.MAPPER.writeValue(os, new TypeParseResponse(type));
				}
			}

		});

		add("/actions.js", t -> {
			t.getResponseHeaders().set("Content-type", "text/javascript");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody(); PrintStream writer = new PrintStream(os, false, "UTF-8")) {
				writer.println(
						"import { jsonParameters, link, text, title } from './shesmu.js';\nexport const actionRender = new Map();\n");
				DefinitionRepository.sources().forEach(repository -> {
					writer.print("// ");
					writer.print(repository.getClass().getCanonicalName());
					writer.println();
					repository.writeJavaScriptRenderer(writer);
					writer.println();
				});
			}
		});

		add("/resume", new EmergencyThrottlerHandler(false));
		add("/stopstopstop", new EmergencyThrottlerHandler(true));
		add("/main.css", "text/css");
		add("/shesmu.js", "text/javascript");
		add("/shesmu.svg", "image/svg+xml");
		add("/favicon.png", "image/png");
		add("/swagger.json", "application/json");
		add("/api-docs/favicon-16x16.png", "image/png");
		add("/api-docs/favicon-32x32.png", "image/png");
		add("/api-docs/index.html", "text/html");
		add("/api-docs/oauth2-redirect.html", "text/html");
		add("/api-docs/swagger-ui-bundle.js", "text/javascript");
		add("/api-docs/swagger-ui-standalone-preset.js", "text/javascript");
		add("/api-docs/swagger-ui.css", "text/css");
		add("/api-docs/swagger-ui.js", "text/javascript");

	}

	/**
	 * Add a new service endpoint with Prometheus monitoring
	 */
	private void add(String url, HttpHandler handler) {
		server.createContext(url, t -> {
			try (AutoCloseable timer = responseTime.start(url)) {
				handler.handle(t);
			} catch (final Exception e) {
				e.printStackTrace();
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
	private void addJson(String url, BiFunction<ObjectMapper, String, JsonNode> fetcher) {
		add(url, t -> {

			final JsonNode node = fetcher.apply(RuntimeSupport.MAPPER, t.getRequestURI().getQuery());
			t.getResponseHeaders().set("Content-type", "application/json");
			t.sendResponseHeaders(200, 0);
			try (OutputStream os = t.getResponseBody()) {
				RuntimeSupport.MAPPER.writeValue(os, node);
			}
		});
	}

	@Override
	public Stream<Header> headers() {
		return Stream.of(Header.cssFile("/main.css"), //
				Header.faviconPng(16), //
				Header.jsModule(
						"import {parser, fetchConstant, parseType, toggleBytecode, runFunction, filterForOlive, listActionsPopup, queryStatsPopup} from './shesmu.js'; window.parser = parser; window.fetchConstant = fetchConstant; window.parseType = parseType; window.toggleBytecode = toggleBytecode; window.runFunction = runFunction; window.filterForOlive = filterForOlive; window.listActionsPopup = listActionsPopup; window.queryStatsPopup = queryStatsPopup;"));
	}

	private String localname() {
		URIBuilder builder = null;
		final String url = System.getenv("LOCAL_URL");
		if (url != null) {
			try {
				builder = new URIBuilder(url);
			} catch (final URISyntaxException e) {
				e.printStackTrace();
			}
		}
		if (builder == null) {
			builder = new URIBuilder();
			builder.setScheme("http");
			builder.setHost("localhost");
			builder.setPort(8081);
			builder.setPath("");
			try {
				builder.setHost(InetAddress.getLocalHost().getCanonicalHostName());
			} catch (final UnknownHostException eh1) {
				eh1.printStackTrace();
				try {
					builder.setHost(InetAddress.getLocalHost().getHostAddress());
				} catch (final UnknownHostException eh2) {
					eh2.printStackTrace();
				}
			}
		}
		builder.setPath(builder.getPath() + "/alerts");
		try {
			return builder.build().toASCIIString();
		} catch (final URISyntaxException e) {
			e.printStackTrace();
			return "http://localhost:8081/alerts";
		}
	}

	@Override
	public String name() {
		return "Shesmu";
	}

	@Override
	public Stream<NavigationMenu> navigation() {
		return Stream.of(//
				NavigationMenu.item("olivedash", "Olives"), //
				NavigationMenu.item("actiondash", "Actions"), //
				NavigationMenu.item("alerts", "Alerts"), //
				NavigationMenu.submenu("Definitions", //
						NavigationMenu.item("actiondefs", "Actions"), //
						NavigationMenu.item("constantdefs", "Constants"), //
						NavigationMenu.item("functiondefs", "Functions"), //
						NavigationMenu.item("inputdefs", "Input Formats"), //
						NavigationMenu.item("signaturedefs", "Signatures")), //
				NavigationMenu.submenu("Tools", //
						NavigationMenu.item("typedefs", "Type Converter"), //
						NavigationMenu.item("dumpdefs", "Detected Definition Plugins"), //
						NavigationMenu.item("dumprtb", "Runtime Binding Spy"), //
						NavigationMenu.item("dumpadr", "Arbitrary Binding Spy")));
	}

	public void start() {
		System.out.println("Starting server...");
		server.start();
		System.out.println("Waiting for files to be scanned...");
		DefinitionRepository.sources().count();
		Throttler.services().count();
		try {
			Thread.sleep(5000);
		} catch (final InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		final long pluginCount = DefinitionRepository.allConstants().count();
		System.out.printf("Found %d plugins\n", pluginCount);
		final long throttlerCount = Throttler.services().count();
		System.out.printf("Found %d throttler\n", throttlerCount);
		System.out.println("Compiling script...");
		compiler.start();
		staticActions.start();
		System.out.println("Starting action processor...");
		processor.start(executor);
		System.out.println("Starting scheduler...");
		master.start(executor);
	}

}
