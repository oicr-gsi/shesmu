package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.ImyhatNode;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition.ConstantLoader;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.*;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Filter;
import ca.on.oicr.gsi.shesmu.server.Query.FilterJson;
import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.server.plugins.PluginManager;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.FileWatcher;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
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
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

@SuppressWarnings("restriction")
public final class Server implements ServerConfig, ActionServices {
  private static final Gauge stopGauge =
      Gauge.build("shesmu_emergency_throttler", "Whether the emergency throttler is engaged.")
          .register();
  private volatile boolean emergencyStop;

  private void emergencyThrottle(boolean stopped) {
    this.emergencyStop = stopped;
    stopGauge.set(stopped ? 1 : 0);
  }

  private class EmergencyThrottlerHandler implements HttpHandler {
    private final boolean state;

    public EmergencyThrottlerHandler(boolean state) {
      super();
      this.state = state;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
      emergencyThrottle(state);
      t.getResponseHeaders().set("Location", "/");
      t.sendResponseHeaders(302, -1);
    }
  }

  public static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

  private static final Map<String, Instant> INFLIGHT = new ConcurrentHashMap<>();

  private static final LatencyHistogram responseTime =
      new LatencyHistogram(
          "shesmu_http_request_time", "The time to respond to an HTTP request.", "url");

  public static Runnable inflight(String name) {
    INFLIGHT.putIfAbsent(name, Instant.now());
    return () -> INFLIGHT.remove(name);
  }

  public static AutoCloseable inflightCloseable(String name) {
    return inflight(name)::run;
  }

  public static void main(String[] args) throws Exception {
    DefaultExports.initialize();

    final Server s = new Server(8081);
    s.start();
  }

  private final CompiledGenerator compiler;
  private final Map<String, ConstantLoader> constantLoaders = new HashMap<>();
  private final ScheduledExecutorService executor =
      new ScheduledThreadPoolExecutor(10 * Runtime.getRuntime().availableProcessors());
  private final DefinitionRepository definitionRepository;
  private final Map<String, FunctionRunner> functionRunners = new HashMap<>();

  private final Semaphore inputDownloadSemaphore =
      new Semaphore(Runtime.getRuntime().availableProcessors() / 2 + 1);

  private final MasterRunner master;

  private final PluginManager pluginManager = new PluginManager();

  private final ActionProcessor processor;

  private final AutoUpdatingDirectory<SavedSearch> savedSearches =
      new AutoUpdatingDirectory<>(".search", SavedSearch::new);

  private final HttpServer server;

  private final StaticActions staticActions;

  public Server(int port) throws IOException {
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(executor);
    definitionRepository = DefinitionRepository.concat(new StandardDefinitions(), pluginManager);
    compiler = new CompiledGenerator(executor, definitionRepository);
    processor = new ActionProcessor(localname(), pluginManager, this);
    staticActions = new StaticActions(processor, definitionRepository);
    final InputProvider inputProvider =
        format ->
            Stream.concat(
                    AnnotatedInputFormatDefinition.formats(), Stream.of(pluginManager, processor))
                .flatMap(source -> source.fetch(format));
    master =
        new MasterRunner(
            compiler,
            new OliveServices() {
              @Override
              public boolean isOverloaded(String... services) {
                return processor.isOverloaded(services)
                    || pluginManager.isOverloaded(new HashSet<>(Arrays.asList(services)));
              }

              @Override
              public Dumper findDumper(String name, Imyhat... types) {
                return pluginManager
                    .findDumper(name, types)
                    .orElse(
                        new Dumper() {
                          @Override
                          public void stop() {
                            // Do nothing
                          }

                          @Override
                          public void write(Object... values) {
                            // Do nothing
                          }
                        });
              }

              @Override
              public boolean accept(
                  Action action, String filename, int line, int column, long time) {
                return processor.accept(action, filename, line, column, time);
              }

              @Override
              public boolean accept(String[] labels, String[] annotation, long ttl)
                  throws Exception {
                return processor.accept(labels, annotation, ttl);
              }
            },
            inputProvider);

    add(
        "/",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new StatusPage(this) {

              @Override
              protected void emitCore(SectionRenderer renderer) throws XMLStreamException {
                renderer.link(
                    "Emergency Stop",
                    emergencyStop ? "/resume" : "/stopstopstop",
                    emergencyStop ? "▶ Resume" : "⏹ STOP ALL ACTIONS");
                FileWatcher.DATA_DIRECTORY
                    .paths()
                    .forEach(path -> renderer.line("Data Directory", path.toString()));
                compiler.errorHtml(renderer);
              }

              @Override
              public Stream<ConfigurationSection> sections() {
                return Stream.concat(
                    pluginManager.listConfiguration(), staticActions.listConfiguration());
              }
            }.renderPage(os);
          }
        });

    add(
        "/inflightdash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new TablePage(this, "Inflight Process", "Started", "Duration") {
              final Instant now = Instant.now();

              @Override
              protected void writeRows(TableRowWriter writer) {
                for (Entry<String, Instant> inflight : INFLIGHT.entrySet()) {
                  writer.write(
                      false,
                      inflight.getKey(),
                      inflight.getValue().toString(),
                      Duration.between(inflight.getValue(), now).toString());
                }
              }
            }.renderPage(os);
          }
        });
    add(
        "/olivedash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("table");
                writer.writeAttribute("class", "even");
                compiler
                    .dashboard()
                    .forEach(
                        fileTable -> {
                          try {
                            writer.writeStartElement("tr");
                            writer.writeStartElement("th");
                            writer.writeAttribute("colspan", "2");
                            writer.writeCharacters(fileTable.filename());
                            writer.writeEndElement();
                            writer.writeEndElement();
                          } catch (XMLStreamException e) {
                            throw new RuntimeException(e);
                          }
                          fileTable
                              .olives()
                              .forEach(
                                  olive -> {
                                    try {

                                      writer.writeStartElement("tr");
                                      writer.writeAttribute("style", "cursor: pointer;");
                                      writer.writeAttribute(
                                          "onclick",
                                          String.format(
                                              "location.hash = '%1$s:%2$d:%3$d:%4$d'",
                                              fileTable.filename(),
                                              olive.line(),
                                              olive.column(),
                                              fileTable.timestamp().toEpochMilli()));
                                      writer.writeStartElement("td");
                                      writer.writeCharacters(olive.syntax());
                                      writer.writeEndElement();
                                      writer.writeStartElement("td");
                                      writer.writeCharacters(
                                          String.format("%d:%d", olive.line(), olive.column()));
                                      writer.writeEndElement();
                                      writer.writeEndElement();
                                    } catch (XMLStreamException e) {
                                      throw new RuntimeException(e);
                                    }
                                  });
                        });
                writer.writeEndElement();
                final Map<String, Instant> currentOlives =
                    compiler
                        .dashboard()
                        .collect(Collectors.toMap(FileTable::filename, FileTable::timestamp));
                final List<SourceLocation> deadPauses =
                    processor
                        .pauses()
                        .filter(
                            pause ->
                                !currentOlives
                                    .getOrDefault(pause.fileName(), Instant.EPOCH)
                                    .equals(pause.time()))
                        .collect(Collectors.toList());
                if (!deadPauses.isEmpty()) {
                  writer.writeStartElement("div");
                  writer.writeAttribute("class", "state_throttled inset");
                  writer.writeStartElement("h1");
                  writer.writeCharacters("⚠ Pauses for Old Olives");
                  writer.writeEndElement();
                  writer.writeStartElement("p");
                  writer.writeCharacters(
                      "The following olives were paused but the olives have been replaced or deleted. They may still be throttling actions from running. If a new olive is producing the same actions, then the actions will still be throttled! Use the ");
                  writer.writeStartElement("i");
                  writer.writeCharacters("Stats on Actions");
                  writer.writeEndElement();
                  writer.writeCharacters(" button above to check for throttled actions.");
                  writer.writeEndElement();
                  writer.writeStartElement("table");
                  writer.writeStartElement("tbody");

                  writer.writeStartElement("tr");
                  writer.writeStartElement("th");
                  writer.writeCharacters("Filename");
                  writer.writeEndElement();
                  writer.writeStartElement("th");
                  writer.writeCharacters("Line");
                  writer.writeEndElement();
                  writer.writeStartElement("th");
                  writer.writeCharacters("Column");
                  writer.writeEndElement();
                  writer.writeStartElement("th");
                  writer.writeCharacters("Timestamp");
                  writer.writeEndElement();
                  writer.writeStartElement("th");
                  writer.writeComment("delete");
                  writer.writeEndElement();
                  writer.writeEndElement();
                  for (SourceLocation deadPause : deadPauses) {
                    writer.writeStartElement("tr");
                    writer.writeStartElement("td");
                    writer.writeCharacters(deadPause.fileName());
                    writer.writeEndElement();
                    writer.writeStartElement("td");
                    writer.writeCharacters(Integer.toString(deadPause.line()));
                    writer.writeEndElement();
                    writer.writeStartElement("td");
                    writer.writeCharacters(Integer.toString(deadPause.column()));
                    writer.writeEndElement();
                    writer.writeStartElement("td");
                    writer.writeCharacters(deadPause.time().toString());
                    writer.writeEndElement();
                    writer.writeStartElement("td");
                    writer.writeStartElement("span");
                    writer.writeAttribute("class", "load danger");
                    writer.writeAttribute(
                        "onclick",
                        String.format(
                            "clearDeadPause(this.parentNode.parentNode, { 'file': '%1$s', 'line': %2$d, 'column': %3$d, 'time': %4$d }, false)",
                            deadPause.fileName(),
                            deadPause.line(),
                            deadPause.column(),
                            deadPause.time().toEpochMilli()));
                    writer.writeCharacters("▶ Resume Actions");
                    writer.writeEndElement();
                    writer.writeStartElement("span");
                    writer.writeAttribute("class", "load danger");
                    writer.writeAttribute(
                        "onclick",
                        String.format(
                            "clearDeadPause(this.parentNode.parentNode, { 'file': '%1$s', 'line': %2$d, 'column': %3$d, 'time': %4$d }, true)",
                            deadPause.fileName(),
                            deadPause.line(),
                            deadPause.column(),
                            deadPause.time().toEpochMilli()));
                    writer.writeCharacters("☠️ PURGE ACTIONS");
                    writer.writeEndElement();
                    writer.writeEndElement();
                    writer.writeEndElement();
                    writer.writeEndElement();
                    writer.writeEndElement();
                  }

                  writer.writeEndElement();
                }

                compiler
                    .dashboard()
                    .forEach(
                        fileTable -> {
                          final boolean maybeHasDeadPauses =
                              deadPauses
                                  .stream()
                                  .anyMatch(pause -> pause.fileName().equals(fileTable.filename()));
                          try {
                            writer.writeStartElement("h1");
                            writer.writeCharacters(fileTable.filename());
                            writer.writeEndElement();
                            TableWriter.render(
                                writer,
                                (row) -> {
                                  row.write(false, "Input format", fileTable.format().name());
                                  row.write(
                                      false, "Last Compiled", fileTable.timestamp().toString());
                                  if (CompiledGenerator.didFileTimeout(fileTable.filename())) {
                                    row.write(
                                        Collections.singleton(
                                            new Pair<>("style", "color:red; font-weight:bold;")),
                                        "Run time",
                                        "TIMED OUT");
                                  }
                                });
                            writer.writeStartElement("p");
                            writer.writeAttribute("style", "cursor: pointer;");
                            writer.writeAttribute("onclick", "toggleBytecode(this)");
                            writer.writeCharacters("⊞ Bytecode");
                            writer.writeEndElement();
                            writer.writeStartElement("pre");
                            writer.writeAttribute("class", "json collapsed");
                            writer.writeCharacters(fileTable.bytecode());
                            writer.writeEndElement();
                          } catch (XMLStreamException e) {
                            throw new RuntimeException(e);
                          }
                          long inputCount =
                              (long)
                                  CompiledGenerator.INPUT_RECORDS
                                      .labels(fileTable.format().name())
                                      .get();

                          fileTable
                              .olives()
                              .forEach(
                                  olive -> {
                                    try {
                                      final String id =
                                          String.format(
                                              "%1$s:%2$d:%3$d:%4$d",
                                              fileTable.filename(),
                                              olive.line(),
                                              olive.column(),
                                              fileTable.timestamp().toEpochMilli());
                                      writer.writeStartElement("div");
                                      writer.writeAttribute("id", id);
                                      writer.writeAttribute("class", "olive");

                                      if (maybeHasDeadPauses) {
                                        writer.writeStartElement("p");
                                        writer.writeAttribute("class", "state_throttled inset");
                                        writer.writeCharacters(
                                            "An old olive was paused and it may affect actions for the olive below. Use ");
                                        writer.writeStartElement("i");
                                        writer.writeCharacters("Stats on Actions");
                                        writer.writeEndElement();
                                        writer.writeCharacters(" to check for throttled actions.");
                                        writer.writeEndElement();
                                      }

                                      String filterForOlive =
                                          String.format(
                                              "filterForOlive('%1$s', %2$d, %3$d, %4$d)",
                                              fileTable.filename(),
                                              olive.line(),
                                              olive.column(),
                                              fileTable.timestamp().toEpochMilli());

                                      writer.writeStartElement("p");
                                      writer.writeStartElement("a");
                                      writer.writeAttribute("href", "#" + id);
                                      writer.writeAttribute("class", "load accessory");
                                      writer.writeCharacters("🔗 Permalink");
                                      writer.writeEndElement();
                                      if (olive.producesActions()) {
                                        for (Pair<String, String> button :
                                            Arrays.asList(
                                                new Pair<>("listActionsPopup", "🔍 List Actions"),
                                                new Pair<>(
                                                    "queryStatsPopup", "📈 Stats on Actions"))) {
                                          writer.writeStartElement("span");
                                          writer.writeAttribute("class", "load");
                                          writer.writeAttribute(
                                              "onclick",
                                              button.first() + "(" + filterForOlive + ")");
                                          writer.writeCharacters(button.second());
                                          writer.writeEndElement();
                                        }
                                        final boolean isPaused =
                                            processor.isPaused(
                                                new SourceLocation(
                                                    fileTable.filename(),
                                                    olive.line(),
                                                    olive.column(),
                                                    fileTable.timestamp()));
                                        writer.writeStartElement("span");
                                        writer.writeAttribute("class", "load danger");
                                        writer.writeAttribute(
                                            "onclick",
                                            String.format(
                                                "pauseOlive(this, { 'file': '%1$s', 'line': %2$d, 'column': %3$d, 'time': %4$d} )",
                                                fileTable.filename(),
                                                olive.line(),
                                                olive.column(),
                                                fileTable.timestamp().toEpochMilli()));
                                        writer.writeAttribute(
                                            "is-paused", Boolean.toString(isPaused));
                                        writer.writeCharacters(
                                            isPaused ? "▶ Resume Actions" : "⏸ Pause Actions");
                                        writer.writeEndElement();

                                      } else {
                                        writer.writeCharacters(" Olive does not produce actions.");
                                      }
                                      writer.writeEndElement();

                                      writer.writeStartElement("div");
                                      writer.writeAttribute("style", "overflow-x:auto");
                                      MetroDiagram.draw(
                                          writer,
                                          pluginManager,
                                          fileTable.filename(),
                                          fileTable.timestamp(),
                                          olive,
                                          inputCount,
                                          fileTable.format());
                                      writer.writeEndElement();
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

    add(
        "/actiondash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {
              boolean lacksLocations;

              @Override
              public Stream<Header> headers() {
                final Instant now = Instant.now();
                final ObjectNode locationInfo = RuntimeSupport.MAPPER.createObjectNode();
                processor
                    .sources()
                    .collect(Collectors.groupingBy(SourceLocation::fileName))
                    .entrySet()
                    .stream()
                    .flatMap(
                        entry -> {
                          final Query.LocationJson file = new Query.LocationJson();
                          file.setFile(entry.getKey());
                          final Instant max =
                              entry
                                  .getValue()
                                  .stream()
                                  .map(SourceLocation::time)
                                  .max(Comparator.naturalOrder())
                                  .get();
                          return Stream.concat(
                              Stream.of(new Pair<>(entry.getKey(), file)),
                              entry
                                  .getValue()
                                  .stream()
                                  .flatMap(
                                      location -> {
                                        final Query.LocationJson fileLineColTime =
                                            new Query.LocationJson();
                                        fileLineColTime.setFile(location.fileName());
                                        fileLineColTime.setLine(location.line());
                                        fileLineColTime.setColumn(location.column());
                                        fileLineColTime.setTime(location.time().toEpochMilli());

                                        final String timeLabel =
                                            location.time().equals(max)
                                                ? "current"
                                                : Duration.between(location.time(), now).toString();

                                        return Stream.of(
                                            new Pair<>(
                                                location.fileName()
                                                    + ":"
                                                    + location.line()
                                                    + ":"
                                                    + location.column()
                                                    + "["
                                                    + timeLabel
                                                    + "]",
                                                fileLineColTime));
                                      }));
                        })
                    .sorted(Comparator.comparing(Pair::first))
                    .forEach(Pair.consume(locationInfo::putPOJO));
                lacksLocations = locationInfo.size() == 0;
                final ObjectNode searchInfo = RuntimeSupport.MAPPER.createObjectNode();
                savedSearches.stream().forEach(search -> search.write(searchInfo));
                String locations;
                String savedSearches;
                try {
                  locations = RuntimeSupport.MAPPER.writeValueAsString(locationInfo);
                  savedSearches = RuntimeSupport.MAPPER.writeValueAsString(searchInfo);
                } catch (JsonProcessingException e) {
                  e.printStackTrace();
                  locations = "[]";
                  savedSearches = "{}";
                }
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "initialiseActionDash"
                            + "} from \"./shesmu.js\";"
                            + "initialiseActionDash("
                            + locations
                            + ", "
                            + savedSearches
                            + ");"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeStartElement("span");
                writer.writeAttribute("id", "customSearchButton");
                writer.writeAttribute("class", "tab selected");
                writer.writeCharacters("Custom Search");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("id", "savedSearchButton");
                writer.writeAttribute("class", "tab");
                writer.writeCharacters("Saved Searches");
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("div");
                writer.writeAttribute("id", "customSearchPane");

                writer.writeStartElement("p");
                writer.writeCharacters(
                    "For dates, use the same formats used in specifying dates in olives: ");
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
                writer.writeCharacters(" (i.e. '");
                writer.writeStartElement("tt");
                writer.writeCharacters("Date 2020-02-20");
                writer.writeEndElement();
                writer.writeCharacters("', '");
                writer.writeStartElement("tt");
                writer.writeCharacters("EpochSecond 1582156800");
                writer.writeEndElement();
                writer.writeCharacters("')");
                writer.writeEndElement();

                writer.writeStartElement("table");
                writeDateRange(writer, "added", "Time Since Action was Last Generated by an Olive");
                writeDateRange(writer, "checked", "Last Time Action was Last Run");
                writeDateRange(writer, "statuschanged", "Last Time Action's Status Changed");
                writeDateRange(writer, "external", "External Last Modification Time");

                writer.writeStartElement("tr");
                writer.writeStartElement("td");
                writer.writeCharacters("State");
                writer.writeEndElement();
                writer.writeStartElement("td");
                writer.writeStartElement("span");
                writer.writeAttribute("class", "add");
                writer.writeCharacters("Add ▼");
                writer.writeStartElement("div");
                writer.writeAttribute("id", "newStates");
                writer.writeComment("");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("id", "currentStates");
                writer.writeComment("");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "clearStatesButton");
                writer.writeAttribute("title", "Clear States");
                writer.writeCharacters("⌫");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("tr");
                writer.writeStartElement("td");
                writer.writeCharacters("Action Type");
                writer.writeEndElement();
                writer.writeStartElement("td");
                writer.writeStartElement("span");
                writer.writeAttribute("class", "add");
                writer.writeCharacters("Add ▼");
                writer.writeStartElement("div");
                writer.writeAttribute("id", "newTypes");
                writer.writeComment("");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("id", "currentTypes");
                writer.writeComment("");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "clearTypesButton");
                writer.writeAttribute("title", "Clear Action Types");
                writer.writeCharacters("⌫");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("tr");
                writer.writeStartElement("td");
                writer.writeCharacters("Source Olive/Script");
                writer.writeEndElement();
                writer.writeStartElement("td");
                if (lacksLocations) {
                  writer.writeCharacters("No actions have been generated yet.");
                } else {
                  writer.writeStartElement("span");
                  writer.writeAttribute("class", "add");
                  writer.writeCharacters("Add ▼");
                  writer.writeStartElement("div");
                  writer.writeAttribute("id", "newLocations");
                  writer.writeComment("");
                  writer.writeEndElement();
                  writer.writeEndElement();
                  writer.writeStartElement("span");
                  writer.writeAttribute("id", "currentLocations");
                  writer.writeComment("");
                  writer.writeEndElement();
                  writer.writeStartElement("span");
                  writer.writeAttribute("class", "load accessory");
                  writer.writeAttribute("id", "clearLocationsButton");
                  writer.writeAttribute("title", "Clear Source Olives");
                  writer.writeCharacters("⌫");
                  writer.writeEndElement();
                }
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("tr");
                writer.writeStartElement("td");
                writer.writeCharacters("Text");
                writer.writeEndElement();
                writer.writeStartElement("td");
                writer.writeStartElement("input");
                writer.writeAttribute("type", "input");
                writer.writeAttribute("id", "searchText");
                writer.writeComment("");
                writer.writeEndElement();
                writer.writeCharacters(" ");
                writer.writeStartElement("span");
                writer.writeAttribute("class", "add");
                writer.writeStartElement("span");
                writer.writeAttribute("id", "searchType");
                writer.writeComment("searchType");
                writer.writeEndElement();
                writer.writeCharacters(" ▼");
                writer.writeStartElement("div");
                writer.writeAttribute("id", "searchTypes");
                writer.writeComment("");
                writer.writeEndElement();
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
                writer.writeCharacters("🔍 List Actions");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load");
                writer.writeAttribute("id", "queryStatsButton");
                writer.writeCharacters("📈 Stats on Actions");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "showQueryButton");
                writer.writeCharacters("🛈 Show Search");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "copyButton");
                writer.writeCharacters("📋 Copy Search");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "saveSearchButton");
                writer.writeCharacters("💾 Save Search");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load danger");
                writer.writeAttribute("id", "purgeButton");
                writer.writeCharacters("☠️ PURGE ACTIONS");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("div");
                writer.writeAttribute("id", "savedSearchPane");
                writer.writeAttribute("style", "display: none;");

                writer.writeStartElement("div");
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "pasteSearchButton");
                writer.writeCharacters("➕ Add Search");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "importButton");
                writer.writeCharacters("⬆️ Import Searches");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "exportButton");
                writer.writeCharacters("⬇️ Export Searches");
                writer.writeEndElement();

                writer.writeEndElement();
                writer.writeStartElement("div");
                writer.writeAttribute("id", "savedSearches");
                writer.writeComment("");
                writer.writeEndElement();

                writer.writeStartElement("div");

                writer.writeStartElement("span");
                writer.writeAttribute("class", "load");
                writer.writeAttribute("id", "listActionsSavedButton");
                writer.writeCharacters("🔍 List Actions");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load");
                writer.writeAttribute("id", "queryStatsSavedButton");
                writer.writeCharacters("📈 Stats on Actions");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "showQuerySavedButton");
                writer.writeCharacters("🛈 Show Search");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "copySavedButton");
                writer.writeCharacters("📋 Copy Search");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load danger");
                writer.writeAttribute("id", "purgeSavedButton");
                writer.writeCharacters("☠️ PURGE ACTIONS");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("div");
                writer.writeAttribute("id", "results");
                writer.writeComment("");
                writer.writeEndElement();
                writer.writeStartElement("textarea");
                writer.writeAttribute("id", "copybuffer");
                writer.writeAttribute("style", "display: none");
                writer.writeCharacters("NA");
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

    add(
        "/actiondefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                definitionRepository
                    .actions()
                    .sorted(Comparator.comparing(ActionDefinition::name))
                    .forEach(
                        action -> {
                          try {
                            writer.writeStartElement("h1");
                            writer.writeAttribute("id", action.name());
                            writer.writeCharacters(action.name());
                            writer.writeEndElement();
                            writer.writeStartElement("p");
                            writer.writeCharacters(action.description());
                            writer.writeEndElement();

                            writer.writeStartElement("table");
                            writer.writeAttribute("class", "even");
                            showSourceConfig(writer, action.filename());
                            TableRowWriter row = new TableRowWriter(writer);
                            action
                                .parameters()
                                .sorted(Comparator.comparing(ActionParameterDefinition::name))
                                .forEach(
                                    p ->
                                        row.write(
                                            false,
                                            p.name(),
                                            p.type().name()
                                                + (p.required() ? " Required" : " Optional")));
                            writer.writeEndElement();
                          } catch (XMLStreamException e) {
                            throw new RuntimeException(e);
                          }
                        });
              }
            }.renderPage(os);
          }
        });
    add(
        "/inputdefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                AnnotatedInputFormatDefinition.formats()
                    .sorted(Comparator.comparing(InputFormatDefinition::name))
                    .forEach(
                        format -> {
                          try {
                            writer.writeStartElement("h1");
                            writer.writeAttribute("id", format.name());
                            writer.writeCharacters(format.name());
                            writer.writeEndElement();

                            writer.writeStartElement("table");
                            format
                                .baseStreamVariables()
                                .sorted(Comparator.comparing(Target::name))
                                .forEach(
                                    variable -> {
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
    add(
        "/signaturedefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new TablePage(this) {

              @Override
              protected void writeRows(TableRowWriter row) {
                definitionRepository
                    .signatures()
                    .sorted(Comparator.comparing(SignatureDefinition::name))
                    .forEach(
                        variable -> {
                          row.write(
                              Arrays.asList(new Pair<>("id", variable.name())),
                              variable.name(),
                              variable.type().name());
                        });
              }
            }.renderPage(os);
          }
        });
    add(
        "/constantdefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                definitionRepository
                    .constants()
                    .sorted(Comparator.comparing(Target::name))
                    .forEach(
                        constant -> {
                          try {
                            writer.writeStartElement("h1");
                            writer.writeAttribute("id", constant.name());
                            writer.writeCharacters(constant.name());
                            writer.writeEndElement();
                            writer.writeStartElement("p");
                            writer.writeCharacters(constant.description());
                            writer.writeEndElement();
                            showSourceConfig(writer, constant.filename());
                            writer.writeStartElement("table");
                            writer.writeAttribute("class", "function even");
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
                            writer.writeAttribute(
                                "onclick", String.format("fetchConstant('%s')", constant.name()));
                            writer.writeCharacters("▶ Get");
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

    add(
        "/functiondefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                definitionRepository
                    .functions()
                    .sorted(Comparator.comparing(FunctionDefinition::name))
                    .forEach(
                        function -> {
                          try {
                            writer.writeStartElement("h1");
                            writer.writeAttribute("id", function.name());
                            writer.writeCharacters(function.name());
                            writer.writeEndElement();
                            writer.writeStartElement("p");
                            writer.writeCharacters(function.description());
                            writer.writeEndElement();
                            showSourceConfig(writer, function.filename());
                            writer.writeStartElement("table");
                            writer.writeAttribute("class", "function");
                            function
                                .parameters()
                                .map(Pair.number())
                                .forEach(
                                    p -> {
                                      try {
                                        writer.writeStartElement("tr");
                                        writer.writeStartElement("td");
                                        writer.writeCharacters(
                                            "Argument "
                                                + Integer.toString(p.first() + 1)
                                                + ": "
                                                + p.second().description());
                                        writer.writeEndElement();
                                        writer.writeStartElement("td");
                                        writer.writeCharacters(p.second().type().name());
                                        writer.writeEndElement();
                                        writer.writeStartElement("td");
                                        writer.writeStartElement("input");
                                        writer.writeAttribute("type", "text");
                                        writer.writeAttribute(
                                            "id", function.name() + "$" + p.first());
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
                            writer.writeComment("type");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeStartElement("span");
                            writer.writeAttribute("class", "load");
                            writer.writeAttribute(
                                "onclick",
                                String.format(
                                    "runFunction('%s', %s)",
                                    function.name(),
                                    function
                                        .parameters()
                                        .map(p -> p.type().apply(TypeUtils.TO_JS_PARSER))
                                        .collect(Collectors.joining(",", "[", "]"))));
                            writer.writeCharacters("▶ Run");
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
    add(
        "/dumpdefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new TablePage(this) {

              @Override
              protected void writeRows(TableRowWriter row) {
                PluginManager.dumpBound()
                    .sorted(Comparator.comparing(Pair::first))
                    .forEach(
                        x -> {
                          row.write(false, x.first(), x.second().toString());
                        });
              }
            }.renderPage(os);
          }
        });

    add(
        "/dumpadr",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new TablePage(this) {

              @Override
              protected void writeRows(TableRowWriter row) {
                PluginManager.dumpArbitrary()
                    .sorted(Comparator.comparing(Pair::first))
                    .forEach(
                        x -> {
                          row.write(false, x.first(), x.second().toMethodDescriptorString());
                        });
              }
            }.renderPage(os);
          }
        });
    add(
        "/configmap",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new TablePage(this, "Configuration File", "Kind", "Name") {

              @Override
              protected void writeRows(TableRowWriter row) {
                pluginManager.dumpPluginConfig(row);
              }
            }.renderPage(os);
          }
        });
    add(
        "/typedefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

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

                AnnotatedInputFormatDefinition.formats()
                    .sorted(Comparator.comparing(InputFormatDefinition::name))
                    .forEach(
                        format -> {
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

    add(
        "/alerts",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

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
                writer.writeStartElement("tr");
                writer.writeStartElement("th");
                writer.writeCharacters("ID");
                writer.writeEndElement();
                writer.writeStartElement("th");
                writer.writeCharacters("Labels");
                writer.writeEndElement();
                writer.writeStartElement("th");
                writer.writeCharacters("Annotations");
                writer.writeEndElement();
                writer.writeStartElement("th");
                writer.writeCharacters("Starts");
                writer.writeEndElement();
                writer.writeStartElement("th");
                writer.writeCharacters("Expires");
                writer.writeEndElement();
                writer.writeEndElement();
                processor.alerts(
                    a -> {
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
                    },
                    () -> {
                      try {
                        writer.writeStartElement("tr");
                        writer.writeStartElement("td");
                        writer.writeAttribute("colspan", "5");
                        writer.writeCharacters("No alerts have ever fired.");
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

    addJson(
        "/actions",
        (mapper, query) -> {
          final ArrayNode array = mapper.createArrayNode();
          definitionRepository
              .actions()
              .forEach(
                  actionDefinition -> {
                    final ObjectNode obj = array.addObject();
                    obj.put("name", actionDefinition.name());
                    obj.put("description", actionDefinition.description());
                    final ArrayNode parameters = obj.putArray("parameters");
                    actionDefinition
                        .parameters()
                        .forEach(
                            param -> {
                              final ObjectNode paramInfo = parameters.addObject();
                              paramInfo.put("name", param.name());
                              paramInfo.put("type", param.type().toString());
                              paramInfo.put("required", param.required());
                            });
                  });
          return array;
        });

    addJson(
        "/constants",
        (mapper, query) -> {
          final ArrayNode array = mapper.createArrayNode();
          definitionRepository
              .constants()
              .forEach(
                  constant -> {
                    final ObjectNode obj = array.addObject();
                    obj.put("name", constant.name());
                    obj.put("description", constant.description());
                    obj.put("type", constant.type().descriptor());
                  });
          return array;
        });
    addJson(
        "/signatures",
        (mapper, query) -> {
          final ArrayNode array = mapper.createArrayNode();
          definitionRepository
              .signatures()
              .forEach(
                  constant -> {
                    final ObjectNode obj = array.addObject();
                    obj.put("name", constant.name());
                    obj.put("type", constant.type().descriptor());
                  });
          return array;
        });
    addJson(
        "/functions",
        (mapper, query) -> {
          final ArrayNode array = mapper.createArrayNode();
          definitionRepository
              .functions()
              .forEach(
                  function -> {
                    final ObjectNode obj = array.addObject();
                    obj.put("name", function.name());
                    obj.put("description", function.description());
                    obj.put("return", function.returnType().descriptor());
                    final ArrayNode parameters = obj.putArray("parameters");
                    function
                        .parameters()
                        .forEach(
                            p -> {
                              final ObjectNode parameter = parameters.addObject();
                              parameter.put("type", p.type().descriptor());
                              parameter.put("description", p.description());
                            });
                  });
          return array;
        });

    add(
        "/metrics",
        t -> {
          t.getResponseHeaders().set("Content-type", TextFormat.CONTENT_TYPE_004);
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody();
              Writer writer = new PrintWriter(os)) {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
          }
        });

    add(
        "/query",
        t -> {
          final Query query;
          try {
            query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), Query.class);
          } catch (final Exception e) {
            e.printStackTrace();
            t.sendResponseHeaders(400, 0);
            try (OutputStream os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(
                os, query.perform(RuntimeSupport.MAPPER, pluginManager::sourceUrl, processor));
          }
        });

    add(
        "/stats",
        t -> {
          final FilterJson[] filters;
          try {
            filters = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), FilterJson[].class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (OutputStream os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(
                os,
                processor.stats(
                    RuntimeSupport.MAPPER,
                    Stream.of(filters)
                        .filter(Objects::nonNull)
                        .map(FilterJson::convert)
                        .toArray(Filter[]::new)));
          }
        });

    add(
        "/purge",
        t -> {
          final FilterJson[] filters;
          try {
            filters = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), FilterJson[].class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (OutputStream os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(
                os,
                processor.purge(
                    Stream.of(filters)
                        .filter(Objects::nonNull)
                        .map(FilterJson::convert)
                        .toArray(Filter[]::new)));
          }
        });

    add(
        "/currentalerts",
        t -> {
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            os.write(processor.currentAlerts().getBytes(StandardCharsets.UTF_8));
          }
        });

    add(
        "/constant",
        t -> {
          final String query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
          ConstantLoader loader;
          if (constantLoaders.containsKey(query)) {
            loader = constantLoaders.get(query);
          } else {
            loader =
                definitionRepository
                    .constants()
                    .filter(c -> c.name().equals(query))
                    .findFirst()
                    .map(ConstantDefinition::compile)
                    .orElseGet(
                        () ->
                            target ->
                                target.put("error", String.format("No such constant.", query)));
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

    add(
        "/function",
        t -> {
          final FunctionRequest query =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), FunctionRequest.class);
          FunctionRunner runner;
          if (functionRunners.containsKey(query.getName())) {
            runner = functionRunners.get(query.getName());
          } else {
            runner =
                definitionRepository
                    .functions()
                    .filter(f -> f.name().equals(query.getName()))
                    .findFirst()
                    .map(FunctionRunnerCompiler::compile)
                    .orElseGet(
                        () ->
                            (args, target) ->
                                target.put("error", String.format("No such function.", query)));
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

    addJson(
        "/variables",
        (mapper, query) -> {
          final ObjectNode node = mapper.createObjectNode();
          AnnotatedInputFormatDefinition.formats()
              .forEach(
                  source -> {
                    final ObjectNode sourceNode = node.putObject(source.name());

                    source
                        .baseStreamVariables()
                        .forEach(
                            variable -> {
                              sourceNode.put(variable.name(), variable.type().descriptor());
                            });
                  });
          return node;
        });

    AnnotatedInputFormatDefinition.formats()
        .forEach(
            format -> {
              add(
                  String.format("/input/%s", format.name()),
                  t -> {
                    if (!inputDownloadSemaphore.tryAcquire()) {
                      t.sendResponseHeaders(503, 0);
                      try (OutputStream os = t.getResponseBody()) {}
                      return;
                    }
                    t.getResponseHeaders().set("Content-type", "application/json");
                    t.sendResponseHeaders(200, 0);
                    final JsonFactory jfactory = new JsonFactory();
                    try (OutputStream os = t.getResponseBody();
                        JsonGenerator jGenerator =
                            jfactory.createGenerator(os, JsonEncoding.UTF8)) {
                      format.writeJson(jGenerator, inputProvider);
                    } catch (final IOException e) {
                      e.printStackTrace();
                    } finally {
                      inputDownloadSemaphore.release();
                    }
                  });
            });

    add(
        "/check",
        t -> {
          final String script;
          try (Scanner scanner = new Scanner(t.getRequestBody(), "utf-8")) {
            script = scanner.useDelimiter("\\Z").next();
          }
          final StringBuilder errors = new StringBuilder();
          boolean success =
              (new Compiler(true) {
                    private final NameLoader<FunctionDefinition> functions =
                        new NameLoader<>(
                            definitionRepository.functions(), FunctionDefinition::name);
                    private final NameLoader<ActionDefinition> actions =
                        new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);

                    @Override
                    protected ClassVisitor createClassVisitor() {
                      throw new UnsupportedOperationException();
                    }

                    @Override
                    protected void errorHandler(String message) {
                      errors.append(message).append("\n");
                    }

                    @Override
                    protected ActionDefinition getAction(String name) {
                      return actions.get(name);
                    }

                    @Override
                    protected FunctionDefinition getFunction(String name) {
                      return functions.get(name);
                    }

                    @Override
                    protected InputFormatDefinition getInputFormats(String name) {
                      return CompiledGenerator.SOURCES.get(name);
                    }
                  })
                  .compile(
                      script,
                      "shesmu/dyn/Checker",
                      "Uploaded Check Script.shesmu",
                      definitionRepository::constants,
                      definitionRepository::signatures,
                      x -> {});
          t.getResponseHeaders().set("Content-type", "text/plain; charset=utf-8");
          final byte[] errorBytes = errors.toString().getBytes(StandardCharsets.UTF_8);
          t.sendResponseHeaders(success ? 200 : 400, errorBytes.length);
          try (OutputStream os = t.getResponseBody()) {
            os.write(errorBytes);
          }
        });

    add(
        "/checkhtml",
        t -> {
          final String script;
          try (Scanner scanner = new Scanner(t.getRequestBody(), "utf-8")) {
            script = scanner.useDelimiter("\\Z").next();
          }
          final List<String> errors = new ArrayList<>();
          final AtomicReference<FileTable> description = new AtomicReference<>();
          boolean success =
              (new Compiler(false) {
                    private final NameLoader<FunctionDefinition> functions =
                        new NameLoader<>(
                            definitionRepository.functions(), FunctionDefinition::name);
                    private final NameLoader<ActionDefinition> actions =
                        new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);

                    @Override
                    protected ClassVisitor createClassVisitor() {
                      return new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                    }

                    @Override
                    protected void errorHandler(String message) {
                      errors.add(message);
                    }

                    @Override
                    protected ActionDefinition getAction(String name) {
                      return actions.get(name);
                    }

                    @Override
                    protected FunctionDefinition getFunction(String name) {
                      return functions.get(name);
                    }

                    @Override
                    protected InputFormatDefinition getInputFormats(String name) {
                      return CompiledGenerator.SOURCES.get(name);
                    }
                  })
                  .compile(
                      script,
                      "shesmu/dyn/Checker",
                      "Uploaded Check Script.shesmu",
                      definitionRepository::constants,
                      definitionRepository::signatures,
                      description::set);
          t.getResponseHeaders().set("Content-type", "text/xml");
          t.sendResponseHeaders(200, 0);
          try (OutputStream output = t.getResponseBody()) {
            final XMLOutputFactory outputFactory = XMLOutputFactory.newFactory();
            XMLStreamWriter writer = outputFactory.createXMLStreamWriter(output);
            writer.writeStartDocument("utf-8", "1.0");
            writer.writeStartElement("html");
            writer.writeStartElement("body");
            if (success) {
              description
                  .get()
                  .olives()
                  .forEach(
                      olive -> {
                        try {
                          writer.writeStartElement("div");
                          writer.writeAttribute("style", "overflow-x:auto");
                          MetroDiagram.draw(
                              writer,
                              (localFilePath, line, column, time) -> Stream.empty(),
                              "",
                              description.get().timestamp(),
                              olive,
                              null,
                              description.get().format());
                          writer.writeEndElement();
                        } catch (XMLStreamException e) {
                          throw new RuntimeException(e);
                        }
                      });
              writer.writeStartElement("pre");
              writer.writeCharacters(description.get().bytecode());
              writer.writeEndElement();
            } else {
              writer.writeStartElement("h1");
              writer.writeCharacters("Errors");
              writer.writeEndElement();
              for (String error : errors) {
                writer.writeStartElement("p");
                writer.writeCharacters(error);
                writer.writeEndElement();
              }
            }
            writer.writeEndElement();
            writer.writeEndDocument();
          } catch (XMLStreamException e) {
            e.printStackTrace();
          }
        });
    add(
        "/checkdash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              public Stream<Header> headers() {
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "loadFile,"
                            + "runCheck"
                            + "} from \"./shesmu.js\";"
                            + "const checkButton = document.getElementById(\"checkButton\");"
                            + "const text = document.getElementById(\"inputText\");"
                            + "const output = document.getElementById(\"outputContainer\");"
                            + "checkButton.addEventListener(\"click\", e => runCheck(checkButton, text.value, output));"
                            + "document.getElementById(\"loadButton\").addEventListener(\"click\", e => loadFile(text));"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("textarea");
                writer.writeAttribute("id", "inputText");
                writer.writeCharacters("Input shesmu;");
                writer.writeEndElement();

                writer.writeStartElement("p");
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load");
                writer.writeAttribute("id", "checkButton");
                writer.writeCharacters("✔️ Check");
                writer.writeEndElement();
                writer.writeStartElement("span");
                writer.writeAttribute("class", "load");
                writer.writeAttribute("id", "loadButton");
                writer.writeCharacters("⬆️ Upload File");
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("div");
                writer.writeAttribute("id", "outputContainer");
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

    add(
        "/type",
        t -> {
          final TypeParseRequest request =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), TypeParseRequest.class);
          Imyhat type;
          if (request.getFormat() == null || request.getFormat().equals("0")) {
            type = Imyhat.parse(request.getValue());
            t.getResponseHeaders().set("Content-type", "application/json");
          } else {
            Optional<Function<String, Imyhat>> existingTypes =
                request.getFormat().isEmpty()
                    ? Optional.of(n -> null)
                    : AnnotatedInputFormatDefinition.formats()
                        .filter(format -> format.name().equals(request.getFormat()))
                        .findAny()
                        .map(
                            format ->
                                Stream.<Target>concat(
                                            format.baseStreamVariables(),
                                            definitionRepository.signatures())
                                        .collect(
                                            Collectors.toMap(
                                                udt -> udt.name() + "_type", Target::type))
                                    ::get);
            type =
                existingTypes
                    .flatMap(
                        types -> {
                          AtomicReference<ImyhatNode> node = new AtomicReference<>();
                          Parser parser =
                              Parser.start(request.getValue(), (l, c, m) -> {})
                                  .whitespace()
                                  .then(ImyhatNode::parse, node::set)
                                  .whitespace();
                          if (parser.isGood()) {
                            return Optional.of(node.get().render(types, m -> {}));
                          }
                          return Optional.empty();
                        })
                    .orElse(Imyhat.BAD);
          }
          if (type.isBad()) {
            t.sendResponseHeaders(400, 0);
            try (OutputStream os = t.getResponseBody()) {}
          } else {
            t.sendResponseHeaders(200, 0);
            try (OutputStream os = t.getResponseBody()) {
              RuntimeSupport.MAPPER.writeValue(os, new TypeParseResponse(type));
            }
          }
        });

    add(
        "/actions.js",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/javascript");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody();
              PrintStream writer = new PrintStream(os, false, "UTF-8")) {
            writer.println(
                "import { jsonParameters, link, text, title } from './shesmu.js';\nexport const actionRender = new Map();\n");
            definitionRepository.writeJavaScriptRenderer(writer);
          }
        });

    add(
        "/pauseolive",
        t -> {
          final ObjectNode query =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ObjectNode.class);
          final SourceLocation location =
              new SourceLocation(
                  query.get("file").asText(""),
                  query.get("line").asInt(0),
                  query.get("column").asInt(0),
                  Instant.ofEpochMilli(query.get("time").asLong(0)));
          if (query.get("pause").asBoolean(false)) {
            processor.pause(location);
          } else {
            processor.resume(location);
          }
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            os.write(
                Boolean.toString(processor.isPaused(location)).getBytes(StandardCharsets.UTF_8));
          }
        });
    add("/resume", new EmergencyThrottlerHandler(false));
    add("/stopstopstop", new EmergencyThrottlerHandler(true));
    add("/main.css", "text/css; charset=utf-8");
    add("/shesmu.js", "text/javascript");
    add("/shesmu.svg", "image/svg+xml");
    add("/favicon.png", "image/png");
    add("/thorschariot.gif", "image/gif");
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

  private void showSourceConfig(XMLStreamWriter writer, Path filename) {
    if (filename != null) {
      pluginManager
          .sourceUrl(filename.toString(), 1, 1, Instant.EPOCH)
          .findFirst()
          .ifPresent(
              l -> {
                try {
                  writer.writeStartElement("p");
                  writer.writeStartElement("a");
                  writer.writeAttribute("href", l);
                  writer.writeCharacters("View Configuration File");
                  writer.writeEndElement();
                  writer.writeEndElement();
                } catch (XMLStreamException e) {
                  throw new RuntimeException(e);
                }
              });
    }
  }

  /** Add a new service endpoint with Prometheus monitoring */
  private void add(String url, HttpHandler handler) {
    server.createContext(
        url,
        t -> {
          try (AutoCloseable timer = responseTime.start(url)) {
            handler.handle(t);
          } catch (final Throwable e) {
            e.printStackTrace();
            throw new IOException(e);
          }
        });
  }

  /** Add a file backed by a class resource */
  private void add(String url, String type) {
    server.createContext(
        url,
        t -> {
          t.getResponseHeaders().set("Content-type", type);
          t.sendResponseHeaders(200, 0);
          final byte[] b = new byte[1024];
          try (OutputStream output = t.getResponseBody();
              InputStream input = getClass().getResourceAsStream(url)) {
            int count;
            while ((count = input.read(b)) > 0) {
              output.write(b, 0, count);
            }
          } catch (final IOException e) {
            e.printStackTrace();
          }
        });
  }

  /** Add a new service endpoint with Prometheus monitoring that handles JSON */
  private void addJson(String url, BiFunction<ObjectMapper, String, JsonNode> fetcher) {
    add(
        url,
        t -> {
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
    return Stream.of(
        Header.cssFile("/main.css"),
        Header.faviconPng(16),
        Header.jsModule(
            "import {parser, fetchConstant, parseType, toggleBytecode, runFunction, filterForOlive, listActionsPopup, queryStatsPopup, pauseOlive, clearDeadPause} from './shesmu.js'; window.parser = parser; window.fetchConstant = fetchConstant; window.parseType = parseType; window.toggleBytecode = toggleBytecode; window.runFunction = runFunction; window.filterForOlive = filterForOlive; window.listActionsPopup = listActionsPopup; window.queryStatsPopup = queryStatsPopup; window.pauseOlive = pauseOlive; window.clearDeadPause = clearDeadPause;"));
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
    return Stream.of(
        NavigationMenu.item("olivedash", "Olives"),
        NavigationMenu.item("actiondash", "Actions"),
        NavigationMenu.item("alerts", "Alerts"),
        NavigationMenu.submenu(
            "Definitions",
            NavigationMenu.item("actiondefs", "Actions"),
            NavigationMenu.item("constantdefs", "Constants"),
            NavigationMenu.item("functiondefs", "Functions"),
            NavigationMenu.item("inputdefs", "Input Formats"),
            NavigationMenu.item("signaturedefs", "Signatures")),
        NavigationMenu.submenu(
            "Tools",
            NavigationMenu.item("checkdash", "Olive Checker"),
            NavigationMenu.item("typedefs", "Type Converter"),
            NavigationMenu.item("configmap", "Plugin Configuration"),
            NavigationMenu.item("inflightdash", "Active Server Processes"),
            NavigationMenu.item("dumpdefs", "Detected Definition Plugins"),
            NavigationMenu.item("dumpadr", "Arbitrary Binding Spy")));
  }

  public void start() {
    System.out.println("Starting server...");
    server.start();
    System.out.println("Waiting for files to be scanned...");
    pluginManager.count();
    try {
      Thread.sleep(5000);
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    final long pluginCount = pluginManager.count();
    System.out.printf("Found %d plugins\n", pluginCount);
    System.out.println("Compiling script...");
    compiler.start();
    staticActions.start();
    System.out.println("Starting action processor...");
    processor.start(executor);
    System.out.println("Starting scheduler...");
    master.start(executor);
  }

  @Override
  public boolean isOverloaded(Set<String> services) {
    return emergencyStop || pluginManager.isOverloaded(services);
  }
}
