package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.compiler.*;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition.ConstantLoader;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.Record;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.FileWatcher;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonArray;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.*;
import ca.on.oicr.gsi.shesmu.server.*;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Filter;
import ca.on.oicr.gsi.shesmu.server.Query.FilterJson;
import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.server.plugins.JarHashRepository;
import ca.on.oicr.gsi.shesmu.server.plugins.PluginManager;
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
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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

  private static final Pattern AMPERSAND = Pattern.compile("&");
  private static final Pattern EQUAL = Pattern.compile("=");

  public static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
  private static final Map<String, Instant> INFLIGHT = new ConcurrentHashMap<>();
  private static final String instanceName =
      Optional.ofNullable(System.getenv("SHESMU_INSTANCE"))
          .map("Shesmu - "::concat)
          .orElse("Shesmu");
  private static final LatencyHistogram responseTime =
      new LatencyHistogram(
          "shesmu_http_request_time", "The time to respond to an HTTP request.", "url");
  private static final Gauge stopGauge =
      Gauge.build("shesmu_emergency_throttler", "Whether the emergency throttler is engaged.")
          .register();
  public final String build;
  public final Instant buildTime;
  private final CompiledGenerator compiler;
  private final Map<String, ConstantLoader> constantLoaders = new HashMap<>();
  private final DefinitionRepository definitionRepository;
  private volatile boolean emergencyStop;
  private final ScheduledExecutorService executor =
      new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
  private final Map<String, FunctionRunner> functionRunners = new HashMap<>();
  private final Semaphore inputDownloadSemaphore =
      new Semaphore(Math.min(Runtime.getRuntime().availableProcessors() / 2, 1));
  private final Map<String, String> jsonDumpers = new ConcurrentHashMap<>();
  private final MasterRunner master;
  private final ThreadLocal<Boolean> overloadState = ThreadLocal.withInitial(() -> false);
  private final PluginManager pluginManager = new PluginManager();
  private final ActionProcessor processor;
  private final AutoUpdatingDirectory<SavedSearch> savedSearches =
      new AutoUpdatingDirectory<>(".search", SavedSearch::new);
  private final HttpServer server;
  private final StaticActions staticActions;
  public final String version;
  private final Executor wwwExecutor =
      new ThreadPoolExecutor(
          Runtime.getRuntime().availableProcessors(),
          2 * Runtime.getRuntime().availableProcessors(),
          1,
          TimeUnit.HOURS,
          new ArrayBlockingQueue<>(3 * Runtime.getRuntime().availableProcessors()),
          runnable -> {
            final Thread thread = new Thread(runnable);
            thread.setPriority(Thread.MAX_PRIORITY);
            return thread;
          },
          (runnable, threadPoolExecutor) -> {
            overloadState.set(true);
            runnable.run();
            overloadState.set(false);
          });

  public Server(int port) throws IOException, ParseException {
    try (final InputStream in = Server.class.getResourceAsStream("shesmu-build.properties")) {
      final Properties prop = new Properties();
      prop.load(in);
      version = prop.getProperty("version");
      build =
          prop.getProperty("githash")
              + (Boolean.parseBoolean(prop.getProperty("gitdirty")) ? "-dirty" : "");
      buildTime =
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
              .parse(prop.getProperty("buildtime"))
              .toInstant();
    }
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(wwwExecutor);
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
              public boolean accept(
                  Action action, String filename, int line, int column, long time, String[] tags) {
                return processor.accept(action, filename, line, column, time, tags);
              }

              @Override
              public boolean accept(String[] labels, String[] annotation, long ttl)
                  throws Exception {
                return processor.accept(labels, annotation, ttl);
              }

              @Override
              public Dumper findDumper(String name, Imyhat... types) {
                return pluginManager
                    .findDumper(name, types)
                    .orElseGet(
                        () ->
                            jsonDumpers.containsKey(name)
                                ? new Dumper() {
                                  private ArrayNode output =
                                      RuntimeSupport.MAPPER.createArrayNode();

                                  @Override
                                  public void stop() {
                                    try {
                                      jsonDumpers.put(
                                          name, RuntimeSupport.MAPPER.writeValueAsString(output));
                                    } catch (JsonProcessingException e) {
                                      e.printStackTrace();
                                    }
                                  }

                                  @Override
                                  public void write(Object... values) {
                                    final ArrayNode row = output.addArray();
                                    for (int i = 0; i < types.length; i++) {
                                      types[i].accept(new PackJsonArray(row), values[i]);
                                    }
                                  }
                                }
                                : new Dumper() {
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
              public boolean isOverloaded(String... services) {
                return processor.isOverloaded(services)
                    || pluginManager.isOverloaded(new HashSet<>(Arrays.asList(services)));
              }

              @Override
              public <T> Stream<T> measureFlow(
                  Stream<T> input,
                  String filename,
                  int line,
                  int column,
                  int oliveLine,
                  int oliveColumn) {
                return processor.measureFlow(input, filename, line, column, oliveLine, oliveColumn);
              }

              @Override
              public void oliveRuntime(String filename, int line, int column, long timeInNs) {
                processor.oliveRuntime(filename, line, column, timeInNs);
              }
            },
            inputProvider);

    add(
        "/",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new StatusPage(this, false) {

              @Override
              protected void emitCore(SectionRenderer renderer) throws XMLStreamException {
                renderer.line("Version", version);
                renderer.line("Build Time", buildTime);
                renderer.line("Build Git Commit", build);
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
                    Stream.concat(
                        pluginManager.listConfiguration(), staticActions.listConfiguration()),
                    AnnotatedInputFormatDefinition.formats()
                        .flatMap(AnnotatedInputFormatDefinition::configuration));
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
              public String activeUrl() {
                return "inflightdash";
              }

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
              public String activeUrl() {
                return "olivedash";
              }

              @Override
              public Stream<Header> headers() {
                String olivesJson = "[]";
                String deadPausesJson = "[]";
                String savedJson = "null";
                try {
                  final ArrayNode olives = RuntimeSupport.MAPPER.createArrayNode();
                  oliveJson(olives);
                  olivesJson = RuntimeSupport.MAPPER.writeValueAsString(olives);
                  final Map<String, Instant> currentOlives =
                      compiler
                          .dashboard()
                          .map(Pair::second)
                          .collect(Collectors.toMap(FileTable::filename, FileTable::timestamp));
                  final ArrayNode deadPauses = RuntimeSupport.MAPPER.createArrayNode();
                  processor
                      .pauses()
                      .filter(
                          pause ->
                              !currentOlives
                                  .getOrDefault(pause.fileName(), Instant.EPOCH)
                                  .equals(pause.time()))
                      .forEach(location -> location.toJson(deadPauses, pluginManager));
                  deadPausesJson = RuntimeSupport.MAPPER.writeValueAsString(deadPauses);

                  final Optional<String> savedString =
                      Optional.ofNullable(t.getRequestURI().getQuery())
                          .flatMap(
                              r ->
                                  AMPERSAND
                                      .splitAsStream(r)
                                      .filter(i -> i.length() > 0)
                                      .map(q -> EQUAL.split(q, 2))
                                      .filter(q -> q[0].equals("saved"))
                                      .map(q -> q[1])
                                      .findFirst());
                  if (savedString.isPresent()) {
                    final Query.LocationJson savedLocation =
                        RuntimeSupport.MAPPER.readValue(
                            URLDecoder.decode(savedString.get(), "UTF-8"),
                            Query.LocationJson.class);
                    savedJson = RuntimeSupport.MAPPER.writeValueAsString(savedLocation);
                  }
                } catch (IOException e) {
                  e.printStackTrace();
                }
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "initialiseOliveDash"
                            + "} from \"./shesmu.js\";"
                            + "initialiseOliveDash("
                            + olivesJson
                            + ", "
                            + deadPausesJson
                            + ", "
                            + savedJson
                            + ");"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {

                writer.writeStartElement("div");
                writer.writeAttribute("id", "olives");
                writer.writeComment("");
                writer.writeEndElement();
                writer.writeStartElement("div");
                writer.writeAttribute("id", "results");
                writer.writeComment("");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });
    add(
        "/actiondash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          final String query =
              Optional.ofNullable(t.getRequestURI().getQuery())
                  .flatMap(
                      r ->
                          AMPERSAND
                              .splitAsStream(r)
                              .filter(i -> i.length() > 0)
                              .map(q -> EQUAL.split(q, 2))
                              .filter(q -> q[0].equals("saved"))
                              .map(q -> q[1])
                              .findFirst())
                  .orElse("All Actions");
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "actiondash";
              }

              @Override
              public Stream<Header> headers() {
                final Instant now = Instant.now();
                final ObjectNode searchInfo = RuntimeSupport.MAPPER.createObjectNode();
                savedSearches.stream().forEach(search -> search.write(searchInfo));
                String savedSearches;
                String queryJson;
                String tags;
                try {
                  savedSearches = RuntimeSupport.MAPPER.writeValueAsString(searchInfo);
                  tags =
                      RuntimeSupport.MAPPER.writeValueAsString(
                          compiler
                              .dashboard()
                              .map(Pair::second)
                              .flatMap(FileTable::olives)
                              .flatMap(OliveTable::tags)
                              .collect(Collectors.toSet()));
                  queryJson = RuntimeSupport.MAPPER.writeValueAsString(query);
                } catch (JsonProcessingException e) {
                  e.printStackTrace();
                  savedSearches = "{}";
                  queryJson = "null";
                  tags = "[]";
                }
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "initialiseActionDash"
                            + "} from \"./shesmu.js\";"
                            + "initialiseActionDash("
                            + savedSearches
                            + ", "
                            + tags
                            + ", "
                            + queryJson
                            + ");"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");

                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "pasteSearchButton");
                writer.writeCharacters("➕ Add Search");
                writer.writeEndElement();

                writer.writeStartElement("span");
                writer.writeAttribute("class", "dropdown");
                writer.writeStartElement("span");
                writer.writeAttribute("id", "searchName");
                writer.writeCharacters("Select Search");
                writer.writeEndElement();
                writer.writeCharacters(" ▼");
                writer.writeStartElement("div");
                writer.writeAttribute("id", "searches");
                writer.writeComment("");
                writer.writeEndElement();
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

                writer.writeStartElement("span");
                writer.writeAttribute("class", "load accessory");
                writer.writeAttribute("id", "deleteSearchButton");
                writer.writeCharacters("✖ Delete Search");
                writer.writeEndElement();

                writer.writeEndElement();

                writer.writeStartElement("div");
                writer.writeAttribute("id", "results");
                writer.writeComment("");
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
              public String activeUrl() {
                return "actiondefs";
              }

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
        "/refillerdefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {

              @Override
              public String activeUrl() {
                return "refillerdefs";
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                definitionRepository
                    .refillers()
                    .sorted(Comparator.comparing(RefillerDefinition::name))
                    .forEach(
                        refiller -> {
                          try {
                            writer.writeStartElement("h1");
                            writer.writeAttribute("id", refiller.name());
                            writer.writeCharacters(refiller.name());
                            writer.writeEndElement();
                            writer.writeStartElement("p");
                            writer.writeCharacters(refiller.description());
                            writer.writeEndElement();

                            writer.writeStartElement("table");
                            writer.writeAttribute("class", "even");
                            showSourceConfig(writer, refiller.filename());
                            TableRowWriter row = new TableRowWriter(writer);
                            refiller
                                .parameters()
                                .sorted(Comparator.comparing(RefillerParameterDefinition::name))
                                .forEach(p -> row.write(false, p.name(), p.type().name()));
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
              public String activeUrl() {
                return "inputdefs";
              }

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
        "/grouperdefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "grouperdefs";
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                OliveClauseNodeGroupWithGrouper.definitions()
                    .sorted(Comparator.comparing(GrouperDefinition::name))
                    .forEach(
                        grouper -> {
                          try {
                            writer.writeStartElement("h1");
                            writer.writeAttribute("id", grouper.name());
                            writer.writeCharacters(grouper.name());
                            writer.writeEndElement();

                            writer.writeStartElement("h2");
                            writer.writeCharacters("Input");
                            writer.writeEndElement();

                            writer.writeStartElement("table");
                            writer.writeStartElement("tr");
                            writer.writeStartElement("td");
                            writer.writeCharacters("Name");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeCharacters("Kind");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeCharacters("Type");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeCharacters("Description");
                            writer.writeEndElement();
                            writer.writeEndElement();
                            for (int i = 0; i < grouper.inputs(); i++) {
                              writer.writeStartElement("tr");
                              writer.writeStartElement("td");
                              writer.writeCharacters(grouper.input(i).name());
                              writer.writeEndElement();
                              writer.writeStartElement("td");
                              switch (grouper.input(i).kind()) {
                                case STATIC:
                                  writer.writeCharacters("Fixed");
                                  break;
                                case ROW_VALUE:
                                  writer.writeCharacters("Per row");
                                  break;
                                default:
                                  writer.writeCharacters("Unknown️");
                              }
                              writer.writeEndElement();
                              writer.writeStartElement("td");
                              writer.writeCharacters(
                                  grouper.input(i).type().toString(Collections.emptyMap()));
                              writer.writeEndElement();
                              writer.writeStartElement("td");
                              writer.writeCharacters(grouper.input(i).description());
                              writer.writeEndElement();
                              writer.writeEndElement();
                            }
                            writer.writeEndElement();
                            writer.writeStartElement("h2");
                            writer.writeCharacters("Output");
                            writer.writeEndElement();

                            writer.writeStartElement("table");
                            writer.writeStartElement("tr");
                            writer.writeStartElement("td");
                            writer.writeCharacters("Position");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeCharacters("Default Name");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeCharacters("Kind");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeCharacters("Type");
                            writer.writeEndElement();
                            writer.writeStartElement("td");
                            writer.writeCharacters("Description");
                            writer.writeEndElement();
                            writer.writeEndElement();

                            for (int i = 0; i < grouper.outputs(); i++) {
                              writer.writeStartElement("tr");
                              writer.writeStartElement("td");
                              writer.writeCharacters(Integer.toString(i + 1));
                              writer.writeEndElement();
                              writer.writeStartElement("td");
                              writer.writeCharacters(grouper.output(i).defaultName());
                              writer.writeEndElement();
                              writer.writeStartElement("td");
                              switch (grouper.output(i).kind()) {
                                case STATIC:
                                  writer.writeCharacters("Per subgroup");
                                  break;
                                case ROW_VALUE:
                                  writer.writeCharacters("Per row");
                                  break;
                                default:
                                  writer.writeCharacters("Unknown️");
                              }
                              writer.writeEndElement();
                              writer.writeStartElement("td");
                              writer.writeCharacters(
                                  grouper.output(i).type().toString(Collections.emptyMap()));
                              writer.writeEndElement();
                              writer.writeStartElement("td");
                              writer.writeCharacters(grouper.output(i).description());
                              writer.writeEndElement();
                              writer.writeEndElement();
                            }

                            writer.writeEndElement();
                            writer.writeStartElement("p");
                            writer.writeCharacters(grouper.description());
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
              public String activeUrl() {
                return "signaturedefs";
              }

              @Override
              protected void writeRows(TableRowWriter row) {
                definitionRepository
                    .signatures()
                    .sorted(Comparator.comparing(SignatureDefinition::name))
                    .forEach(
                        variable -> {
                          row.write(
                              Collections.singletonList(new Pair<>("id", variable.name())),
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
              public String activeUrl() {
                return "constantdefs";
              }

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
              public String activeUrl() {
                return "functiondefs";
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                Stream.concat(definitionRepository.functions(), compiler.functions())
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
              public String activeUrl() {
                return "dumpdefs";
              }

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
              public String activeUrl() {
                return "dumpadr";
              }

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
              public String activeUrl() {
                return "configmap";
              }

              @Override
              protected void writeRows(TableRowWriter row) {
                pluginManager.dumpPluginConfig(row);
                AnnotatedInputFormatDefinition.formats()
                    .forEach(format -> format.dumpPluginConfig(row));
              }
            }.renderPage(os);
          }
        });
    add(
        "/pluginhashes",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new TablePage(this, "Plugin", "File", "SHA-256") {
              @Override
              public String activeUrl() {
                return "pluginhashes";
              }

              @Override
              protected void writeRows(TableRowWriter row) {
                Stream.of(
                        PluginManager.PLUGIN_HASHES,
                        AnnotatedInputFormatDefinition.INPUT_FORMAT_HASHES,
                        OliveClauseNodeGroupWithGrouper.GROUPER_HASHES)
                    .flatMap(JarHashRepository::stream)
                    .sorted(Comparator.comparing(e -> e.getKey().getName()))
                    .forEach(
                        e ->
                            row.write(
                                false,
                                e.getKey().getName(),
                                e.getValue().first(),
                                e.getValue().second()));
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
              public String activeUrl() {
                return "typedefs";
              }

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
              @Override
              public String activeUrl() {
                return "alerts";
              }

              private void labelsToHtml(XMLStreamWriter writer, Map<String, String> labels)
                  throws XMLStreamException {
                for (Entry<String, String> entry : labels.entrySet()) {
                  writer.writeStartElement("span");
                  writer.writeAttribute("class", "label");
                  writer.writeCharacters(entry.getKey());
                  writer.writeCharacters(" = ");
                  writer.writeCharacters(entry.getValue());
                  writer.writeEndElement();
                  writer.writeStartElement("br");
                  writer.writeComment("");
                  writer.writeEndElement();
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
        "/refillers",
        (mapper, query) -> {
          final ArrayNode array = mapper.createArrayNode();
          definitionRepository
              .refillers()
              .forEach(
                  refiller -> {
                    final ObjectNode obj = array.addObject();
                    obj.put("name", refiller.name());
                    obj.put("description", refiller.description());
                    final ArrayNode parameters = obj.putArray("parameters");
                    refiller
                        .parameters()
                        .forEach(
                            param -> {
                              final ObjectNode paramInfo = parameters.addObject();
                              paramInfo.put("name", param.name());
                              paramInfo.put("type", param.type().toString());
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
          Stream.concat(definitionRepository.functions(), compiler.functions())
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
    addJson(
        "/olives",
        (mapper, query) -> {
          final ArrayNode array = mapper.createArrayNode();
          oliveJson(array);
          return array;
        });

    add(
        "/metrodiagram",
        t -> {
          final Query.LocationJson location =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), Query.LocationJson.class);
          final Pair<Pair<OliveRunInfo, FileTable>, OliveTable> match =
              compiler
                  .dashboard()
                  .flatMap(
                      ft ->
                          ft.second()
                              .olives()
                              .filter(
                                  o ->
                                      location.test(
                                          new SourceLocation(
                                              ft.second().filename(),
                                              o.line(),
                                              o.column(),
                                              ft.second().timestamp())))
                              .map(o -> new Pair<>(ft, o)))
                  .findFirst()
                  .orElse(null);
          if (match == null) {
            t.sendResponseHeaders(404, 0);
          } else {
            t.sendResponseHeaders(200, 0);
            t.getResponseHeaders().set("Content-type", "image/svg+xml; charset=utf-8");
            try (final OutputStream os = t.getResponseBody()) {
              final XMLStreamWriter writer =
                  XMLOutputFactory.newFactory().createXMLStreamWriter(os);
              writer.writeStartDocument("utf-8", "1.0");

              MetroDiagram.draw(
                  writer,
                  pluginManager,
                  match.first().second().filename(),
                  match.first().second().timestamp(),
                  match.second(),
                  match.first().first() == null ? null : match.first().first().inputCount(),
                  match.first().second().format(),
                  processor);
              writer.writeEndDocument();
            } catch (XMLStreamException e) {
              throw new RuntimeException(e);
            }
          }
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
        "/invalidate",
        t -> {
          final Set<String> names;
          try {
            names =
                new HashSet<>(
                    Arrays.asList(
                        RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String[].class)));
          } catch (final Exception e) {
            e.printStackTrace();
            t.sendResponseHeaders(400, 0);
            try (OutputStream os = t.getResponseBody()) {}
            return;
          }
          KeyValueCache.caches()
              .filter(cache -> names.contains(cache.name()))
              .forEach(KeyValueCache::invalidateAll);
          ValueCache.caches()
              .filter(cache -> names.contains(cache.name()))
              .forEach(ValueCache::invalidate);
          t.sendResponseHeaders(201, -1);
        });
    addJson(
        "/caches",
        (mapper, query) -> {
          final ArrayNode array = mapper.createArrayNode();
          KeyValueCache.caches()
              .forEach(
                  cache -> {
                    final ObjectNode node = array.addObject();
                    node.put("name", cache.name());
                    node.put("ttl", cache.ttl());
                    node.put("type", "kv");
                    final ObjectNode entries = node.putObject("entries");
                    storeEntries(entries, cache);
                  });
          ValueCache.caches()
              .forEach(
                  cache -> {
                    final ObjectNode node = array.addObject();
                    node.put("name", cache.name());
                    node.put("ttl", cache.ttl());
                    node.put("lastUpdate", cache.lastUpdated().toEpochMilli());
                    node.put("collectionSize", cache.collectionSize());
                    node.put("type", "v");
                  });
          return array;
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
                Stream.concat(definitionRepository.functions(), compiler.functions())
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
                    if (processor.isOverloaded(format.name())
                        || pluginManager.isOverloaded(Collections.singleton(format.name()))
                        || !inputDownloadSemaphore.tryAcquire()) {
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
                    private final NameLoader<ActionDefinition> actions =
                        new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);
                    private final NameLoader<RefillerDefinition> refillers =
                        new NameLoader<>(
                            definitionRepository.refillers(), RefillerDefinition::name);
                    private final NameLoader<FunctionDefinition> functions =
                        new NameLoader<>(
                            Stream.concat(definitionRepository.functions(), compiler.functions()),
                            FunctionDefinition::name);

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
                    protected RefillerDefinition getRefiller(String name) {
                      return refillers.get(name);
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
                      null,
                      x -> {});
          t.getResponseHeaders().set("Content-type", "text/plain; charset=utf-8");
          final byte[] errorBytes = errors.toString().getBytes(StandardCharsets.UTF_8);
          t.sendResponseHeaders(success ? 200 : 400, errorBytes.length);
          try (OutputStream os = t.getResponseBody()) {
            os.write(errorBytes);
          }
        });
    add(
        "/simulate",
        t -> {
          final SimulateRequest request =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), SimulateRequest.class);
          request.run(definitionRepository, compiler::functions, this, inputProvider, t);
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
          final List<Consumer<XMLStreamWriter>> exports = new ArrayList<>();
          boolean success =
              (new Compiler(false) {
                    private final NameLoader<ActionDefinition> actions =
                        new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);
                    private final NameLoader<RefillerDefinition> refillers =
                        new NameLoader<>(
                            definitionRepository.refillers(), RefillerDefinition::name);
                    private final NameLoader<FunctionDefinition> functions =
                        new NameLoader<>(
                            Stream.concat(definitionRepository.functions(), compiler.functions()),
                            FunctionDefinition::name);

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
                    protected RefillerDefinition getRefiller(String name) {
                      return refillers.get(name);
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
                      new ExportConsumer() {
                        @Override
                        public void function(
                            String name,
                            Imyhat returnType,
                            Supplier<Stream<FunctionParameter>> parameters) {
                          exports.add(
                              writer -> {
                                try {
                                  writer.writeStartElement("h1");
                                  writer.writeCharacters("Export ");
                                  writer.writeCharacters(name);
                                  writer.writeEndElement();
                                  final TableRowWriter row = new TableRowWriter(writer);
                                  writer.writeStartElement("table");
                                  row.write(false, "Return", "", returnType.name());
                                  parameters
                                      .get()
                                      .forEach(
                                          new Consumer<FunctionParameter>() {
                                            private int index;

                                            @Override
                                            public void accept(
                                                FunctionParameter functionParameter) {
                                              row.write(
                                                  false,
                                                  Integer.toString(index++),
                                                  functionParameter.description(),
                                                  functionParameter.type().name());
                                            }
                                          });
                                  writer.writeEndElement();
                                } catch (XMLStreamException e) {
                                  throw new RuntimeException(e);
                                }
                              });
                        }
                      },
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
                              description.get().format(),
                              (f, l, c, ol, oc) -> null);
                          writer.writeEndElement();
                        } catch (XMLStreamException e) {
                          throw new RuntimeException(e);
                        }
                      });
              exports.forEach(export -> export.accept(writer));
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
              public String activeUrl() {
                return "checkdash";
              }

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
                            + "document.getElementById(\"loadButton\").addEventListener(\"click\", e => loadFile((name, data) => text.value = data));"));
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
        "/simulatedash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "simulatedash";
              }

              @Override
              public Stream<Header> headers() {
                return Stream.of(
                    Header.jsFile("ace.js"),
                    Header.jsFile("ext-searchbox.js"),
                    Header.jsFile("theme-ambiance.js"),
                    Header.jsFile("theme-chrome.js"),
                    Header.jsFile("mode-shesmu.js"),
                    Header.jsModule(
                        "import {"
                            + "initialiseSimulationDashboard"
                            + "} from \"./shesmu.js\";"
                            + "const output = document.getElementById(\"outputContainer\");"
                            + "const sound = document.getElementById(\"sound\");"
                            + "initialiseSimulationDashboard(ace, output, sound);"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("audio");
                writer.writeAttribute("id", "sound");
                writer.writeAttribute("controls", "none");
                writer.writeAttribute("preload", "auto");
                writer.writeAttribute("style", "display: none");
                writer.writeAttribute("src", "complete.ogg");
                writer.writeComment("");
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
                            return Optional.of(
                                node.get()
                                    .render(
                                        types,
                                        pluginManager
                                                .functions()
                                                .collect(
                                                    Collectors.toMap(
                                                        FunctionDefinition::name,
                                                        Function.identity()))
                                            ::get,
                                        m -> {}));
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
          t.getResponseHeaders().set("Content-type", "text/javascript;charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (OutputStream os = t.getResponseBody();
              PrintStream writer = new PrintStream(os, false, "UTF-8")) {
            writer.println(
                "import { blank, collapse, jsonParameters, link, objectTable, table, text, timespan, title, visibleText } from './utils.js';\nexport const actionRender = new Map();\n");
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
    add(
        "/jsondumper",
        t -> {
          final String name = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
          switch (t.getRequestMethod()) {
            case "POST":
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(200, 0);
              try (OutputStream os = t.getResponseBody()) {
                os.write(jsonDumpers.getOrDefault(name, "[]").getBytes(StandardCharsets.UTF_8));
              }
              break;
            case "PUT":
              jsonDumpers.computeIfAbsent(name, k -> "[]");
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(201, -1);
              break;
            case "DELETE":
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(200, 0);
              try (OutputStream os = t.getResponseBody()) {
                os.write(
                    Boolean.toString(jsonDumpers.remove(name) != null)
                        .getBytes(StandardCharsets.UTF_8));
              }
              break;
            default:
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(400, 0);
              try (OutputStream os = t.getResponseBody()) {
                os.write("{}".getBytes(StandardCharsets.UTF_8));
              }
          }
        });
    add("/resume", new EmergencyThrottlerHandler(false));
    add("/stopstopstop", new EmergencyThrottlerHandler(true));
    add("main.css", "text/css; charset=utf-8");
    add("shesmu.js", "text/javascript;charset=utf-8");
    add("utils.js", "text/javascript;charset=utf-8");
    add("ace.js", "text/javascript;charset=utf-8");
    add("ext-searchbox.js", "text/javascript;charset=utf-8");
    add("theme-ambiance.js", "text/javascript;charset=utf-8");
    add("theme-chrome.js", "text/javascript;charset=utf-8");
    add("mode-shesmu.js", "text/javascript;charset=utf-8");
    add("shesmu.svg", "image/svg+xml");
    add("favicon.png", "image/png");
    add("thorschariot.gif", "image/gif");
    add("swagger.json", "application/json");
    add("complete.ogg", "audio/ogg");
    add("api-docs/favicon-16x16.png", "image/png");
    add("api-docs/favicon-32x32.png", "image/png");
    add("api-docs/index.html", "text/html");
    add("api-docs/oauth2-redirect.html", "text/html");
    add("api-docs/swagger-ui-bundle.js", "text/javascript");
    add("api-docs/swagger-ui-standalone-preset.js", "text/javascript");
    add("api-docs/swagger-ui.css", "text/css");
    add("api-docs/swagger-ui.js", "text/javascript");
  }

  /** Add a new service endpoint with Prometheus monitoring */
  private void add(String url, HttpHandler handler) {
    server.createContext(
        url,
        t -> {
          if (overloadState.get()) {
            t.sendResponseHeaders(503, -1);
            return;
          }
          try (AutoCloseable timer = responseTime.start(url);
              AutoCloseable inflight =
                  inflightCloseable(
                      String.format(
                          "HTTP request %s for %s", url, t.getRemoteAddress().toString()))) {
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
        "/" + url,
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

  private void emergencyThrottle(boolean stopped) {
    this.emergencyStop = stopped;
    stopGauge.set(stopped ? 1 : 0);
  }

  @Override
  public Stream<Header> headers() {
    return Stream.of(
        Header.cssFile("/main.css"),
        Header.faviconPng(16),
        Header.jsModule(
            "import {parser, fetchConstant, parseType, runFunction } from './shesmu.js'; window.parser = parser; window.fetchConstant = fetchConstant; window.parseType = parseType; window.runFunction = runFunction;"));
  }

  @Override
  public boolean isOverloaded(Set<String> services) {
    return emergencyStop || pluginManager.isOverloaded(services);
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
    return instanceName;
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
            NavigationMenu.item("grouperdefs", "Groupers"),
            NavigationMenu.item("inputdefs", "Input Formats"),
            NavigationMenu.item("refillerdefs", "Refillers"),
            NavigationMenu.item("signaturedefs", "Signatures")),
        NavigationMenu.submenu(
            "Tools",
            NavigationMenu.item("checkdash", "Olive Checker"),
            NavigationMenu.item("simulatedash", "Olive Simulator"),
            NavigationMenu.item("typedefs", "Type Converter")),
        NavigationMenu.submenu(
            "Internals",
            NavigationMenu.item("inflightdash", "Active Server Processes"),
            NavigationMenu.item("configmap", "Plugin-Olive Mapping"),
            NavigationMenu.item("pluginhashes", "Plugin JAR Hashes"),
            NavigationMenu.item("dumpdefs", "Annotation-Driven Definition"),
            NavigationMenu.item("dumpadr", "Manual Definitions")));
  }

  private void oliveJson(ArrayNode array) {
    compiler
        .dashboard()
        .forEach(
            fileTable -> {
              final ObjectNode fileNode = array.addObject();
              fileNode.put("format", fileTable.second().format().name());
              fileNode.put("lastCompiled", fileTable.second().timestamp().toEpochMilli());
              fileNode.put("filename", fileTable.second().filename());
              fileNode.put("bytecode", fileTable.second().bytecode());
              fileNode.put(
                  "status", fileTable.first() == null ? "Not yet run" : fileTable.first().status());
              fileNode.put(
                  "inputCount", fileTable.first() == null ? null : fileTable.first().inputCount());
              fileNode.put(
                  "runtime",
                  fileTable.first() == null
                      ? null
                      : TimeUnit.NANOSECONDS.toMillis(fileTable.first().runtime().getNano()));
              fileNode.put(
                  "lastRun",
                  fileTable.first() == null ? null : fileTable.first().lastRun().toEpochMilli());
              final ArrayNode olivesNode = fileNode.putArray("olives");

              fileTable
                  .second()
                  .olives()
                  .forEach(
                      olive -> {
                        final SourceLocation location =
                            new SourceLocation(
                                fileTable.second().filename(),
                                olive.line(),
                                olive.column(),
                                fileTable.second().timestamp());
                        final ObjectNode oliveNode = olivesNode.addObject();
                        location.toJson(oliveNode, pluginManager);
                        oliveNode.put("description", olive.description());
                        oliveNode.put("syntax", olive.syntax());
                        oliveNode.put("producesActions", olive.producesActions());
                        if (olive.producesActions()) {
                          oliveNode.put("paused", processor.isPaused(location));
                        }
                        olive.tags().sorted().forEach(oliveNode.putArray("tags")::add);
                        final ArrayNode clauseArray = oliveNode.putArray("clauses");
                        olive
                            .clauses()
                            .forEach(
                                clause -> {
                                  final ObjectNode clauseNode = clauseArray.addObject();
                                  clauseNode.put("line", clause.line());
                                  clauseNode.put("column", clause.column());
                                  clauseNode.put("syntax", clause.syntax());
                                });
                      });
            });
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

  @SuppressWarnings("ResultOfMethodCallIgnored")
  public void start() {
    // Initialise status page start time immediately
    StatusPage.class.toString();
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

  private <K, V> void storeEntries(ObjectNode entries, KeyValueCache<K, V> cache) {
    for (final Map.Entry<K, Record<V>> record : cache) {
      final ObjectNode node = entries.putObject(record.getKey().toString());
      node.put("collectionSize", record.getValue().collectionSize());
      node.put("lastUpdate", record.getValue().lastUpdate().toEpochMilli());
    }
  }
}
