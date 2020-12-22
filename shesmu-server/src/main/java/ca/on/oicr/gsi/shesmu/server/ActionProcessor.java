package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.core.input.shesmu.ShesmuIntrospectionValue;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation.SourceLocationLinker;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.AlertFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.SourceOliveLocation;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.plugins.PluginManager;
import ca.on.oicr.gsi.shesmu.util.AutoLock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Collector;
import io.prometheus.client.Gauge;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.http.client.utils.URIBuilder;

/**
 * Background process for launching actions and reporting the results
 *
 * <p>This class collects actions and tries to {@link Action#perform(ActionServices)} until
 * successful.
 */
public final class ActionProcessor
    implements OliveServices,
        InputSource,
        MetroDiagram.OliveFlowReader,
        ActionFilterBuilder<ActionProcessor.Filter> {

  private interface Bin<T> extends Comparator<T> {
    long bucket(T min, long width, T value);

    long minWidth();

    JsonNode name(T min, long offset);

    long span(T min, T max);
  }

  private interface BinMember<T> {
    Optional<T> extract(Entry<Action, Information> input);

    String name();
  }

  private interface Property<T> {
    Stream<T> extract(Entry<Action, Information> input);

    JsonNode json(T input);

    String name();

    Function<T, String> name(Stream<T> input);
  }

  public static final class Alert {
    private Map<String, String> annotations = new TreeMap<>();
    private String endsAt;
    @JsonIgnore private Instant expiryTime;
    private String generatorURL;
    private final String id;
    private Map<String, String> labels = new TreeMap<>();

    private final Set<SourceLocation> locations = ConcurrentHashMap.newKeySet();
    private String startsAt;

    public Alert(String id) {
      this.id = id;
    }

    public void expiresIn(long ttl) {
      expiryTime = Instant.now().plusSeconds(ttl);
    }

    public String expiryTime() {
      return DateTimeFormatter.ISO_INSTANT.format(expiryTime);
    }

    public Map<String, String> getAnnotations() {
      return annotations;
    }

    public String getEndsAt() {
      return endsAt;
    }

    public String getGeneratorURL() {
      return generatorURL;
    }

    public Map<String, String> getLabels() {
      return labels;
    }

    public Set<SourceLocation> getLocations() {
      return locations;
    }

    public String getStartsAt() {
      return startsAt;
    }

    public String id() {
      return id;
    }

    public boolean isLive() {
      return expiryTime.isAfter(Instant.now());
    }

    public void setAnnotations(Map<String, String> annotations) {
      this.annotations = annotations;
    }

    public void setEndsAt(Instant endsAt) {
      this.endsAt = DateTimeFormatter.ISO_INSTANT.format(endsAt);
    }

    public void setEndsAt(String endsAt) {
      this.endsAt = endsAt;
    }

    public void setGeneratorURL(String generatorURL) {
      this.generatorURL = generatorURL;
    }

    public void setLabels(Map<String, String> labels) {
      this.labels = labels;
    }

    public void setStartsAt(Instant startsAt) {
      this.startsAt = DateTimeFormatter.ISO_INSTANT.format(startsAt);
    }

    public void setStartsAt(String startsAt) {
      this.startsAt = startsAt;
    }
  }

  /** A filter all the actions based on some criteria */
  public abstract static class Filter {
    protected abstract boolean check(Action action, Information info);

    /** Produce a filter that selects the opposite output of this filter. */
    public Filter negate() {
      final Filter owner = this;
      return new Filter() {

        @Override
        protected boolean check(Action action, Information info) {
          return !owner.check(action, info);
        }
      };
    }
  }

  private static class Information {
    final String id;
    Instant lastAdded = Instant.now();
    Instant lastChecked = Instant.EPOCH;
    ActionState lastState = ActionState.UNKNOWN;
    Instant lastStateTransition = Instant.now();
    final Set<SourceLocation> locations = ConcurrentHashMap.newKeySet();
    final Set<String> tags = ConcurrentHashMap.newKeySet();
    String thrown;
    volatile boolean updateInProgress;

    private Information(Action action) {
      String id;
      try {
        final MessageDigest digest = MessageDigest.getInstance("SHA1");
        digest.update(action.type().getBytes(StandardCharsets.UTF_8));
        action.generateUUID(digest::update);
        id = "shesmu:" + Utils.bytesToHex(digest.digest());
      } catch (NoSuchAlgorithmException e) {
        e.printStackTrace();
        id = "";
      }
      this.id = id;
    }
  }

  private abstract static class InstantFilter extends Filter {
    private final Optional<Instant> end;
    private final Optional<Instant> start;

    private InstantFilter(Optional<Instant> start, Optional<Instant> end) {
      super();
      this.start = start;
      this.end = end;
    }

    @Override
    protected final boolean check(Action action, Information info) {
      return get(action, info)
          .map(
              time ->
                  start.map(s -> !time.isBefore(s)).orElse(true)
                      && end.map(e -> !time.isAfter(e)).orElse(true))
          .orElse(false);
    }

    protected abstract Optional<Instant> get(Action action, Information info);
  }

  static Function<String, String> commonPathPrefix(Stream<String> input) {
    final List<String> items = input.collect(Collectors.toList());
    if (items.isEmpty()) {
      return Function.identity();
    }

    final List<String> commonPrefix =
        SLASH.splitAsStream(items.get(0)).collect(Collectors.toList());
    commonPrefix.remove(commonPrefix.size() - 1);
    for (int i = 1; i < items.size(); i++) {
      final List<String> parts = Arrays.asList(SLASH.split(items.get(i)));
      int x = 0;
      while (x < parts.size() - 1
          && x < commonPrefix.size()
          && parts.get(x).equals(commonPrefix.get(x))) x++;
      commonPrefix.subList(x, commonPrefix.size()).clear();
    }
    return x -> SLASH.splitAsStream(x).skip(commonPrefix.size()).collect(Collectors.joining("/"));
  }

  private static <T extends Comparable<T>> void propertySummary(
      ArrayNode table, Property<T> property, List<Entry<Action, Information>> actions) {
    final TreeMap<T, Set<Action>> states =
        actions
            .stream()
            .flatMap(
                action -> property.extract(action).map(prop -> new Pair<>(prop, action.getKey())))
            .collect(
                Collectors.groupingBy(
                    Pair::first,
                    TreeMap::new,
                    Collectors.mapping(Pair::second, Collectors.toSet())));

    final Function<T, String> namer = property.name(states.keySet().stream());
    for (final Entry<T, Set<Action>> state : states.entrySet()) {
      final ObjectNode row = table.addObject();
      row.put("title", "Total");
      row.put("value", state.getValue().size());
      row.put("kind", "property");
      row.put("type", property.name());
      row.put("property", namer.apply(state.getKey()));
      row.set("json", property.json(state.getKey()));
    }
  }

  public static final int ACTION_PERFORM_THREADS =
      Math.max(1, Runtime.getRuntime().availableProcessors() * 5 - 1);
  private static final BinMember<Instant> ADDED =
      new BinMember<Instant>() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return Optional.of(input.getValue().lastAdded).filter(Instant.EPOCH::isBefore);
        }

        @Override
        public String name() {
          return "added";
        }
      };
  public static final AlertFilterBuilder<Predicate<Alert>> ALERT_FILTER_BUILDER =
      new AlertFilterBuilder<Predicate<Alert>>() {
        @Override
        public Predicate<Alert> and(Stream<Predicate<Alert>> filters) {
          final List<Predicate<Alert>> predicates = filters.collect(Collectors.toList());
          return alert -> predicates.stream().allMatch(predicate -> predicate.test(alert));
        }

        @Override
        public Predicate<Alert> fromSourceLocation(Stream<SourceOliveLocation> locations) {
          final List<Predicate<SourceLocation>> predicates = locations.collect(Collectors.toList());
          return alert ->
              alert
                  .locations
                  .stream()
                  .anyMatch(
                      location ->
                          predicates.stream().anyMatch(predicate -> predicate.test(location)));
        }

        @Override
        public Predicate<Alert> hasLabelName(Pattern labelName) {
          return alert -> alert.labels.keySet().stream().anyMatch(labelName.asPredicate());
        }

        @Override
        public Predicate<Alert> hasLabelName(String labelName) {
          return alert -> alert.labels.containsKey(labelName);
        }

        @Override
        public Predicate<Alert> hasLabelValue(String labelName, Pattern regex) {
          return alert -> {
            final String value = alert.labels.get(labelName);
            return value != null && regex.matcher(value).matches();
          };
        }

        @Override
        public Predicate<Alert> hasLabelValue(String labelName, String labelValue) {
          return alert -> labelValue.equals(alert.labels.get(labelName));
        }

        @Override
        public Predicate<Alert> isLive() {
          return Alert::isLive;
        }

        @Override
        public Predicate<Alert> negate(Predicate<Alert> filter) {
          return filter.negate();
        }

        @Override
        public Predicate<Alert> or(Stream<Predicate<Alert>> filters) {
          final List<Predicate<Alert>> predicates = filters.collect(Collectors.toList());
          return alert -> predicates.stream().anyMatch(predicate -> predicate.test(alert));
        }
      };
  private static final BinMember<Instant> CHECKED =
      new BinMember<Instant>() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return Optional.of(input.getValue().lastChecked).filter(Instant.EPOCH::isBefore);
        }

        @Override
        public String name() {
          return "checked";
        }
      };
  private static final BinMember<Instant> EXTERNAL =
      new BinMember<Instant>() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return input.getKey().externalTimestamp().filter(Instant.EPOCH::isBefore);
        }

        @Override
        public String name() {
          return "external";
        }
      };
  private static final JsonNodeFactory JSON_FACTORY = JsonNodeFactory.withExactBigDecimals(false);
  private static final Bin<Instant> INSTANT_BIN =
      new Bin<Instant>() {
        @Override
        public final long bucket(Instant min, long width, Instant value) {
          return (value.toEpochMilli() - min.toEpochMilli()) / width;
        }

        @Override
        public final int compare(Instant o1, Instant o2) {
          return o1.compareTo(o2);
        }

        @Override
        public long minWidth() {
          return 60_000;
        }

        @Override
        public JsonNode name(Instant min, long offset) {
          return JSON_FACTORY.numberNode(min.toEpochMilli() + offset);
        }

        @Override
        public final long span(Instant min, Instant max) {
          return max.toEpochMilli() - min.toEpochMilli();
        }
      };
  private static final Property<ActionState> ACTION_STATE =
      new Property<ActionState>() {

        @Override
        public Stream<ActionState> extract(Entry<Action, Information> input) {
          return Stream.of(input.getValue().lastState);
        }

        @Override
        public JsonNode json(ActionState input) {
          return JSON_FACTORY.textNode(input.name());
        }

        @Override
        public String name() {
          return "status";
        }

        @Override
        public Function<ActionState, String> name(Stream<ActionState> input) {
          input.close();
          return ActionState::name;
        }
      };
  public static final Gauge OLIVE_FLOW =
      Gauge.build(
              "shesmu_olive_data_flow", "The number of items passing through each olive clause.")
          .labelNames(
              "filename",
              "line",
              "column",
              "hash",
              "olive_file",
              "olive_line",
              "olive_column",
              "olive_hash")
          .register();
  private static final Gauge OLIVE_RUN_TIME =
      Gauge.build("shesmu_olive_run_time", "The runtime of an olive in seconds.")
          .labelNames("filename", "line", "column")
          .register();
  private static final Pattern SLASH = Pattern.compile("/");
  private static final Property<String> SOURCE_FILE =
      new Property<String>() {

        @Override
        public Stream<String> extract(Entry<Action, Information> input) {
          return input.getValue().locations.stream().map(SourceLocation::fileName);
        }

        @Override
        public JsonNode json(String input) {
          return JSON_FACTORY.textNode(input);
        }

        @Override
        public String name() {
          return "sourcefile";
        }

        @Override
        public Function<String, String> name(Stream<String> input) {
          return commonPathPrefix(input);
        }
      };
  private static final BinMember<Instant> STATUS_CHANGED =
      new BinMember<Instant>() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return Optional.of(input.getValue().lastStateTransition).filter(Instant.EPOCH::isBefore);
        }

        @Override
        public String name() {
          return "statuschanged";
        }
      };
  private static final Property<String> TAG =
      new Property<String>() {

        @Override
        public Stream<String> extract(Entry<Action, Information> input) {
          return Stream.concat(input.getValue().tags.stream(), input.getKey().tags());
        }

        @Override
        public JsonNode json(String input) {
          return JSON_FACTORY.textNode(input);
        }

        @Override
        public String name() {
          return "tag";
        }

        @Override
        public Function<String, String> name(Stream<String> input) {
          input.close();
          return Function.identity();
        }
      };
  private static final Property<String> TYPE =
      new Property<String>() {

        @Override
        public Stream<String> extract(Entry<Action, Information> input) {
          return Stream.of(input.getKey().type());
        }

        @Override
        public JsonNode json(String input) {
          return JSON_FACTORY.textNode(input);
        }

        @Override
        public String name() {
          return "type";
        }

        @Override
        public Function<String, String> name(Stream<String> input) {
          input.close();
          return Function.identity();
        }
      };
  private static final LatencyHistogram actionPerformTime =
      new LatencyHistogram(
          "shesmu_action_perform_time",
          "The length of time for an action to update it state in seconds.",
          "type");
  private static final Gauge actionThrows =
      Gauge.build(
              "shesmu_action_perform_throw",
              "The number of actions that threw an exception in their last attempt.")
          .register();
  private static final Gauge currentRunningActionsGauge =
      Gauge.build(
              "shesmu_action_currently_running",
              "The number of actions that are running (or waiting for a thread).")
          .register();
  private static final Gauge lastAdd =
      Gauge.build("shesmu_action_add_last_time", "The last time an actions was added.").register();
  private static final Gauge lastRun =
      Gauge.build("shesmu_action_perform_last_time", "The last time the actions were processed.")
          .register();
  private static final Gauge oldest =
      Gauge.build("shesmu_action_oldest_time", "The oldest action in a particular state.")
          .labelNames("state", "type")
          .register();
  private static final Gauge scheduledInRound =
      Gauge.build(
              "shesmu_action_scheduled_in_round",
              "The number of actions that were schedule for processing in the last round.")
          .register();
  private static final Gauge stateCount =
      Gauge.build("shesmu_action_state_count", "The number of actions in a particular state.")
          .labelNames("state", "type")
          .register();
  private final ActionServices actionServices;
  private final Map<Action, Information> actions = new ConcurrentHashMap<>();
  private final AutoLock alertLock = new AutoLock();
  private final Map<Map<String, String>, Alert> alerts = new HashMap<>();
  private final String baseUri;
  private String currentAlerts = "[]";
  private final AtomicInteger currentRunningActions = new AtomicInteger();
  private final Set<String> knownActionTypes = ConcurrentHashMap.newKeySet();
  private final PluginManager manager;
  private final Set<String> pausedFiles = ConcurrentHashMap.newKeySet();
  private final Set<SourceLocation> pausedOlives = ConcurrentHashMap.newKeySet();
  private final Set<SourceLocation> sourceLocations = ConcurrentHashMap.newKeySet();
  private final ScheduledExecutorService timeoutExecutor =
      Executors.newSingleThreadScheduledExecutor();
  private final ExecutorService workExecutor = Executors.newFixedThreadPool(ACTION_PERFORM_THREADS);

  public ActionProcessor(String baseUri, PluginManager manager, ActionServices actionServices) {
    super();
    this.baseUri = baseUri;
    this.manager = manager;
    this.actionServices = actionServices;
  }

  /**
   * Add an action to the execution pool
   *
   * <p>If this action is a duplicate of an existing action, the existing state is kept.
   */
  @Override
  public synchronized boolean accept(
      Action action, String filename, int line, int column, String hash, String[] tags) {
    Information information;
    boolean isDuplicate;
    knownActionTypes.add(action.type());
    if (!actions.containsKey(action)) {
      information = new Information(action);
      action.accepted(information.id);
      actions.put(action, information);
      stateCount.labels(ActionState.UNKNOWN.name(), action.type()).inc();
      isDuplicate = false;
    } else {
      information = actions.get(action);
      information.lastAdded = Instant.now();
      isDuplicate = true;
    }
    final SourceLocation location = new SourceLocation(filename, line, column, hash);
    information.locations.add(location);
    information.tags.addAll(Arrays.asList(tags));
    sourceLocations.add(location);
    lastAdd.setToCurrentTime();
    return isDuplicate;
  }

  @Override
  public boolean accept(
      String[] labels,
      String[] annotations,
      long ttl,
      String filename,
      int line,
      int column,
      String hash)
      throws Exception {
    final Map<String, String> labelMap = repack(labels, "Labels");
    // Alert Manager doesn't officially require an instance, but it gets weird if not included, so
    // add one unless the olive supplied it.
    if (!labelMap.containsKey("instance")) {
      labelMap.put("instance", baseUri);
    }
    try (AutoCloseable lock = alertLock.acquire()) {
      Alert alert;
      final boolean duplicate = alerts.containsKey(labelMap);
      if (duplicate) {
        alert = alerts.get(labelMap);
      } else {
        final MessageDigest digest = MessageDigest.getInstance("SHA1");
        for (final Entry<String, String> entry : labelMap.entrySet()) {
          digest.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
          digest.update((byte) 0);
          digest.update(entry.getValue().getBytes(StandardCharsets.UTF_8));
          digest.update((byte) 0);
        }
        alert = new Alert(Utils.bytesToHex(digest.digest()));
        alert.setLabels(labelMap);
        alert.setStartsAt(Instant.now());
        alerts.put(labelMap, alert);
        final URIBuilder builder = new URIBuilder(baseUri);
        builder.setFragment(alert.id());
        alert.setGeneratorURL(builder.build().toASCIIString());
      }
      alert.setAnnotations(repack(annotations, "Annotations"));
      alert.expiresIn(ttl);
      alert.locations.add(new SourceLocation(filename, line, column, hash));
    }
    return false;
  }

  /**
   * Check that an action was last added in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public Filter added(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return Optional.of(info.lastAdded);
      }
    };
  }

  public void alerts(JsonGenerator output, Predicate<Alert> predicate) throws IOException {
    output.writeStartArray();
    for (final Alert alert : alerts.values()) {
      if (predicate.test(alert)) {
        output.writeObject(alert);
      }
    }
    output.writeEndArray();
  }

  /** Check that all of the filters match */
  public Filter and(Stream<Filter> filters) {
    return new Filter() {
      private final List<Filter> filterList = filters.collect(Collectors.toList());

      @Override
      protected boolean check(Action action, Information info) {
        return filterList.stream().allMatch(f -> f.check(action, info));
      }
    };
  }

  /**
   * Check that an action was last checked in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public Filter checked(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return Optional.of(info.lastChecked);
      }
    };
  }

  /**
   * Execute a command on matching actions
   *
   * @param command the command to perform
   * @param user the user performing the command, if known
   * @param filters the filters to select actions
   * @return the number of actions that were able to execute the command
   */
  public long command(
      PluginManager pluginManager, String command, Optional<String> user, Filter... filters) {
    return startStream(filters)
        .filter(
            e -> {
              final List<String> performedCommands =
                  e.getKey()
                      .commands()
                      .filter(
                          c -> c.command().equals(command) && c.process(e.getKey(), command, user))
                      .map(ActionCommand::command)
                      .collect(Collectors.toList());
              final Map<String, String> labels = new TreeMap<>();
              labels.put("action", e.getValue().id);
              labels.put("action_type", e.getKey().type());
              for (final String performedCommand : performedCommands) {
                labels.put("command", performedCommand);
                pluginManager.log("Performed command", labels);
              }
              if (!performedCommands.isEmpty()) {
                if (e.getValue().lastState != ActionState.UNKNOWN) {
                  stateCount.labels(e.getValue().lastState.name(), e.getKey().type()).dec();
                  stateCount.labels(ActionState.UNKNOWN.name(), e.getKey().type()).inc();
                  e.getValue().lastStateTransition = Instant.now();
                }
                e.getValue().lastState = ActionState.UNKNOWN;
                return true;
              }
              return false;
            })
        .count();
  }

  public Map<ActionCommand<?>, Long> commonCommands(Filter... filters) {
    return startStream(filters)
        .flatMap(e -> e.getKey().commands())
        .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
  }

  private <T extends Comparable<T>, U extends Comparable<U>> void crosstab(
      ArrayNode output,
      List<Entry<Action, Information>> input,
      Property<T> row,
      Property<U> column) {
    final Set<T> rows = input.stream().flatMap(row::extract).collect(Collectors.toSet());
    final Set<U> columns = input.stream().flatMap(column::extract).collect(Collectors.toSet());
    // Rows and columns which are always present aren't interesting
    rows.removeIf(value -> input.stream().allMatch(e -> row.extract(e).anyMatch(value::equals)));
    columns.removeIf(
        value -> input.stream().allMatch(e -> column.extract(e).anyMatch(value::equals)));

    // If there's too little or too much data, just bail.
    if (rows.size() < 2
        || columns.size() < 2
        || Math.max(rows.size(), columns.size()) > 40
        || Math.min(rows.size(), columns.size()) > 20) {
      return;
    }
    // Transpose this thing to have more rows that columns
    if (rows.size() >= columns.size()) {
      crosstabHelper(output, input, row, column, rows, columns);
    } else {
      crosstabHelper(output, input, column, row, columns, rows);
    }
  }

  private <T extends Comparable<T>, U extends Comparable<U>> void crosstabHelper(
      ArrayNode output,
      List<Entry<Action, Information>> input,
      Property<T> row,
      Property<U> column,
      Set<T> rows,
      Set<U> columns) {
    final ObjectNode node = output.addObject();
    node.put("type", "crosstab");
    node.put("column", column.name());
    node.put("row", row.name());
    final ObjectNode rowsJson = node.putObject("rows");
    final Function<T, String> rowNamer = row.name(rows.stream());
    final Function<U, String> columnNamer = column.name(columns.stream());
    final ArrayNode columnsJson = node.putArray("columns");
    columns
        .stream()
        .map(c -> new Pair<>(columnNamer.apply(c), column.json(c)))
        .sorted(Comparator.comparing(Pair::first))
        .forEach(
            colPair -> {
              final ObjectNode columnJson = columnsJson.addObject();
              columnJson.put("name", colPair.first());
              columnJson.set("value", colPair.second());
            });
    final Map<T, Set<Entry<Action, Information>>> map =
        input
            .stream()
            .flatMap(e -> row.extract(e).map(t -> new Pair<>(t, e)))
            .collect(
                Collectors.groupingBy(
                    Pair::first, Collectors.mapping(Pair::second, Collectors.toSet())));
    final ObjectNode data = node.putObject("data");
    for (final Entry<T, Set<Entry<Action, Information>>> entry : map.entrySet()) {
      final String name = rowNamer.apply(entry.getKey());
      final ObjectNode inner = data.putObject(name);
      rowsJson.set(name, row.json(entry.getKey()));

      for (final Entry<U, Long> i :
          entry
              .getValue()
              .stream()
              .filter(
                  c ->
                      !c.equals(
                          entry.getKey())) // Normally, we don't expect the row to equal the columns
              // because they are different types, but when generating a
              // tag×tag matrix, we want to erase the diagonal
              .flatMap(e -> column.extract(e).map(u -> new Pair<>(u, e)))
              .collect(Collectors.groupingBy(Pair::first, Collectors.counting()))
              .entrySet()) {
        inner.put(columnNamer.apply(i.getKey()), i.getValue());
      }
    }
  }

  public String currentAlerts() {
    return currentAlerts;
  }

  /**
   * Check that an action's external timestamp is in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public Filter external(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return action.externalTimestamp();
      }
    };
  }

  @Override
  public Stream<Object> fetch(String format, boolean readStale) {
    return format.equals("shesmu")
        ? actions
            .entrySet()
            .stream()
            .map(
                entry ->
                    new ShesmuIntrospectionValue(
                        entry.getKey(),
                        entry.getValue().id,
                        entry.getValue().lastStateTransition,
                        entry.getValue().lastChecked,
                        entry.getValue().lastAdded,
                        entry.getValue().lastStateTransition,
                        entry.getValue().lastState,
                        entry.getValue().locations,
                        new TreeSet<>(entry.getValue().tags)))
        : Stream.empty();
  }

  @Override
  public Dumper findDumper(String name, Imyhat... types) {
    return null;
  }

  /**
   * Checks that an action was generated in a particular file
   *
   * @param files the names of the files
   */
  public Filter fromFile(String... files) {
    final Set<String> set = Stream.of(files).collect(Collectors.toSet());
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return info.locations.stream().map(SourceLocation::fileName).anyMatch(set::contains);
      }
    };
  }

  /**
   * Checks that an action was generated in a particular source location
   *
   * @param locations the source locations
   */
  public Filter fromSourceLocation(Stream<SourceOliveLocation> locations) {
    final List<Predicate<SourceLocation>> list = locations.collect(Collectors.toList());
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return list.stream().anyMatch(l -> info.locations.stream().anyMatch(l));
      }
    };
  }

  public Filter fromSourceLocation(SourceLocation location) {
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return info.locations.contains(location);
      }
    };
  }

  public void getAlert(OutputStream output, String id) throws IOException {
    final Alert alert =
        alerts.values().stream().filter(a -> a.id.equals(id)).findAny().orElse(null);
    RuntimeSupport.MAPPER.writeValue(output, alert);
  }

  @SafeVarargs
  private final <T> void histogram(
      ArrayNode output,
      int count,
      List<Entry<Action, Information>> input,
      Bin<T> bin,
      BinMember<T>... members) {
    final List<T> contents =
        Stream.of(members)
            .flatMap(
                member ->
                    input
                        .stream()
                        .flatMap(v -> member.extract(v).map(Stream::of).orElseGet(Stream::empty)))
            .collect(Collectors.toList());
    final Optional<T> min = contents.stream().min(bin);
    final Optional<T> max = contents.stream().max(bin);
    if (!min.isPresent() || !max.isPresent() || min.get().equals(max.get())) {
      return;
    }
    long width = bin.span(min.get(), max.get()) / count;
    final int bucketsLength;
    if (width < bin.minWidth()) {
      // If the buckets are less than a minimum width, use buckets of the minimum width over the
      // range
      width = bin.minWidth();
      bucketsLength = (int) (bin.span(min.get(), max.get()) / bin.minWidth()) + 1;
    } else {
      bucketsLength = count;
    }
    if (bucketsLength < 2) {
      return;
    }
    final long binWidth = width;

    final ObjectNode node = output.addObject();
    node.put("type", "histogram");
    final ArrayNode boundaries = node.putArray("boundaries");
    for (int i = 0; i < bucketsLength; i++) {
      boundaries.add(bin.name(min.get(), i * width));
    }
    boundaries.add(bin.name(max.get(), 0));
    final ObjectNode counts = node.putObject("counts");
    for (final BinMember<T> member : members) {
      int[] buckets = new int[bucketsLength];
      for (final T value : contents) {
        buckets[Math.min((int) bin.bucket(min.get(), binWidth, value), buckets.length - 1)]++;
      }
      Arrays.stream(buckets).forEach(counts.putArray(member.name())::add);
    }
  }

  @SafeVarargs
  private final <T, U> void histogramByProperty(
      ArrayNode output,
      int count,
      List<Entry<Action, Information>> input,
      Property<U> property,
      Bin<T> bin,
      BinMember<T>... members) {
    final List<T> contents =
        Stream.of(members)
            .flatMap(
                member ->
                    input
                        .stream()
                        .flatMap(v -> member.extract(v).map(Stream::of).orElseGet(Stream::empty)))
            .collect(Collectors.toList());
    final Optional<T> min = contents.stream().min(bin);
    final Optional<T> max = contents.stream().max(bin);
    if (!min.isPresent() || !max.isPresent() || min.get().equals(max.get())) {
      return;
    }
    final Set<U> properties = input.stream().flatMap(property::extract).collect(Collectors.toSet());
    // Properties which are always present aren't interesting
    properties.removeIf(
        value -> input.stream().allMatch(e -> property.extract(e).anyMatch(value::equals)));
    if (properties.size() < 2) {
      return;
    }

    long width = bin.span(min.get(), max.get()) / count;
    final int bucketsLength;
    if (width < bin.minWidth()) {
      // If the buckets are less than a minimum width, use buckets of the minimum width over the
      // range
      width = bin.minWidth();
      bucketsLength = (int) (bin.span(min.get(), max.get()) / bin.minWidth()) + 1;
    } else {
      bucketsLength = count;
    }
    if (bucketsLength < 2) {
      return;
    }
    final long binWidth = width;

    final ObjectNode node = output.addObject();
    node.put("type", "histogram-by-property");
    node.put("property", property.name());
    properties.stream().map(property::json).forEach(node.putArray("properties")::add);
    final ArrayNode boundaries = node.putArray("boundaries");
    for (int i = 0; i < bucketsLength; i++) {
      boundaries.add(bin.name(min.get(), i * width));
    }
    boundaries.add(bin.name(max.get(), 0));
    final ObjectNode counts = node.putObject("counts");
    for (final BinMember<T> member : members) {
      final Map<U, Map<Integer, Long>> counting =
          input
              .stream()
              .flatMap(
                  v ->
                      member
                          .extract(v)
                          .map(x -> property.extract(v).map(p -> new Pair<>(p, x)))
                          .orElseGet(Stream::empty))
              .collect(
                  Collectors.groupingBy(
                      Pair::first,
                      Collectors.groupingBy(
                          v ->
                              Math.min(
                                  (int) bin.bucket(min.get(), binWidth, v.second()),
                                  bucketsLength - 1),
                          Collectors.counting())));
      final ArrayNode memberValues = counts.putArray(member.name());
      for (final Map.Entry<U, Map<Integer, Long>> entry : counting.entrySet()) {
        final ObjectNode propertyCount = memberValues.addObject();
        propertyCount.set("value", property.json(entry.getKey()));
        IntStream.range(0, bucketsLength)
            .mapToLong(i -> entry.getValue().getOrDefault(i, 0L))
            .forEach(propertyCount.putArray("counts")::add);
      }
    }
  }

  /**
   * Get actions by unique ID.
   *
   * @param ids the allowed identifiers
   */
  public Filter ids(List<String> ids) {
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return ids.contains(info.id);
      }
    };
  }

  @Override
  public boolean isOverloaded(String... services) {
    return false;
  }

  public boolean isPaused(SourceLocation location) {
    return pausedOlives.contains(location);
  }

  public boolean isPaused(String file) {
    return pausedFiles.contains(file);
  }

  /**
   * Checks that an action is in one of the specified actions states
   *
   * @param states the permitted states
   */
  public Filter isState(ActionState... states) {
    final EnumSet<ActionState> set = EnumSet.noneOf(ActionState.class);
    set.addAll(Arrays.asList(states));
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return set.contains(info.lastState);
      }
    };
  }

  public Stream<SourceLocation> locations() {
    return actions.values().stream().flatMap(i -> i.locations.stream()).distinct();
  }

  @Override
  public <T> Stream<T> measureFlow(
      Stream<T> input,
      String fileName,
      int line,
      int column,
      String hash,
      String oliveFile,
      int oliveLine,
      int oliveColumn,
      String oliveHash) {
    final Gauge.Child child =
        OLIVE_FLOW.labels(
            fileName,
            Integer.toString(line),
            Integer.toString(column),
            hash,
            oliveFile,
            Integer.toString(oliveLine),
            Integer.toString(oliveColumn),
            oliveHash);
    final AtomicLong counter = new AtomicLong();
    return input.peek(x -> counter.incrementAndGet()).onClose(() -> child.set(counter.get()));
  }

  public Filter negate(Filter filter) {
    return filter.negate();
  }

  @Override
  public void oliveRuntime(String filename, int line, int column, long timeInNs) {
    OLIVE_RUN_TIME
        .labels(filename, Integer.toString(line), Integer.toString(column))
        .set(timeInNs / Collector.NANOSECONDS_PER_SECOND);
  }

  /** Check that any of the filters match */
  public Filter or(Stream<Filter> filters) {
    return new Filter() {
      private final List<Filter> filterList = filters.collect(Collectors.toList());

      @Override
      protected boolean check(Action action, Information info) {
        return filterList.stream().anyMatch(f -> f.check(action, info));
      }
    };
  }

  public void pause(SourceLocation location) {
    pausedOlives.add(location);
  }

  public void pause(String file) {
    pausedFiles.add(file);
  }

  public Stream<String> pausedFiles() {
    return pausedFiles.stream();
  }

  public Stream<SourceLocation> pauses() {
    return pausedOlives.stream();
  }

  public long purge(Filter... filters) {
    final Set<Action> deadActions =
        startStream(filters)
            .peek(e -> stateCount.labels(e.getValue().lastState.name(), e.getKey().type()).dec())
            .map(Entry::getKey)
            .collect(Collectors.toSet());
    deadActions.forEach(Action::purgeCleanup);
    actions.keySet().removeAll(deadActions);
    return deadActions.size();
  }

  @Override
  public Long read(
      String filename,
      int line,
      int column,
      String hash,
      String oliveFilename,
      int oliveLine,
      int oliveColumn,
      String oliveHash) {
    return (long)
        OLIVE_FLOW
            .labels(
                filename,
                Integer.toString(line),
                Integer.toString(column),
                hash,
                oliveFilename,
                Integer.toString(oliveLine),
                Integer.toString(oliveColumn),
                oliveHash)
            .get();
  }

  private Map<String, String> repack(String[] input, String name) {
    if (input.length % 2 != 0) {
      throw new IllegalArgumentException(name + " must be paired.");
    }
    final Map<String, String> output = new TreeMap<>();
    for (int i = 0; i < input.length; i += 2) {
      if (input[i + 1] != null) {
        output.put(input[i], input[i + 1]);
      }
    }
    return output;
  }

  public void resume(SourceLocation location) {
    pausedOlives.remove(location);
  }

  public void resume(String file) {
    pausedFiles.remove(file);
  }

  public long size(Filter... filters) {
    return startStream(filters).count();
  }

  public Stream<SourceLocation> sources() {
    return sourceLocations.stream();
  }

  /** Begin the action processor */
  public void start(ScheduledExecutorService executor) {
    executor.scheduleWithFixedDelay(this::update, 5, 1, TimeUnit.MINUTES);
    executor.scheduleWithFixedDelay(this::updateAlerts, 5, 5, TimeUnit.MINUTES);
  }

  private Stream<Entry<Action, Information>> startStream(Filter... filters) {
    return actions
        .entrySet()
        .stream()
        .filter(
            entry ->
                Arrays.stream(filters)
                    .allMatch(filter -> filter.check(entry.getKey(), entry.getValue())));
  }

  public ArrayNode stats(ObjectMapper mapper, Filter... filters) {
    final List<Entry<Action, Information>> actions =
        startStream(filters).collect(Collectors.toList());
    final ArrayNode array = mapper.createArrayNode();
    final ObjectNode message = array.addObject();
    message.put("type", "table");
    final ArrayNode table = message.putArray("table");

    final ObjectNode total = table.addObject();
    total.put("title", "Total");
    total.put("value", actions.size());
    total.putNull("kind");

    // This is all written out because there's no convenient type-safe way to put it
    // in a list
    propertySummary(table, ACTION_STATE, actions);
    propertySummary(table, TYPE, actions);
    propertySummary(table, SOURCE_FILE, actions);

    crosstab(array, actions, ACTION_STATE, TYPE);
    crosstab(array, actions, ACTION_STATE, SOURCE_FILE);
    crosstab(array, actions, TYPE, SOURCE_FILE);

    crosstab(array, actions, ACTION_STATE, TAG);
    crosstab(array, actions, SOURCE_FILE, TAG);
    crosstab(array, actions, TYPE, TAG);
    crosstab(array, actions, TAG, TAG);
    histogram(array, 50, actions, INSTANT_BIN, ADDED, CHECKED, STATUS_CHANGED);
    histogram(array, 100, actions, INSTANT_BIN, EXTERNAL);

    histogramByProperty(
        array, 50, actions, ACTION_STATE, INSTANT_BIN, ADDED, CHECKED, STATUS_CHANGED);
    histogramByProperty(array, 100, actions, ACTION_STATE, INSTANT_BIN, EXTERNAL);
    histogramByProperty(
        array, 50, actions, SOURCE_FILE, INSTANT_BIN, ADDED, CHECKED, STATUS_CHANGED);
    histogramByProperty(array, 100, actions, SOURCE_FILE, INSTANT_BIN, EXTERNAL);
    return array;
  }

  /**
   * Check that an action's last status change was in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public Filter statusChanged(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return Optional.of(info.lastStateTransition);
      }
    };
  }

  /**
   * Stream all the actions in the processor matching a filter set
   *
   * @param filters the filters to match
   */
  public Stream<Action> stream(Filter... filters) {
    return startStream(filters).map(Entry::getKey);
  }

  /**
   * Stream all the actions, converted to JSON objects, in the processor matching a filter set
   *
   * @param filters the filters to match
   */
  public Stream<ObjectNode> stream(SourceLocationLinker linker, Filter... filters) {
    return startStream(filters)
        .map(
            entry -> {
              ObjectNode actionNode;
              try {
                actionNode = entry.getKey().toJson(RuntimeSupport.MAPPER);
              } catch (Exception e) {
                e.printStackTrace();
                actionNode = RuntimeSupport.MAPPER.createObjectNode();
              }
              final ObjectNode node = actionNode;
              node.put("actionId", entry.getValue().id);
              node.put("updateInProgress", entry.getValue().updateInProgress);
              node.put("state", entry.getValue().lastState.name());
              node.put("lastAdded", entry.getValue().lastAdded.toEpochMilli());
              node.put("lastChecked", entry.getValue().lastChecked.toEpochMilli());
              node.put("lastStatusChange", entry.getValue().lastStateTransition.toEpochMilli());
              Stream.concat(entry.getValue().tags.stream(), entry.getKey().tags())
                  .forEach(node.putArray("tags")::add);
              entry
                  .getKey()
                  .externalTimestamp()
                  .ifPresent(external -> node.put("external", external.toEpochMilli()));
              final String thrown = entry.getValue().thrown;
              if (thrown != null) {
                if (node.has("errors")) {
                  ((ArrayNode) node.get("errors")).add(thrown);
                } else {
                  node.putArray("errors").add(thrown);
                }
              }
              node.put("type", entry.getKey().type());
              final ArrayNode locations = node.putArray("locations");
              entry
                  .getValue()
                  .locations
                  .stream()
                  .sorted()
                  .forEach(location -> location.toJson(locations, linker));
              final ArrayNode commands = node.putArray("commands");
              entry
                  .getKey()
                  .commands()
                  .forEach(
                      c -> {
                        final ObjectNode command = commands.addObject();
                        command.put("command", c.command());
                        command.put("buttonText", c.buttonText());
                        command.put("icon", c.icon().icon());
                        command.put("showPrompt", c.prefers(Preference.PROMPT));
                        command.put("allowBulk", c.prefers(Preference.ALLOW_BULK));
                      });
              return node;
            });
  }

  @Override
  public Filter tag(Pattern pattern) {
    return new Filter() {
      private final Predicate<String> predicate = pattern.asPredicate();

      @Override
      protected boolean check(Action action, Information info) {
        return Stream.concat(info.tags.stream(), action.tags()).anyMatch(predicate);
      }
    };
  }

  /**
   * Check that an action has one of the listed tags attached
   *
   * @param tags the set of tags
   */
  public Filter tags(Stream<String> tags) {
    final Set<String> tagSet = tags.collect(Collectors.toSet());
    return new Filter() {
      @Override
      protected boolean check(Action action, Information info) {
        return Stream.concat(info.tags.stream(), action.tags()).anyMatch(tagSet::contains);
      }
    };
  }

  public Stream<String> tags(Filter... filters) {
    return startStream(filters)
        .flatMap(entry -> Stream.concat(entry.getValue().tags.stream(), entry.getKey().tags()));
  }

  /**
   * Check that an action matches the regular expression provided
   *
   * @param pattern the pattern
   */
  public Filter textSearch(Pattern pattern) {
    return new Filter() {
      @Override
      protected boolean check(Action action, Information info) {
        return action.search(pattern);
      }
    };
  }

  /** Check that an action has one of the types specified */
  public Filter type(String... types) {
    final Set<String> set = Stream.of(types).collect(Collectors.toSet());
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return set.contains(action.type());
      }
    };
  }

  private void update() {

    final Instant now = Instant.now();
    final List<Entry<Action, Information>> candidates =
        actions
            .entrySet()
            .stream()
            .sorted(Comparator.comparingInt(e -> e.getKey().priority()))
            .filter(
                entry ->
                    entry.getValue().lastState != ActionState.SUCCEEDED
                        && entry.getValue().lastState != ActionState.ZOMBIE
                        && !entry.getValue().updateInProgress
                        && Duration.between(entry.getValue().lastChecked, now).toMinutes()
                            >= Math.max(10, entry.getKey().retryMinutes()))
            .limit(1000 * ACTION_PERFORM_THREADS - currentRunningActions.get())
            .collect(Collectors.toList());
    currentRunningActionsGauge.set(currentRunningActions.addAndGet(candidates.size()));

    for (final Entry<Action, Information> entry : candidates) {
      entry.getValue().updateInProgress = true;
      final String location =
          entry
              .getValue()
              .locations
              .stream()
              .map(Object::toString)
              .sorted()
              .collect(Collectors.joining(" "));
      final Runnable queuedInflight =
          Server.inflight(
              String.format(
                  "Waiting to perform %s action %s from %s",
                  entry.getKey().type(), entry.getValue().id, location));
      final CompletableFuture<Boolean> timeoutFuture = new CompletableFuture<>();
      final CompletableFuture<Boolean> workFuture =
          CompletableFuture.supplyAsync(
              () -> {
                // We wait to schedule the timeout for when the action is actually
                // starting
                final Duration timeout = entry.getKey().performTimeout().abs();
                timeoutExecutor.schedule(
                    () -> timeoutFuture.complete(true),
                    Math.max(timeout.getSeconds(), 60),
                    TimeUnit.SECONDS);
                entry.getValue().lastChecked = Instant.now();
                final ActionState oldState = entry.getValue().lastState;
                final boolean oldThrown = entry.getValue().thrown != null;
                queuedInflight.run();
                try (AutoCloseable timer = actionPerformTime.start(entry.getKey().type());
                    AutoCloseable inflight =
                        Server.inflightCloseable(
                            String.format(
                                "Performing %s action %s from %s",
                                entry.getKey().type(), entry.getValue().id, location))) {
                  entry.getValue().lastState =
                      entry
                              .getValue()
                              .locations
                              .stream()
                              .anyMatch(
                                  l ->
                                      pausedOlives.contains(l)
                                          || pausedFiles.contains(l.fileName()))
                          ? ActionState.THROTTLED
                          : entry.getKey().perform(actionServices);
                  entry.getValue().thrown = null;
                } catch (final Throwable e) {
                  entry.getValue().lastState = ActionState.UNKNOWN;
                  entry.getValue().thrown = "Exception thrown during evaluation: " + e;
                  e.printStackTrace();
                  if (e instanceof Error) {
                    throw (Error) e;
                  }
                }
                if (oldState != entry.getValue().lastState) {
                  entry.getValue().lastStateTransition = Instant.now();
                  stateCount.labels(oldState.name(), entry.getKey().type()).dec();
                  stateCount.labels(entry.getValue().lastState.name(), entry.getKey().type()).inc();
                }
                actionThrows.inc((entry.getValue().thrown != null ? 0 : 1) - (oldThrown ? 0 : 1));
                entry.getValue().updateInProgress = false;
                currentRunningActionsGauge.set(currentRunningActions.decrementAndGet());
                return false;
              },
              workExecutor);
      CompletableFuture.anyOf(timeoutFuture, workFuture)
          .thenAcceptAsync(
              o -> {
                if (((Boolean) o)) workFuture.cancel(true);
              },
              timeoutExecutor);
    }
    scheduledInRound.set(candidates.size());
    lastRun.setToCurrentTime();
    final Map<Pair<ActionState, String>, Optional<Instant>> lastTransitions =
        actions
            .entrySet()
            .stream()
            .collect(
                Collectors.groupingBy(
                    (Entry<Action, Information> e) ->
                        new Pair<>(e.getValue().lastState, e.getKey().type()),
                    Collectors.mapping(
                        (Entry<Action, Information> e) -> e.getValue().lastStateTransition,
                        Collectors.maxBy(Comparator.naturalOrder()))));
    for (final String actionType : knownActionTypes) {
      for (final ActionState state : ActionState.values()) {
        final Instant time =
            lastTransitions
                .getOrDefault(new Pair<>(state, actionType), Optional.empty())
                .orElse(null);
        if (time == null) {
          oldest.remove(state.name(), actionType);
        } else {
          oldest.labels(state.name(), actionType).set(time.getEpochSecond());
        }
      }
    }
  }

  private void updateAlerts() {
    try (AutoCloseable lock = alertLock.acquire();
        AutoCloseable inflight = Server.inflightCloseable("Push alerts")) {
      currentAlerts =
          RuntimeSupport.MAPPER.writeValueAsString(
              alerts.values().stream().filter(Alert::isLive).collect(Collectors.toList()));
    } catch (final Exception e) {
      e.printStackTrace();
    }
    if (currentAlerts != null) {
      manager.pushAlerts(currentAlerts);
    }
  }
}
