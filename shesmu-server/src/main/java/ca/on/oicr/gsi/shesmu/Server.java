package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.compiler.*;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition.ConstantLoader;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository.CallableOliveDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.compiler.description.Produces;
import ca.on.oicr.gsi.shesmu.core.StandardDefinitions;
import ca.on.oicr.gsi.shesmu.plugin.ErrorableStream;
import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.LabelledKeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.FileWatcher;
import ca.on.oicr.gsi.shesmu.plugin.filter.*;
import ca.on.oicr.gsi.shesmu.plugin.grouper.GrouperDefinition;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeParser;
import ca.on.oicr.gsi.shesmu.plugin.wdl.WdlInputType;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.*;
import ca.on.oicr.gsi.shesmu.server.ActionProcessor.Filter;
import ca.on.oicr.gsi.shesmu.server.plugins.AnnotatedInputFormatDefinition;
import ca.on.oicr.gsi.shesmu.server.plugins.BaseHumanTypeParser;
import ca.on.oicr.gsi.shesmu.server.plugins.JarHashRepository;
import ca.on.oicr.gsi.shesmu.server.plugins.PluginManager;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import ca.on.oicr.gsi.status.*;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;
import io.prometheus.client.hotspot.DefaultExports;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.Base64;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.objectweb.asm.ClassVisitor;

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
      try (var os = t.getResponseBody()) {}
    }
  }

  private static final Pattern AMPERSAND = Pattern.compile("&");
  private static final Pattern BASE64URL_DATA =
      Pattern.compile("((?:[A-Za-z0-9_-]{4})*(?:[A-Za-z0-9_-]{2}|[A-Za-z0-9_-]{3}|))");
  private static final Pattern EQUAL = Pattern.compile("=");
  public static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final Map<String, Instant> INFLIGHT = new ConcurrentSkipListMap<>();
  private static final Gauge inflightCount =
      Gauge.build("shesmu_inflight_count", "The number of inflight processes.").register();
  private static final Gauge inflightOldest =
      Gauge.build(
              "shesmu_inflight_oldest_time",
              "The start time of the longest-running server process.")
          .register();
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
  private static final Counter versionCounter =
      Counter.build("shesmu_version", "The Shesmu git commit version")
          .labelNames("version")
          .register();

  public static Map<String, String> getParameters(HttpExchange t) {
    return Optional.ofNullable(t.getRequestURI().getQuery())
        .map(
            r ->
                AMPERSAND
                    .splitAsStream(r)
                    .filter(i -> i.length() > 0)
                    .map(q -> EQUAL.split(q, 2))
                    .collect(Collectors.toMap(q -> q[0], q -> q[1], (a, b) -> a)))
        .orElseGet(Map::of);
  }

  private static <T> String handleJsonQueryParameter(
      Class<T> clazz, String parameter, T defaultValue) throws IOException {
    if (parameter == null) {
      return RuntimeSupport.MAPPER.writeValueAsString(defaultValue);
    }
    if (BASE64URL_DATA.matcher(parameter).matches()) {

      return RuntimeSupport.MAPPER.writeValueAsString(
          RuntimeSupport.MAPPER.readValue(Base64.getUrlDecoder().decode(parameter), clazz));
    } else {
      return RuntimeSupport.MAPPER.writeValueAsString(
          RuntimeSupport.MAPPER.readValue(
              URLDecoder.decode(parameter, StandardCharsets.UTF_8), clazz));
    }
  }

  public static Runnable inflight(String name) {
    INFLIGHT.putIfAbsent(name, Instant.now());
    inflightOldest.set(
        INFLIGHT.values().stream()
            .min(Comparator.naturalOrder())
            .map(Instant::getEpochSecond)
            .orElse(0L));
    inflightCount.set(INFLIGHT.size());
    return () -> {
      INFLIGHT.remove(name);
      inflightOldest.set(
          INFLIGHT.values().stream()
              .min(Comparator.naturalOrder())
              .map(Instant::getEpochSecond)
              .orElse(0L));
      inflightCount.set(INFLIGHT.size());
    };
  }

  public static AutoCloseable inflightCloseable(String name) {
    return inflight(name)::run;
  }

  public static void main(String[] args) throws Exception {
    DefaultExports.initialize();

    final var s = new Server(8081, FileWatcher.DATA_DIRECTORY);
    s.start();
  }

  public final String build;
  public final Instant buildTime;
  private final CompiledGenerator compiler;
  private final Map<String, ConstantLoader> constantLoaders = new HashMap<>();
  private final DefinitionRepository definitionRepository;
  private volatile boolean emergencyStop;
  // This executor is to be used only for server dispatch work. The action processor queues actions
  // on this thread; the olives are queued using this thread pool. No heavy lifting or user-driven
  // loads are  to happen here.
  private final ScheduledExecutorService executor =
      new ScheduledThreadPoolExecutor(
          Runtime.getRuntime().availableProcessors(),
          new ShesmuThreadFactory("server-housekeeping", Thread.NORM_PRIORITY));
  private final FileWatcher fileWatcher;
  private final Map<String, FunctionRunner> functionRunners = new HashMap<>();
  private final AutoUpdatingDirectory<GuidedMeditation> guidedMeditations;
  private final Semaphore inputDownloadSemaphore =
      new Semaphore(Math.min(Runtime.getRuntime().availableProcessors() / 2, 1));
  private final Map<String, String> jsonDumpers = new ConcurrentHashMap<>();
  private final MasterRunner master;
  private final ThreadLocal<Boolean> overloadState = ThreadLocal.withInitial(() -> false);
  private final PluginManager pluginManager;
  private final ActionProcessor processor;
  private final AutoUpdatingDirectory<SavedSearch> savedSearches;
  private final HttpServer server;
  private final StaticActions staticActions;
  private final Map<String, TypeParser> typeParsers = new TreeMap<>();
  public final String version;
  private final Executor wwwExecutor =
      new ThreadPoolExecutor(
          Runtime.getRuntime().availableProcessors(),
          4 * Runtime.getRuntime().availableProcessors(),
          1,
          TimeUnit.HOURS,
          new ArrayBlockingQueue<>(10 * Runtime.getRuntime().availableProcessors()),
          new ShesmuThreadFactory("www-requests", Thread.MAX_PRIORITY),
          (runnable, threadPoolExecutor) -> {
            overloadState.set(true);
            runnable.run();
            overloadState.set(false);
          });

  public Server(int port, FileWatcher fileWatcher) throws IOException, ParseException {
    this.fileWatcher = fileWatcher;
    try (final var in = Server.class.getResourceAsStream("shesmu-build.properties")) {
      final var prop = new Properties();
      prop.load(in);
      version = prop.getProperty("version");
      versionCounter.labels(version).inc();
      build =
          prop.getProperty("githash")
              + (Boolean.parseBoolean(prop.getProperty("gitdirty")) ? "-dirty" : "");
      buildTime =
          new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
              .parse(prop.getProperty("buildtime"))
              .toInstant();
    }
    pluginManager = new PluginManager(fileWatcher);
    savedSearches = new AutoUpdatingDirectory<>(fileWatcher, ".search", SavedSearch::new);
    final var module = new SimpleModule("shesmu");
    module.addSerializer(
        SourceLocation.class, new SourceLocation.SourceLocationSerializer(pluginManager));
    RuntimeSupport.MAPPER.registerModule(module);
    server = HttpServer.create(new InetSocketAddress(port), 0);
    server.setExecutor(wwwExecutor);
    definitionRepository = DefinitionRepository.concat(new StandardDefinitions(), pluginManager);
    processor = new ActionProcessor(localname().resolve("/alerts"), pluginManager, this);
    compiler = new CompiledGenerator(executor, definitionRepository, processor::isPaused);
    staticActions = new StaticActions(processor, definitionRepository);
    guidedMeditations =
        new AutoUpdatingDirectory<>(
            fileWatcher,
            GuidedMeditation.EXTENSION,
            fileName ->
                new GuidedMeditation(
                    fileName, DefinitionRepository.concat(definitionRepository, compiler)));

    /**
     * When fetch()ing a format, try the name of the format on:
     * - AnnotatedInputFormatDefinition's Stream of known formats, which includes -input and -remote files
     * - PluginManager, which will stream its known formats (installed plugins) and try fetching from each of those
     * - ActionProcessor, which offers the 'shesmu' input format
     * We try every format and flatMap the results together in order to offer the ability to specify the same input
     * format multiple ways at the same time. E.g., a cerberus plugin may be configured AND a cerberus_fp-remote file
     * (a RemoteJsonSource in AnnotatedInputFormatDefinition) may be present at the same time. We
     * present the data from both sources as one unified cerberus_fp format wherever that input
     * format is requested.
     * If a format is not available from a particular source, e.g.
     * 'cerberus_fp' is requested from unix_file, then nothing is returned, making this a safe flatMap
     */
    final InputSource inputSource =
        (format, readStale) ->
            ErrorableStream.concatWithErrors(
                    AnnotatedInputFormatDefinition.formats(), Stream.of(pluginManager, processor))
                .flatMap(source -> source.fetch(format, readStale));
    master =
        new MasterRunner(
            compiler,
            new OliveServices() {
              @Override
              public boolean accept(
                  Action action,
                  String filename,
                  int line,
                  int column,
                  String hash,
                  String[] tags) {
                return processor.accept(action, filename, line, column, hash, tags);
              }

              @Override
              public boolean accept(
                  String[] labels,
                  String[] annotation,
                  long ttl,
                  String filename,
                  int line,
                  int column,
                  String hash)
                  throws Exception {
                return processor.accept(labels, annotation, ttl, filename, line, column, hash);
              }

              @Override
              public Dumper findDumper(String name, String[] columns, Imyhat... types) {
                return pluginManager
                    .findDumper(name, columns, types)
                    .orElseGet(
                        () ->
                            jsonDumpers.containsKey(name)
                                ? new Dumper() {
                                  private final ArrayNode output =
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
                                    final var row = output.addObject();
                                    for (var i = 0; i < types.length; i++) {
                                      types[i].accept(
                                          new PackJsonObject(row, columns[i]), values[i]);
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
                    || pluginManager.isOverloaded(Set.of(services)).findAny().isPresent();
              }

              @Override
              public <T> Stream<T> measureFlow(
                  Stream<T> input,
                  String filename,
                  int line,
                  int column,
                  String hash,
                  String oliveFile,
                  int oliveLine,
                  int oliveColumn,
                  String oliveHash) {
                return processor.measureFlow(
                    input,
                    filename,
                    line,
                    column,
                    hash,
                    oliveFile,
                    oliveLine,
                    oliveColumn,
                    oliveHash);
              }

              @Override
              public void oliveRuntime(String filename, int line, int column, long timeInNs) {
                processor.oliveRuntime(filename, line, column, timeInNs);
              }
            },
            inputSource);

    addTypeParser(
        new TypeParser() {
          @Override
          public String description() {
            return "Descriptor";
          }

          @Override
          public String format() {
            return "shesmu::descriptor";
          }

          @Override
          public Imyhat parse(String type) {
            return Imyhat.parse(type);
          }
        });

    addTypeParser(
        new TypeParser() {
          @Override
          public String description() {
            return "JSON-enhanced Descriptor";
          }

          @Override
          public String format() {
            return "shesmu::json_descriptor";
          }

          @Override
          public Imyhat parse(String type) {
            try {
              return RuntimeSupport.MAPPER.readValue(type, Imyhat.class);
            } catch (Exception e) {
              return Imyhat.BAD;
            }
          }
        });

    addTypeParser(
        new TypeParser() {
          @Override
          public String description() {
            return "WDL Type (Pairs as objects)";
          }

          @Override
          public String format() {
            return "shesmu::wdl::objects";
          }

          @Override
          public Imyhat parse(String type) {
            return WdlInputType.parseString(type, true);
          }
        });
    addTypeParser(
        new TypeParser() {
          @Override
          public String description() {
            return "WDL Type (Pairs as tuples)";
          }

          @Override
          public String format() {
            return "shesmu::wdl::tuples";
          }

          @Override
          public Imyhat parse(String type) {
            return WdlInputType.parseString(type, false);
          }
        });
    addTypeParser(
        new BaseHumanTypeParser(definitionRepository, InputFormatDefinition.DUMMY) {
          @Override
          public String description() {
            return "Human-friendly";
          }

          @Override
          public String format() {
            return "shesmu::olive";
          }

          @Override
          public Imyhat typeForName(String name) {
            return null;
          }
        });

    AnnotatedInputFormatDefinition.formats()
        .sorted(Comparator.comparing(InputFormatDefinition::name))
        .forEach(
            format ->
                addTypeParser(
                    new BaseHumanTypeParser(definitionRepository, format) {
                      final Map<String, Imyhat> predefinedTypes =
                          InputFormatDefinition.predefinedTypes(
                              definitionRepository.signatures(), format);

                      @Override
                      public String description() {
                        return "Human-friendly with types from " + format.name();
                      }

                      @Override
                      public String format() {
                        return "shesmu::olive+" + format.name();
                      }

                      @Override
                      public Imyhat typeForName(String name) {
                        return predefinedTypes.get(name);
                      }
                    }));

    for (final var parser : ServiceLoader.load(TypeParser.class)) {
      addTypeParser(parser);
    }

    add(
        "/",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            new StatusPage(this, false) {

              @Override
              protected void emitCore(SectionRenderer renderer) {
                renderer.line("Version", version);
                renderer.line("Build Time", buildTime);
                renderer.line("Build Git Commit", build);
                renderer.link(
                    "Emergency Stop",
                    emergencyStop ? "/resume" : "/stopstopstop",
                    emergencyStop ? "▶ Resume" : "⏹ STOP ALL ACTIONS");
                fileWatcher
                    .paths()
                    .forEach(path -> renderer.line("Data Directory", path.toString()));
                compiler.errorHtml(renderer);
              }

              @Override
              public Stream<Header> headers() {
                return Stream.of(
                    Header.jsModule(
                        "import {initialiseStatusHelp} from \"./help.js\"; initialiseStatusHelp();"));
              }

              @Override
              public Stream<ConfigurationSection> sections() {
                return Stream.of(
                        pluginManager.listConfiguration(),
                        staticActions.listConfiguration(),
                        guidedMeditations.stream().map(GuidedMeditation::configuration),
                        AnnotatedInputFormatDefinition.formats()
                            .flatMap(AnnotatedInputFormatDefinition::configuration))
                    .flatMap(Function.identity());
              }
            }.renderPage(os);
          }
        });

    server.createContext( // This uses createContext instead of add to bypass the fail-fast handler
        "/inflight",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            final var jsonOutput = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8);
            jsonOutput.writeStartObject();
            for (var inflight : INFLIGHT.entrySet()) {
              jsonOutput.writeNumberField(inflight.getKey(), inflight.getValue().toEpochMilli());
            }
            jsonOutput.writeEndObject();
          }
        });
    server.createContext( // This uses createContext instead of add to bypass the fail-fast handler
        "/inflightdash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            new TablePage(this, "Inflight Process", "Started", "Duration") {

              final Instant now = Instant.now();

              @Override
              public String activeUrl() {
                return "inflightdash";
              }

              @Override
              protected void writeRows(TableRowWriter writer) {
                for (var inflight : INFLIGHT.entrySet()) {
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
        "/threaddash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);

          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "threaddash";
              }

              @Override
              public Stream<Header> headers() {
                return Stream.of(
                    Header.jsModule(
                        "import {initialiseThreadDash} from \"./threads.js\";initialiseThreadDash();"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeAttribute("id", "threaddash");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });
    add(
        "/olivedash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          final var parameters = getParameters(t);
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "olivedash";
              }

              @Override
              public Stream<Header> headers() {
                var olivesJson = "[]";
                var savedJson = "null";
                var userFilters = "null";
                var alertFilters = "null";
                try {
                  final var olives = RuntimeSupport.MAPPER.createArrayNode();
                  oliveJson(olives);
                  olivesJson = RuntimeSupport.MAPPER.writeValueAsString(olives);

                  savedJson =
                      handleJsonQueryParameter(
                          SourceOliveLocation.class, parameters.get("saved"), null);
                  userFilters =
                      handleJsonQueryParameter(JsonNode.class, parameters.get("filters"), null);
                  alertFilters =
                      handleJsonQueryParameter(
                          AlertFilter[].class, parameters.get("alert"), new AlertFilter[0]);

                } catch (IOException e) {
                  e.printStackTrace();
                }
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "initialiseOliveDash"
                            + "} from \"./olive.js\";"
                            + "initialiseOliveDash("
                            + olivesJson
                            + ", "
                            + savedJson
                            + ", "
                            + userFilters
                            + ", "
                            + alertFilters
                            + ", "
                            + exportSearches()
                            + ");"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {

                writer.writeStartElement("div");
                writer.writeAttribute("id", "olives");
                writer.writeComment("");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });
    add(
        "/meditationdash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "meditationdash";
              }

              @Override
              public Stream<Header> headers() {
                var filesJson = "[]";
                try {
                  final var files = RuntimeSupport.MAPPER.createArrayNode();
                  compiler
                      .dashboard()
                      .map(fileTable -> fileTable.second().filename())
                      .forEach(files::add);
                  filesJson = RuntimeSupport.MAPPER.writeValueAsString(files);

                } catch (IOException e) {
                  e.printStackTrace();
                }
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "initialiseMeditationDash, register"
                            + "} from \"./guided_meditations.js\";"
                            + "import * as runtime from \"./runtime.js\";\n\n"
                            + guidedMeditations.stream()
                                .flatMap(GuidedMeditation::stream)
                                .collect(Collectors.joining())
                            + "initialiseMeditationDash("
                            + filesJson
                            + ", "
                            + exportSearches()
                            + ");"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {

                writer.writeStartElement("div");
                writer.writeAttribute("id", "meditationdash");
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
          final var parameters = getParameters(t);

          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "actiondash";
              }

              @Override
              public Stream<Header> headers() {
                String savedSearch;
                String locations;
                String userFilter;
                try {
                  locations = RuntimeSupport.MAPPER.writeValueAsString(activeLocations());
                  savedSearch =
                      handleJsonQueryParameter(
                          String.class, parameters.get("saved"), "All Actions");
                  userFilter =
                      handleJsonQueryParameter(JsonNode.class, parameters.get("filters"), null);
                } catch (IOException e) {
                  e.printStackTrace();
                  savedSearch = "null";
                  locations = "[]";
                  userFilter = "'{}'";
                }
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "encodeSearch,"
                            + "initialiseActionDash"
                            + "} from \"./action.js\";"
                            + "initialiseActionDash("
                            + locations
                            + ", "
                            + savedSearch
                            + ", "
                            + userFilter
                            + ", "
                            + exportSearches()
                            + ");"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeAttribute("id", "actiondash");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });
    add(
        "/pausedash",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);

          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "pausedash";
              }

              @Override
              public Stream<Header> headers() {
                String pauses;
                try {
                  pauses = RuntimeSupport.MAPPER.writeValueAsString(pauses());
                } catch (JsonProcessingException e) {
                  e.printStackTrace();
                  pauses = "null";
                }
                return Stream.of(
                    Header.jsModule(
                        "import {"
                            + "initialisePauseDashboard"
                            + "} from \"./pause.js\";"
                            + "initialisePauseDashboard("
                            + pauses
                            + ");"));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeAttribute("id", "pausedash");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });
    add(
        "/defs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "defs";
              }

              @Override
              public Stream<Header> headers() {
                var defs = "[]";
                try {
                  final var array = RuntimeSupport.MAPPER.createArrayNode();
                  actionDefsJson(array);
                  constantDefsJson(array);
                  functionsDefsJson(array);
                  oliveDefsJson(array);
                  refillerDefsJson(array);
                  signatureDefsJson(array);
                  defs = RuntimeSupport.MAPPER.writeValueAsString(array);
                } catch (JsonProcessingException e) {
                  e.printStackTrace();
                }
                return Stream.of(
                    Header.jsModule(
                        String.format(
                            "import {initialiseDefinitionDash} from \"./definitions.js\"; initialiseDefinitionDash(%s);",
                            defs)));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeAttribute("id", "definitiondash");
                writer.writeComment("dashboard content");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });
    add(
        "/inputdefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "inputdefs";
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) {
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
                            format
                                .gangs()
                                .forEach(
                                    gang -> {
                                      try {
                                        writer.writeStartElement("h2");
                                        writer.writeAttribute(
                                            "id", format.name() + "@" + gang.name());
                                        writer.writeCharacters("Gang: @");
                                        writer.writeCharacters(gang.name());
                                        writer.writeEndElement();

                                        writer.writeStartElement("table");
                                        gang.elements()
                                            .forEach(
                                                element -> {
                                                  try {
                                                    writer.writeStartElement("tr");
                                                    writer.writeStartElement("td");
                                                    writer.writeCharacters(element.name());
                                                    writer.writeEndElement();
                                                    writer.writeStartElement("td");
                                                    writer.writeCharacters(element.type().name());
                                                    writer.writeEndElement();
                                                    writer.writeStartElement("td");
                                                    if (element.dropIfDefault()) {
                                                      writer.writeAttribute(
                                                          "title",
                                                          "Dropped from names if blank/default");
                                                      writer.writeCharacters("🚫");
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
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "grouperdefs";
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) {
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
                            for (var i = 0; i < grouper.inputs(); i++) {
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
                              writer.writeCharacters(grouper.input(i).type().toString(Map.of()));
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

                            for (var i = 0; i < grouper.outputs(); i++) {
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
                              writer.writeCharacters(grouper.output(i).type().toString(Map.of()));
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
        "/dumpdefs",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            new TablePage(this) {
              @Override
              public String activeUrl() {
                return "dumpdefs";
              }

              @Override
              protected void writeRows(TableRowWriter row) {
                PluginManager.dumpBound()
                    .sorted(Comparator.comparing(Pair::first))
                    .forEach(x -> row.write(false, x.first(), x.second().toString()));
              }
            }.renderPage(os);
          }
        });

    add(
        "/dumpadr",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
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
                        x -> row.write(false, x.first(), x.second().toMethodDescriptorString()));
              }
            }.renderPage(os);
          }
        });
    add(
        "/configmap",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
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
          try (var os = t.getResponseBody()) {
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
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "typedefs";
              }

              @Override
              public Stream<Header> headers() {
                return Stream.of(
                    Header.jsModule(
                        "import {parseType} from \"./definitions.js\"; window.parseType = parseType;"));
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

                for (final var parser :
                    typeParsers.values().stream()
                        .sorted(Comparator.comparing(TypeParser::description))
                        .collect(Collectors.toList())) {
                  writer.writeStartElement("option");
                  writer.writeAttribute("value", parser.format());
                  writer.writeCharacters(parser.description());
                  writer.writeEndElement();
                }

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
                writer.writeAttribute("class", "button regular");
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

                writer.writeStartElement("tr");
                writer.writeStartElement("td");
                writer.writeCharacters("JSON-enhanced Descriptor");
                writer.writeEndElement();
                writer.writeStartElement("td");
                writer.writeStartElement("span");
                writer.writeAttribute("id", "jsonDescriptorType");
                writer.writeEndElement();
                writer.writeEndElement();
                writer.writeEndElement();

                writer.writeStartElement("tr");
                writer.writeStartElement("td");
                writer.writeCharacters("WDL Type");
                writer.writeEndElement();
                writer.writeStartElement("td");
                writer.writeStartElement("span");
                writer.writeAttribute("id", "wdlType");
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
          final var filters =
              handleJsonQueryParameter(
                  ArrayNode.class,
                  getParameters(t).get("filters"),
                  RuntimeSupport.MAPPER.createArrayNode());
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "alerts";
              }

              @Override
              public Stream<Header> headers() {
                try {
                  return Stream.of(
                      Header.jsModule(
                          String.format(
                              "import {"
                                  + "initialiseAlertDashboard"
                                  + "} from \"./alert.js\";"
                                  + "const output = document.getElementById(\"outputContainer\");"
                                  + "initialiseAlertDashboard(%s, output);",
                              filters)));
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeAttribute("id", "outputContainer");
                writer.writeComment("");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });

    addJson(
        "/actions",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          actionDefsJson(array);
          return array;
        });
    add(
        "/tags",
        t -> {
          final ActionProcessor.Filter[] filters;
          if (t.getRequestMethod().equals("GET")) {
            filters = new ActionProcessor.Filter[0];
          } else {
            try {
              filters =
                  Stream.of(
                          RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ActionFilter[].class))
                      .map(f -> f.convert(processor))
                      .toArray(Filter[]::new);
            } catch (final Exception e) {
              e.printStackTrace();
              t.sendResponseHeaders(400, 0);
              try (var os = t.getResponseBody()) {}
              return;
            }
          }
          t.sendResponseHeaders(200, 0);
          try (final var os = t.getResponseBody();
              final var jsonOutput = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8)) {
            jsonOutput.writeStartArray();
            for (final var tag :
                processor.tags(filters).collect(Collectors.toCollection(TreeSet::new))) {
              jsonOutput.writeString(tag);
            }
            jsonOutput.writeEndArray();
          }
        });
    addJson("/locations", (mapper, query) -> activeLocations());
    addJson("/pauses", (mapper, query) -> pauses());
    addJson("/savedsearches", (mapper, query) -> savedSearches());

    addJson(
        "/refillers",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          refillerDefsJson(array);
          return array;
        });

    addJson(
        "/constants",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          constantDefsJson(array);
          return array;
        });
    addJson(
        "/signatures",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          signatureDefsJson(array);
          return array;
        });
    addJson(
        "/functions",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          functionsDefsJson(array);
          return array;
        });
    addJson(
        "/olivedefinitions",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          oliveDefsJson(array);
          return array;
        });
    addJson(
        "/olives",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          oliveJson(array);
          return array;
        });

    add(
        "/metrodiagram",
        t -> {
          final var location =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), SourceOliveLocation.class);
          final var match =
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
                                              ft.second().hash())))
                              .map(o -> new Pair<>(ft, o)))
                  .findFirst()
                  .orElse(null);
          if (match == null) {
            t.sendResponseHeaders(404, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.getResponseHeaders().set("Content-type", "image/svg+xml; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (final var os = t.getResponseBody()) {
            final var writer = XMLOutputFactory.newFactory().createXMLStreamWriter(os);
            writer.writeStartDocument("utf-8", "1.0");

            MetroDiagram.draw(
                writer,
                pluginManager,
                match.first().second().filename(),
                match.first().second().hash(),
                match.second(),
                match.first().first() == null ? null : match.first().first().inputCount(),
                match.first().second().format(),
                processor);
            writer.writeEndDocument();
          } catch (XMLStreamException e) {
            throw new RuntimeException(e);
          }
        });
    server.createContext( // This uses createContext instead of add to bypass the fail-fast handler
        "/metrics",
        t -> {
          t.getResponseHeaders().set("Content-type", TextFormat.CONTENT_TYPE_004);
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody();
              Writer writer = new PrintWriter(os)) {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.metricFamilySamples());
          }
        });

    add(
        "/query",
        t -> {
          final Query query;
          final ActionProcessor.Filter[] filters;
          try {
            query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), Query.class);
            filters = query.perform(processor);
          } catch (final Exception e) {
            e.printStackTrace();
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            final var jsonOutput = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8);
            jsonOutput.writeStartObject();
            jsonOutput.writeNumberField("offset", query.getSkip());
            jsonOutput.writeNumberField("total", processor.size(filters));
            jsonOutput.writeArrayFieldStart("results");
            processor.stream(pluginManager, filters)
                .skip(Math.max(0, query.getSkip()))
                .limit(query.getLimit())
                .forEach(
                    action -> {
                      try {
                        jsonOutput.writeTree(action);
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    });
            jsonOutput.writeEndArray();
            jsonOutput.writeArrayFieldStart("bulkCommands");
            for (final var command : processor.commonCommands(filters).entrySet()) {
              if (command.getKey().prefers(Preference.ALLOW_BULK)) {
                jsonOutput.writeStartObject();
                jsonOutput.writeStringField("command", command.getKey().command());
                jsonOutput.writeStringField("buttonText", command.getKey().buttonText());
                jsonOutput.writeStringField("icon", command.getKey().icon().icon());
                jsonOutput.writeBooleanField(
                    "showPrompt", command.getKey().prefers(Preference.PROMPT));
                jsonOutput.writeBooleanField(
                    "annoyUser", command.getKey().prefers(Preference.ANNOY_USER));
                jsonOutput.writeNumberField("count", command.getValue());
                jsonOutput.writeEndObject();
              }
            }
            jsonOutput.writeEndArray();
            jsonOutput.writeEndObject();
            jsonOutput.close();
          }
        });
    add(
        "/invalidate",
        t -> {
          final Set<String> names;
          try {
            names = Set.of(RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String[].class));
          } catch (final Exception e) {
            e.printStackTrace();
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          KeyValueCache.caches()
              .filter(cache -> names.contains(cache.name()))
              .forEach(KeyValueCache::invalidateAll);
          LabelledKeyValueCache.caches()
              .filter(cache -> names.contains(cache.name()))
              .forEach(LabelledKeyValueCache::invalidateAll);
          ValueCache.caches()
              .filter(cache -> names.contains(cache.name()))
              .forEach(ValueCache::invalidate);
          t.sendResponseHeaders(201, -1);
          try (var os = t.getResponseBody()) {}
        });
    addJson(
        "/caches",
        (mapper, query) -> {
          final var array = mapper.createArrayNode();
          KeyValueCache.caches()
              .forEach(
                  cache -> {
                    final var node = array.addObject();
                    node.put("name", cache.name());
                    node.put("ttl", cache.ttl());
                    node.put("type", "kv");
                    final var entries = node.putObject("entries");
                    storeEntries(entries, cache);
                  });
          LabelledKeyValueCache.caches()
              .forEach(
                  cache -> {
                    final var node = array.addObject();
                    node.put("name", cache.name());
                    node.put("ttl", cache.ttl());
                    node.put("type", "kv");
                    final var entries = node.putObject("entries");
                    storeEntries(entries, cache);
                  });
          ValueCache.caches()
              .forEach(
                  cache -> {
                    final var node = array.addObject();
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
          final StatsRequest request;
          try {
            request = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), StatsRequest.class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(
                os,
                processor.stats(
                    RuntimeSupport.MAPPER,
                    request.isWait(),
                    Stream.of(request.getFilters())
                        .filter(Objects::nonNull)
                        .map(filterJson -> filterJson.convert(processor))
                        .toArray(ActionProcessor.Filter[]::new)));
          }
        });

    add(
        "/count",
        t -> {
          final ActionFilter[] filters;
          try {
            filters = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ActionFilter[].class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(
                os,
                processor.count(
                    Stream.of(filters)
                        .filter(Objects::nonNull)
                        .map(filterJson -> filterJson.convert(processor))
                        .toArray(Filter[]::new)));
          }
        });
    add(
        "/purge",
        t -> {
          final ActionFilter[] filters;
          try {
            filters = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ActionFilter[].class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(
                os,
                processor.purge(
                    Stream.of(filters)
                        .filter(Objects::nonNull)
                        .map(filterJson -> filterJson.convert(processor))
                        .toArray(Filter[]::new)));
          }
        });
    add(
        "/action-ids",
        t -> {
          final ActionFilter[] filters;
          try {
            filters = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ActionFilter[].class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody();
              final var jsonOutput = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8)) {
            jsonOutput.writeStartArray();
            processor
                .actionIds(
                    Stream.of(filters)
                        .filter(Objects::nonNull)
                        .map(filterJson -> filterJson.convert(processor))
                        .toArray(Filter[]::new))
                .forEach(
                    id -> {
                      try {
                        jsonOutput.writeString(id);
                      } catch (IOException e) {
                        throw new RuntimeException(e);
                      }
                    });
            jsonOutput.writeEndArray();
          }
        });
    add(
        "/drain",
        t -> {
          final ActionFilter[] filters;
          try {
            filters = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ActionFilter[].class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (final var os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (final var actions =
                  processor.drain(
                      pluginManager,
                      Stream.of(filters)
                          .filter(Objects::nonNull)
                          .map(filterJson -> filterJson.convert(processor))
                          .toArray(Filter[]::new));
              final var os = t.getResponseBody();
              final var jsonOutput = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8)) {
            jsonOutput.setCodec(RuntimeSupport.MAPPER);
            jsonOutput.writeStartArray();
            actions.forEach(
                action -> {
                  try {
                    jsonOutput.writeTree(action);
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });
            jsonOutput.writeEndArray();
          }
        });
    add(
        "/command",
        t -> {
          final CommandRequest request;
          try {
            request = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), CommandRequest.class);
          } catch (final Exception e) {
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(
                os,
                processor.command(
                    pluginManager,
                    request.getCommand(),
                    t.getRequestHeaders().getOrDefault("X-Remote-User", List.of()).stream()
                        .findFirst(),
                    Stream.of(request.getFilters())
                        .filter(Objects::nonNull)
                        .map(filterJson -> filterJson.convert(processor))
                        .toArray(Filter[]::new)));
          }
        });
    add(
        "/parsefiltertext",
        t -> {
          final var text = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
          final var result = ActionFilter.extractFromText(text, RuntimeSupport.MAPPER);
          if (result.isPresent()) {
            t.getResponseHeaders().set("Content-type", "application/json");
            t.sendResponseHeaders(200, 0);
            try (var os = t.getResponseBody()) {
              RuntimeSupport.MAPPER.writeValue(os, result.get());
            }
          } else {
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
          }
        });

    add(
        "/currentalerts",
        t -> {
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            os.write(processor.currentAlerts().getBytes(StandardCharsets.UTF_8));
          }
        });
    add(
        "/allalerts",
        t -> {
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (final var os = t.getResponseBody();
              final var jGenerator = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8)) {
            processor.alerts(jGenerator, a -> true);
          }
        });
    add(
        "/queryalerts",
        t -> {
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          final var predicate =
              RuntimeSupport.MAPPER
                  .readValue(t.getRequestBody(), AlertFilter.class)
                  .convert(ActionProcessor.ALERT_FILTER_BUILDER);
          try (var os = t.getResponseBody();
              var jGenerator = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8)) {
            processor.alerts(jGenerator, predicate);
          }
        });
    add(
        "/getalert",
        t -> {
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          final var id = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
          try (var os = t.getResponseBody()) {
            processor.getAlert(os, id);
          }
        });

    add(
        "/constant",
        t -> {
          final var query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
          ConstantLoader loader;
          if (constantLoaders.containsKey(query)) {
            loader = constantLoaders.get(query);
          } else {
            loader =
                Stream.concat(definitionRepository.constants(), compiler.constants())
                    .filter(c -> c.name().equals(query))
                    .findFirst()
                    .map(ConstantDefinition::compile)
                    .orElseGet(
                        () ->
                            target ->
                                target.put(
                                    "error", String.format("Constant %s is unknown.", query)));
            constantLoaders.put(query, loader);
          }
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          final var node = RuntimeSupport.MAPPER.createObjectNode();
          try {
            loader.load(node);
          } catch (final Exception e) {
            node.put("error", e.getMessage());
          }
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(os, node);
          }
        });

    add(
        "/function",
        t -> {
          final var query =
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
                                target.put(
                                    "error", String.format("Function %s is unknown.", query)));
            functionRunners.put(query.getName(), runner);
          }
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          final var node = RuntimeSupport.MAPPER.createObjectNode();
          try {
            runner.run(query.getArgs(), node);
          } catch (final Exception e) {
            node.put("error", e.getMessage());
          }
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(os, node);
          }
        });

    addJson(
        "/variables",
        (mapper, query) -> {
          final var node = mapper.createObjectNode();
          AnnotatedInputFormatDefinition.formats()
              .forEach(
                  source -> {
                    final var sourceNode = node.putObject(source.name());

                    final var variablesNode = sourceNode.putObject("variables");
                    source
                        .baseStreamVariables()
                        .forEach(
                            variable ->
                                variablesNode.put(variable.name(), variable.type().descriptor()));

                    final var gangsNode = sourceNode.putObject("gangs");
                    source
                        .gangs()
                        .forEach(
                            gang -> {
                              final var gangArray = gangsNode.putArray(gang.name());
                              gang.elements()
                                  .forEach(
                                      element -> {
                                        final var elementNode = gangArray.addObject();
                                        elementNode.put("name", element.name());
                                        elementNode.put("type", element.type().descriptor());
                                        elementNode.put("dropIfDefault", element.dropIfDefault());
                                      });
                            });
                  });
          return node;
        });

    AnnotatedInputFormatDefinition.formats()
        .forEach(
            format -> {
              add(
                  String.format("/input/%s", format.name()),
                  t -> downloadInputData(t, inputSource, format, false));
              add(
                  String.format("/input/%s/stale", format.name()),
                  t -> downloadInputData(t, inputSource, format, true));
            });

    add(
        "/check",
        t -> {
          final String script;
          try (var scanner = new Scanner(t.getRequestBody(), StandardCharsets.UTF_8)) {
            script = scanner.useDelimiter("\\Z").next();
          }
          final var errors = new StringBuilder();
          var success =
              (new Compiler(true) {
                    private final NameLoader<ActionDefinition> actions =
                        new NameLoader<>(definitionRepository.actions(), ActionDefinition::name);
                    private final NameLoader<CallableOliveDefinition> definitions =
                        new NameLoader<>(
                            Stream.concat(
                                definitionRepository.oliveDefinitions(),
                                compiler.oliveDefinitions()),
                            CallableOliveDefinition::name);
                    private final NameLoader<FunctionDefinition> functions =
                        new NameLoader<>(
                            Stream.concat(definitionRepository.functions(), compiler.functions()),
                            FunctionDefinition::name);
                    private final NameLoader<RefillerDefinition> refillers =
                        new NameLoader<>(
                            definitionRepository.refillers(), RefillerDefinition::name);

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

                    @Override
                    protected CallableDefinition getOliveDefinition(String name) {
                      return definitions.get(name);
                    }

                    @Override
                    protected CallableDefinitionRenderer getOliveDefinitionRenderer(String name) {
                      return definitions.get(name);
                    }

                    @Override
                    protected RefillerDefinition getRefiller(String name) {
                      return refillers.get(name);
                    }
                  })
                  .compile(
                      script,
                      "shesmu/dyn/Checker",
                      "Uploaded Check Script.shesmu",
                      () -> Stream.concat(definitionRepository.constants(), compiler.constants()),
                      definitionRepository::signatures,
                      null,
                      x -> {},
                      true,
                      false);
          t.getResponseHeaders().set("Content-type", "text/plain; charset=utf-8");
          final var errorBytes = errors.toString().getBytes(StandardCharsets.UTF_8);
          t.sendResponseHeaders(success ? 200 : 400, errorBytes.length);
          try (var os = t.getResponseBody()) {
            os.write(errorBytes);
          }
        });
    add(
        "/simulate",
        t -> {
          final var request =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), SimulateRequest.class);
          request.run(
              DefinitionRepository.concat(definitionRepository, compiler), this, inputSource, t);
        });
    add(
        "/simulate-existing",
        t -> {
          final var request =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), SimulateExistingRequest.class);
          request.run(
              compiler,
              DefinitionRepository.concat(definitionRepository, compiler),
              this,
              inputSource,
              t);
        });
    add(
        "/simulatedash",
        t -> {
          final var existingScript = getParameters(t).get("script");
          final var sharedScript = getParameters(t).get("share");
          final String scriptName;
          final String scriptBody;
          final boolean decodeBody;
          if (existingScript != null) {
            if (compiler.dashboard().noneMatch(p -> p.second().filename().equals(existingScript))) {
              t.sendResponseHeaders(403, -1);
              return;
            }
            final var scriptPath = Paths.get(existingScript);
            scriptName =
                RuntimeSupport.MAPPER.writeValueAsString(scriptPath.getFileName().toString());
            scriptBody = RuntimeSupport.MAPPER.writeValueAsString(Files.readString(scriptPath));
            decodeBody = false;
          } else {
            scriptName = "null";
            if (sharedScript != null) {
              scriptBody = RuntimeSupport.MAPPER.writeValueAsString(sharedScript);
              decodeBody = true;
            } else {
              scriptBody = "null";
              decodeBody = false;
            }
          }
          final var typeFormats =
              RuntimeSupport.MAPPER.writeValueAsString(
                  typeParsers.values().stream()
                      .collect(Collectors.toMap(TypeParser::format, TypeParser::description)));
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
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
                        String.format(
                            "import {"
                                + "initialiseSimulationDashboard"
                                + "} from \"./simulation.js\";"
                                + "const output = document.getElementById(\"outputContainer\");"
                                + "const sound = document.getElementById(\"sound\");"
                                + "initialiseSimulationDashboard(ace, output, sound, %s, %s, %s, %s);",
                            scriptName, scriptBody, decodeBody, typeFormats)));
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
            }.renderPage(os);
          }
        });
    add(
        "/compile-meditation",
        t -> {
          final var request =
              RuntimeSupport.MAPPER.readValue(
                  t.getRequestBody(), MeditationCompilationRequest.class);
          try {
            request.run(DefinitionRepository.concat(definitionRepository, compiler), t);
          } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
          }
        });
    add(
        "/yogastudio",
        t -> {
          final var existingScript = getParameters(t).get("script");
          final var sharedScript = getParameters(t).get("share");
          final String scriptName;
          final String scriptBody;
          final boolean decodeBody;
          if (existingScript != null) {
            if (guidedMeditations.stream()
                .noneMatch(p -> p.filename().toString().equals(existingScript))) {
              t.sendResponseHeaders(403, -1);
              try (var os = t.getResponseBody()) {}
              return;
            }
            final var scriptPath = Paths.get(existingScript);
            scriptName =
                RuntimeSupport.MAPPER.writeValueAsString(scriptPath.getFileName().toString());
            scriptBody = RuntimeSupport.MAPPER.writeValueAsString(Files.readString(scriptPath));
            decodeBody = false;
          } else {
            scriptName = "null";
            if (sharedScript != null) {
              scriptBody = RuntimeSupport.MAPPER.writeValueAsString(sharedScript);
              decodeBody = true;
            } else {
              scriptBody = "null";
              decodeBody = false;
            }
          }
          t.getResponseHeaders().set("Content-type", "text/html; charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            new BasePage(this, false) {
              @Override
              public String activeUrl() {
                return "yogastudio";
              }

              @Override
              public Stream<Header> headers() {
                String locations;
                try {
                  locations =
                      RuntimeSupport.MAPPER.writeValueAsString(
                          processor
                              .locations()
                              .map(SourceLocation::fileName)
                              .collect(Collectors.toSet()));
                } catch (IOException e) {
                  locations = "[]";
                }
                return Stream.of(
                    Header.jsFile("ace.js"),
                    Header.jsFile("ext-searchbox.js"),
                    Header.jsFile("theme-ambiance.js"),
                    Header.jsFile("theme-chrome.js"),
                    Header.jsFile("mode-shesmu.js"),
                    Header.jsModule(
                        String.format(
                            "import {"
                                + "initialiseYogaStudio"
                                + "} from \"./yogastudio.js\";"
                                + "const output = document.getElementById(\"outputContainer\");"
                                + "initialiseYogaStudio(ace, output, %s, %s, %s, %s, %s);",
                            scriptName, scriptBody, decodeBody, locations, exportSearches())));
              }

              @Override
              protected void renderContent(XMLStreamWriter writer) throws XMLStreamException {
                writer.writeStartElement("div");
                writer.writeAttribute("id", "outputContainer");
                writer.writeEndElement();
              }
            }.renderPage(os);
          }
        });
    add(
        "/type",
        t -> {
          final var request =
              RuntimeSupport.MAPPER.readValue(t.getRequestBody(), TypeParseRequest.class);
          final var type =
              typeParsers
                  .getOrDefault(
                      request.getFormat(),
                      new TypeParser() {
                        @Override
                        public String description() {
                          return "Hates everything";
                        }

                        @Override
                        public String format() {
                          return "shesmu::bad";
                        }

                        @Override
                        public Imyhat parse(String type) {
                          return Imyhat.BAD;
                        }
                      })
                  .parse(request.getValue());
          if (type.isBad()) {
            t.sendResponseHeaders(400, 0);
            try (var os = t.getResponseBody()) {}
            return;
          }
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(os, new TypeParseResponse(type));
          }
        });

    add(
        "/actions.js",
        t -> {
          t.getResponseHeaders().set("Content-type", "text/javascript;charset=utf-8");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody();
              var writer = new PrintStream(os, false, StandardCharsets.UTF_8)) {
            writer.println(
                "import { title } from './action.js'; import { breakSlashes } from './util.js'; import { blank, collapsible, jsonParameters, link, objectTable, preformatted, recursiveDifferences, revealWhitespace, table, text, timespan, strikeout } from './html.js';\nexport const actionRender = new Map();\nexport const specialImports = [];\n");
            definitionRepository.writeJavaScriptRenderer(writer);
          }
        });

    add(
        "/pauseolive",
        t -> {
          final var query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ObjectNode.class);
          final var location =
              new SourceLocation(
                  query.get("file").asText(""),
                  query.get("line").asInt(0),
                  query.get("column").asInt(0),
                  query.get("hash").asText(""));
          if (query.has("pause") && !query.get("pause").isNull()) {
            if (query.get("pause").asBoolean(false)) {
              processor.pause(location);
            } else {
              processor.resume(location);
            }
          }
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            os.write(
                Boolean.toString(processor.isPaused(location)).getBytes(StandardCharsets.UTF_8));
          }
        });
    add(
        "/pausefile",
        t -> {
          final var query = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ObjectNode.class);
          final var file = query.get("file").asText("");
          if (query.has("pause") && !query.get("pause").isNull()) {
            if (query.get("pause").asBoolean(false)) {
              processor.pause(file);
            } else {
              processor.resume(file);
            }
          }
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            os.write(Boolean.toString(processor.isPaused(file)).getBytes(StandardCharsets.UTF_8));
          }
        });
    add(
        "/jsondumper",
        t -> {
          final var name = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
          switch (t.getRequestMethod()) {
            case "POST":
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(200, 0);
              try (var os = t.getResponseBody()) {
                os.write(jsonDumpers.getOrDefault(name, "[]").getBytes(StandardCharsets.UTF_8));
              }
              break;
            case "PUT":
              jsonDumpers.computeIfAbsent(name, k -> "[]");
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(201, -1);
              try (var os = t.getResponseBody()) {}
              break;
            case "DELETE":
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(200, 0);
              try (var os = t.getResponseBody()) {
                os.write(
                    Boolean.toString(jsonDumpers.remove(name) != null)
                        .getBytes(StandardCharsets.UTF_8));
              }
              break;
            default:
              t.getResponseHeaders().set("Content-type", "application/json");
              t.sendResponseHeaders(400, 0);
              try (var os = t.getResponseBody()) {
                os.write("{}".getBytes(StandardCharsets.UTF_8));
              }
          }
        });
    add(
        "/parsequery",
        t -> {
          final var input = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), String.class);
          final var response = RuntimeSupport.MAPPER.createObjectNode();
          final var errors = response.putArray("errors");
          ActionFilter.parseQuery(
                  input,
                  name ->
                      savedSearches.stream()
                          .filter(s -> s.name().equals(name))
                          .findAny()
                          .map(s -> ActionFilterBuilder.JSON.and(s.filters())),
                  ((line, column, errorMessage) -> {
                    final var error = errors.addObject();
                    error.put("line", line);
                    error.put("column", column);
                    error.put("message", errorMessage);
                  }))
              .ifPresent(
                  f -> {
                    response.putPOJO("filter", f);
                    response.put("formatted", f.convert(ActionFilterBuilder.QUERY).first());
                  });
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(os, response);
          }
        });
    add(
        "/printquery",
        t -> {
          final var input = RuntimeSupport.MAPPER.readValue(t.getRequestBody(), ActionFilter.class);
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(os, input.convert(ActionFilterBuilder.QUERY).first());
          }
        });
    add(
        "/threads",
        t -> {
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody();
              var output = RuntimeSupport.MAPPER.createGenerator(os)) {
            output.writeStartObject();
            output.writeNumberField("time", System.currentTimeMillis());
            output.writeObjectFieldStart("threads");
            final var threadMxBean = ManagementFactory.getThreadMXBean();
            for (final var thread : threadMxBean.dumpAllThreads(false, false)) {
              output.writeObjectFieldStart(thread.getThreadName());
              output.writeStringField("state", thread.getThreadState().name());
              output.writeNumberField("blockedCount", thread.getBlockedCount());
              output.writeNumberField("blockedTime", thread.getBlockedTime());
              output.writeNumberField("waitCount", thread.getWaitedCount());
              output.writeNumberField("waitTime", thread.getWaitedTime());
              output.writeNumberField("priority", thread.getPriority());
              output.writeNumberField(
                  "cpuTime", threadMxBean.getThreadCpuTime(thread.getThreadId()));
              output.writeArrayFieldStart("trace");
              var last = "";
              for (final var stack : thread.getStackTrace()) {
                var current = "";
                if (stack.getModuleName() == null) {
                  if (stack.getClassName().startsWith("shesmu.dyn.")) {
                    if (stack.getFileName() == null) {
                      continue;
                    } else {
                      current = stack.getFileName();
                    }
                  } else {
                    current = stack.getClassName();
                  }
                } else if (stack.getModuleName().startsWith("java.")
                    || stack.getModuleName().startsWith("jdk.")) {
                  continue;
                } else {
                  current = stack.getModuleName();
                }
                if (!last.equals(current)) {
                  output.writeString(current);
                  last = current;
                }
              }
              output.writeEndArray();
              output.writeEndObject();
            }
            output.writeEndObject();
            output.writeEndObject();
          }
        });
    add("/resume", new EmergencyThrottlerHandler(false));
    add("/stopstopstop", new EmergencyThrottlerHandler(true));
    add("main.css", "text/css; charset=utf-8");
    add("actions.d.js", "text/javascript;charset=utf-8");
    add("action.js", "text/javascript;charset=utf-8");
    add("actionfilters.js", "text/javascript;charset=utf-8");
    add("alert.js", "text/javascript;charset=utf-8");
    add("definitions.js", "text/javascript;charset=utf-8");
    add("guided_meditations.js", "text/javascript;charset=utf-8");
    add("help.js", "text/javascript;charset=utf-8");
    add("histogram.js", "text/javascript;charset=utf-8");
    add("html.js", "text/javascript;charset=utf-8");
    add("io.js", "text/javascript;charset=utf-8");
    add("lz-string.js", "text/javascript;charset=utf-8");
    add("olive.js", "text/javascript;charset=utf-8");
    add("parser.js", "text/javascript;charset=utf-8");
    add("pause.js", "text/javascript;charset=utf-8");
    add("runtime.js", "text/javascript;charset=utf-8");
    add("simulation.js", "text/javascript;charset=utf-8");
    add("stats.js", "text/javascript;charset=utf-8");
    add("threads.js", "text/javascript;charset=utf-8");
    add("util.js", "text/javascript;charset=utf-8");
    add("yogastudio.js", "text/javascript;charset=utf-8");
    add("ace.js", "text/javascript;charset=utf-8");
    add("ext-searchbox.js", "text/javascript;charset=utf-8");
    add("theme-ambiance.js", "text/javascript;charset=utf-8");
    add("theme-chrome.js", "text/javascript;charset=utf-8");
    add("mode-shesmu.js", "text/javascript;charset=utf-8");
    add("bootstrap-icons.svg", "image/svg+xml");
    add("dead.svg", "image/svg+xml");
    add("shesmu.svg", "image/svg+xml");
    add("press.svg", "image/svg+xml");
    add("car.gif", "image/gif");
    add("favicon.png", "image/png");
    add("flamethrower.gif", "image/gif");
    add("holtburn.gif", "image/gif");
    add("indifferent.gif", "image/gif");
    add("ohno.gif", "image/gif");
    add("shrek.gif", "image/gif");
    add("starwars.gif", "image/gif");
    add("thorschariot.gif", "image/gif");
    add("vacuum.gif", "image/gif");
    add("volcano.gif", "image/gif");
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

  public void actionDefsJson(ArrayNode array) {
    definitionRepository
        .actions()
        .forEach(
            actionDefinition -> {
              final var obj = array.addObject();
              obj.put("kind", "action");
              obj.put("name", actionDefinition.name());
              obj.put("description", actionDefinition.description());
              obj.put(
                  "filename",
                  actionDefinition.filename() == null
                      ? null
                      : actionDefinition.filename().toString());
              final var supplementaryInfoArray = obj.putArray("supplementaryInformation");
              actionDefinition
                  .supplementaryInformation()
                  .generate()
                  .forEach(
                      row -> {
                        final var rowNode = supplementaryInfoArray.addObject();
                        rowNode.set("label", row.first().toJson());
                        rowNode.set("value", row.second().toJson());
                      });
              final var parameters = obj.putArray("parameters");
              actionDefinition
                  .parameters()
                  .forEach(
                      param -> {
                        final var paramInfo = parameters.addObject();
                        paramInfo.put("name", param.name());
                        paramInfo.put("type", param.type().toString());
                        paramInfo.put("required", param.required());
                      });
            });
  }

  private ArrayNode activeLocations() {
    final var locationArray = RuntimeSupport.MAPPER.createArrayNode();
    processor.locations().forEach(l -> l.toJson(locationArray, pluginManager));
    return locationArray;
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
          try (var timer = responseTime.start(url);
              var inflight =
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
          final var b = new byte[1024];
          try (var output = t.getResponseBody();
              var input = getClass().getResourceAsStream(url)) {
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
          final var node = fetcher.apply(RuntimeSupport.MAPPER, t.getRequestURI().getQuery());
          t.getResponseHeaders().set("Content-type", "application/json");
          t.sendResponseHeaders(200, 0);
          try (var os = t.getResponseBody()) {
            RuntimeSupport.MAPPER.writeValue(os, node);
          }
        });
  }

  private void addTypeParser(TypeParser parser) {
    typeParsers.put(parser.format(), parser);
  }

  public void constantDefsJson(ArrayNode array) {
    Stream.concat(definitionRepository.constants(), compiler.constants())
        .forEach(
            constant -> {
              final var obj = array.addObject();
              obj.put("kind", "constant");
              obj.put("name", constant.name());
              obj.put("description", constant.description());
              obj.put("type", constant.type().descriptor());
              obj.put(
                  "filename", constant.filename() == null ? null : constant.filename().toString());
            });
  }

  public void downloadInputData(
      HttpExchange t,
      InputSource inputSource,
      AnnotatedInputFormatDefinition format,
      boolean readStale)
      throws IOException {

    if (!readStale
        && (processor.isOverloaded(format.name())
            || pluginManager.isOverloaded(Set.of(format.name())).findAny().isPresent()
            || !inputDownloadSemaphore.tryAcquire())) {
      t.sendResponseHeaders(503, 0);
      try (var os = t.getResponseBody()) {}
      return;
    }

    // If the format opts to use ErrorableStream itself, OK may be true or false. Otherwise,
    // constructor will automatically set OK to true
    ErrorableStream<Object> fetchedInput =
        new ErrorableStream<>(inputSource.fetch(format.name(), readStale));
    if (fetchedInput.isOk()) {
      t.getResponseHeaders().set("Content-type", "application/json");
      t.sendResponseHeaders(200, 0);
    } else {
      t.sendResponseHeaders(503, 0);
      try (var os = t.getResponseBody()) {}
      if (!readStale) inputDownloadSemaphore.release();
      return;
    }
    try (var os = t.getResponseBody();
        var jGenerator = RuntimeSupport.MAPPER.createGenerator(os, JsonEncoding.UTF8)) {
      format.writeJson(jGenerator, fetchedInput);
    } catch (final IOException e) {
      e.printStackTrace();
    } finally {
      inputDownloadSemaphore.release();
    }
  }

  private void emergencyThrottle(boolean stopped) {
    this.emergencyStop = stopped;
    stopGauge.set(stopped ? 1 : 0);
  }

  private String exportSearches() {
    return pluginManager
        .exportSearches(
            new ExportSearch<String>() {
              @Override
              public String linkWithJson(
                  FrontEndIcon icon,
                  String name,
                  FrontEndIcon categoryIcon,
                  String category,
                  String urlStart,
                  String urlEnd,
                  String description) {
                try {
                  return String.format(
                      "{icon:\"%s\", categoryIcon:\"%s\", label: %s, category: %s, description: %s, callback: filters => window.location.href = %s + encodeURIComponent(JSON.stringify(filters)) + %s}",
                      icon.icon(),
                      categoryIcon.icon(),
                      RuntimeSupport.MAPPER.writeValueAsString(name),
                      RuntimeSupport.MAPPER.writeValueAsString(category),
                      RuntimeSupport.MAPPER.writeValueAsString(description),
                      RuntimeSupport.MAPPER.writeValueAsString(urlStart),
                      RuntimeSupport.MAPPER.writeValueAsString(urlEnd));
                } catch (JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              }

              @Override
              public String linkWithUrlSearch(
                  FrontEndIcon icon,
                  String name,
                  FrontEndIcon categoryIcon,
                  String category,
                  String urlStart,
                  String urlEnd,
                  String description) {
                try {
                  return String.format(
                      "{icon:\"%s\", categoryIcon:\"%s\", label: %s, category: %s, description: %s, callback: filters => window.location.href = %s + encodeSearch(filters) + %s}",
                      icon.icon(),
                      categoryIcon.icon(),
                      RuntimeSupport.MAPPER.writeValueAsString(name),
                      RuntimeSupport.MAPPER.writeValueAsString(category),
                      RuntimeSupport.MAPPER.writeValueAsString(description),
                      RuntimeSupport.MAPPER.writeValueAsString(urlStart),
                      RuntimeSupport.MAPPER.writeValueAsString(urlEnd));
                } catch (JsonProcessingException e) {
                  throw new RuntimeException(e);
                }
              }
            })
        .collect(Collectors.joining(",", "[", "]"));
  }

  public void functionsDefsJson(ArrayNode array) {
    Stream.concat(definitionRepository.functions(), compiler.functions())
        .forEach(
            function -> {
              final var obj = array.addObject();
              obj.put("kind", "function");
              obj.put("name", function.name());
              obj.put("description", function.description());
              obj.put("return", function.returnType().descriptor());
              obj.put(
                  "filename", function.filename() == null ? null : function.filename().toString());
              final var parameters = obj.putArray("parameters");
              function
                  .parameters()
                  .forEach(
                      p -> {
                        final var parameter = parameters.addObject();
                        parameter.put("type", p.type().descriptor());
                        parameter.put("description", p.description());
                      });
            });
  }

  @Override
  public Stream<Header> headers() {
    return Stream.of(Header.cssFile("/main.css"), Header.faviconPng(16));
  }

  @Override
  public Set<String> isOverloaded(Set<String> services) {
    return Stream.concat(
            emergencyStop ? Stream.of("Emergency Stop Button") : Stream.empty(),
            pluginManager.isOverloaded(services))
        .collect(Collectors.toSet());
  }

  private URI localname() {
    final var url = System.getenv("LOCAL_URL");
    if (url != null) {
      return URI.create(url);
    }
    try {
      return new URI(
          "http", null, InetAddress.getLocalHost().getCanonicalHostName(), 8081, "/", null, null);
    } catch (final UnknownHostException | URISyntaxException e) {
      e.printStackTrace();
    }
    try {
      return new URI(
          "http", null, InetAddress.getLocalHost().getHostAddress(), 8081, "/", null, null);
    } catch (final UnknownHostException | URISyntaxException e) {
      e.printStackTrace();
    }
    return URI.create("http://localhost:8081/");
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
        NavigationMenu.item("pausedash", "Pauses"),
        NavigationMenu.submenu(
            "Definitions",
            NavigationMenu.item("defs", "Definitions"),
            NavigationMenu.item("grouperdefs", "Groupers"),
            NavigationMenu.item("inputdefs", "Input Formats")),
        NavigationMenu.submenu(
            "Tools",
            NavigationMenu.item("simulatedash", "Olive Simulator"),
            NavigationMenu.item("meditationdash", "Guided Meditations"),
            NavigationMenu.item("yogastudio", "Yoga Studio (Meditation Prototyping)"),
            NavigationMenu.item("typedefs", "Type Converter")),
        NavigationMenu.submenu(
            "Internals",
            NavigationMenu.item("inflightdash", "Active Server Processes"),
            NavigationMenu.item("threaddash", "Threads"),
            NavigationMenu.item("configmap", "Plugin-Olive Mapping"),
            NavigationMenu.item("pluginhashes", "Plugin JAR Hashes"),
            NavigationMenu.item("dumpdefs", "Annotation-Driven Definition"),
            NavigationMenu.item("dumpadr", "Manual Definitions")));
  }

  public void oliveDefsJson(ArrayNode array) {
    Stream.concat(definitionRepository.oliveDefinitions(), compiler.oliveDefinitions())
        .forEach(
            oliveDefinition -> {
              final var obj = array.addObject();
              obj.put("kind", "olive");
              obj.put("name", oliveDefinition.name());
              obj.put("isRoot", oliveDefinition.isRoot());
              obj.put("format", oliveDefinition.format());
              obj.put(
                  "filename",
                  oliveDefinition.filename() == null
                      ? null
                      : oliveDefinition.filename().toString());
              final var output = obj.putObject("output");
              oliveDefinition
                  .outputStreamVariables(null, null)
                  .get()
                  .forEach(v -> output.put(v.name(), v.type().descriptor()));
              final var parameters = obj.putArray("parameters");
              for (var i = 0; i < oliveDefinition.parameterCount(); i++) {
                parameters.add(oliveDefinition.parameterType(i).descriptor());
              }
            });
  }

  private void oliveJson(ArrayNode array) {
    compiler
        .dashboard()
        .sorted(Comparator.comparing(fileTable -> fileTable.second().filename()))
        .forEach(
            fileTable -> {
              final var fileNode = array.addObject();
              fileNode.put("format", fileTable.second().format().name());
              fileNode.put("hash", fileTable.second().hash());
              fileNode.put("file", fileTable.second().filename());
              fileNode.putNull("line");
              fileNode.putNull("column");
              fileNode.put("bytecode", fileTable.second().bytecode());
              fileNode.put("isPaused", processor.isPaused(fileTable.second().filename()));
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
                  "cpuTime",
                  fileTable.first() == null || fileTable.first().cpuTime() == null
                      ? null
                      : TimeUnit.NANOSECONDS.toMillis(fileTable.first().cpuTime().getNano()));
              fileNode.put(
                  "lastRun",
                  fileTable.first() == null ? null : fileTable.first().lastRun().toEpochMilli());
              final var olivesNode = fileNode.putArray("olives");

              fileTable
                  .second()
                  .olives()
                  .sorted(Comparator.comparing(OliveTable::line).thenComparing(OliveTable::column))
                  .forEach(
                      olive -> {
                        final var location =
                            new SourceLocation(
                                fileTable.second().filename(),
                                olive.line(),
                                olive.column(),
                                fileTable.second().hash());
                        final var oliveNode = olivesNode.addObject();
                        location.toJson(oliveNode, pluginManager);
                        oliveNode.put("description", olive.description());
                        oliveNode.put("syntax", olive.syntax());
                        oliveNode.put("produces", olive.produces().name());
                        if (olive.produces() == Produces.ACTIONS) {
                          oliveNode.put("paused", processor.isPaused(location));
                        }
                        olive.tags().sorted().forEach(oliveNode.putArray("tags")::add);
                        final var supplementaryInfoArray =
                            oliveNode.putArray("supplementaryInformation");
                        olive
                            .supplementaryInformation()
                            .generate()
                            .forEach(
                                row -> {
                                  final var rowNode = supplementaryInfoArray.addObject();
                                  rowNode.set("label", row.first().toJson());
                                  rowNode.set("value", row.second().toJson());
                                });
                        final var clauseArray = oliveNode.putArray("clauses");
                        olive
                            .clauses()
                            .forEach(
                                clause -> {
                                  final var clauseNode = clauseArray.addObject();
                                  clauseNode.put("line", clause.line());
                                  clauseNode.put("column", clause.column());
                                  clauseNode.put("syntax", clause.syntax());
                                });
                      });
            });
  }

  public ObjectNode pauses() {
    final var currentOlives =
        compiler
            .dashboard()
            .map(Pair::second)
            .collect(Collectors.toMap(FileTable::filename, FileTable::hash));
    final var pauseInfo = RuntimeSupport.MAPPER.createObjectNode();
    final var liveOlives = pauseInfo.putArray("liveOlives");
    final var deadOlives = pauseInfo.putArray("deadOlives");
    processor
        .pauses()
        .forEach(
            location ->
                location.toJson(
                    currentOlives.getOrDefault(location.fileName(), "").equals(location.hash())
                        ? liveOlives
                        : deadOlives,
                    pluginManager));
    final var liveFiles = pauseInfo.putArray("liveFiles");
    final var deadFiles = pauseInfo.putArray("deadFiles");
    processor
        .pausedFiles()
        .forEach(file -> (currentOlives.containsKey(file) ? liveFiles : deadFiles).add(file));
    return pauseInfo;
  }

  public void refillerDefsJson(ArrayNode array) {
    definitionRepository
        .refillers()
        .forEach(
            refiller -> {
              final var obj = array.addObject();
              obj.put("kind", "refiller");
              obj.put("name", refiller.name());
              obj.put("description", refiller.description());
              obj.put(
                  "filename", refiller.filename() == null ? null : refiller.filename().toString());
              final var supplementaryInfoArray = obj.putArray("supplementaryInformation");
              refiller
                  .supplementaryInformation()
                  .generate()
                  .forEach(
                      row -> {
                        final var rowNode = supplementaryInfoArray.addObject();
                        rowNode.set("label", row.first().toJson());
                        rowNode.set("value", row.second().toJson());
                      });
              final var parameters = obj.putArray("parameters");
              refiller
                  .parameters()
                  .forEach(
                      param -> {
                        final var paramInfo = parameters.addObject();
                        paramInfo.put("name", param.name());
                        paramInfo.put("type", param.type().toString());
                      });
            });
  }

  public ObjectNode savedSearches() {
    final var searchInfo = RuntimeSupport.MAPPER.createObjectNode();
    pluginManager
        .searches(ActionFilterBuilder.JSON)
        .forEach(pair -> searchInfo.putArray(pair.first()).addPOJO(pair.second()));
    savedSearches.stream().forEach(search -> search.write(searchInfo));
    return searchInfo;
  }

  public void signatureDefsJson(ArrayNode array) {
    definitionRepository
        .signatures()
        .forEach(
            constant -> {
              final var obj = array.addObject();
              obj.put("kind", "signature");
              obj.put("name", constant.name());
              obj.put("type", constant.type().descriptor());
              obj.put(
                  "filename", constant.filename() == null ? null : constant.filename().toString());
            });
  }

  @SuppressWarnings("ResultOfMethodCallIgnored")
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
    final var pluginCount = pluginManager.count();
    System.out.printf("Found %d plugins\n", pluginCount);
    System.out.println("Compiling script...");
    compiler.start(fileWatcher);
    staticActions.start(fileWatcher);
    System.out.println("Starting action processor...");
    processor.start(executor);
    System.out.println("Starting scheduler...");
    master.start(executor);
    pluginManager.log("Shesmu started.", Map.of());
  }

  private <L, V> void storeEntries(ObjectNode entries, LabelledKeyValueCache<?, ?, ?, ?> cache) {
    for (final var record : cache) {
      final var node = entries.putObject(record.getKey().toString());
      node.put("collectionSize", record.getValue().collectionSize());
      node.put("lastUpdate", record.getValue().lastUpdate().toEpochMilli());
    }
  }

  private <K, V> void storeEntries(ObjectNode entries, KeyValueCache<?, ?, ?> cache) {
    for (final var record : cache) {
      final var node = entries.putObject(record.getKey().toString());
      node.put("collectionSize", record.getValue().collectionSize());
      node.put("lastUpdate", record.getValue().lastUpdate().toEpochMilli());
    }
  }
}
