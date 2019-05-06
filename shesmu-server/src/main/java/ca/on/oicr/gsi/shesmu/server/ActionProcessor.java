package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.core.input.shesmu.ShesmuIntrospectionValue;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.SourceLocation.SourceLoctionLinker;
import ca.on.oicr.gsi.shesmu.server.plugins.PluginManager;
import ca.on.oicr.gsi.shesmu.util.AutoLock;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.client.utils.URIBuilder;

/**
 * Background process for launching actions and reporting the results
 *
 * <p>This class collects actions and tries to {@link Action#perform(ActionServices)} until
 * successful.
 */
public final class ActionProcessor implements OliveServices, InputProvider {

  private interface Bin<T> extends Comparator<T> {
    long bucket(T min, long width, T value);

    Optional<T> extract(Entry<Action, Information> input);

    default Stream<T> flatExtract(Entry<Action, Information> input) {
      return extract(input).map(Stream::of).orElseGet(Stream::empty);
    }

    long minWidth();

    String name();

    JsonNode name(T min, long offset);

    long span(T min, T max);
  }

  private interface Property<T> {
    Stream<T> extract(Entry<Action, Information> input);

    JsonNode json(T input);

    String name();

    String name(T input);
  }

  public static final class Alert {
    private Map<String, String> annotations = new TreeMap<>();
    private String endsAt;
    @JsonIgnore private Instant expiryTime;
    private String generatorURL;
    private final String id = Long.toString(alertIdGenerator.getAndIncrement());
    private Map<String, String> labels = new TreeMap<>();
    private String startsAt;

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
    Instant lastAdded = Instant.now();
    Instant lastChecked = Instant.EPOCH;
    ActionState lastState = ActionState.UNKNOWN;
    Instant lastStateTransition = Instant.now();
    final Set<SourceLocation> locations = new HashSet<>();
    boolean thrown;
  }

  private abstract static class InstantBin implements Bin<Instant> {
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
                  start.map(s -> s.compareTo(time) < 1).orElse(true)
                      && end.map(e -> e.isAfter(time)).orElse(true))
          .orElse(false);
    }

    protected abstract Optional<Instant> get(Action action, Information info);
  }

  /**
   * Check that an action was last added in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public static Filter added(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return Optional.of(info.lastAdded);
      }
    };
  }

  private static <T> void binSummary(
      ArrayNode table, Bin<T> bin, List<Entry<Action, Information>> actions) {
    Stream.<Pair<String, BiFunction<Stream<T>, Comparator<T>, Optional<T>>>>of(
            new Pair<>("Minimum", Stream::min), new Pair<>("Minimum", Stream::max))
        .forEach(
            pair -> {
              pair.second()
                  .apply(actions.stream().flatMap(bin::flatExtract), bin)
                  .ifPresent(
                      value -> {
                        final ObjectNode row = table.addObject();
                        row.put("title", pair.first());
                        row.set("value", bin.name(value, 0));
                        row.put("kind", "bin");
                        row.put("type", bin.name());
                      });
            });
  }

  /**
   * Check that an action was last checked in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public static Filter checked(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return Optional.of(info.lastChecked);
      }
    };
  }

  /**
   * Check that an action's external timestamp is in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public static Filter external(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return action.externalTimestamp();
      }
    };
  }

  /**
   * Checks that an action was generated in a particular source location
   *
   * @param locations the source locations
   */
  public static Filter fromFile(Stream<Predicate<SourceLocation>> locations) {
    final List<Predicate<SourceLocation>> list = locations.collect(Collectors.toList());
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return list.stream().anyMatch(l -> info.locations.stream().anyMatch(l));
      }
    };
  }

  /**
   * Checks that an action was generated in a particular file
   *
   * @param files the names of the files
   */
  public static Filter fromFile(String... files) {
    final Set<String> set = Stream.of(files).collect(Collectors.toSet());
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return info.locations.stream().map(SourceLocation::fileName).anyMatch(set::contains);
      }
    };
  }

  /**
   * Checks that an action is in one of the specified actions states
   *
   * @param states the permitted states
   */
  public static Filter isState(ActionState... states) {
    final EnumSet<ActionState> set = EnumSet.noneOf(ActionState.class);
    set.addAll(Arrays.asList(states));
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return set.contains(info.lastState);
      }
    };
  }

  private static <T extends Comparable<T>> void propertySummary(
      ArrayNode table, Property<T> property, List<Entry<Action, Information>> actions) {
    final TreeMap<T, Long> states =
        actions
            .stream()
            .flatMap(property::extract)
            .collect(
                Collectors.groupingBy(Function.identity(), TreeMap::new, Collectors.counting()));
    for (final Entry<T, Long> state : states.entrySet()) {
      final ObjectNode row = table.addObject();
      row.put("title", "Total");
      row.put("value", state.getValue());
      row.put("kind", "property");
      row.put("type", property.name());
      row.put("property", property.name(state.getKey()));
      row.set("json", property.json(state.getKey()));
    }
  }

  /**
   * Check that an action's last status change was in the time range provided
   *
   * @param start the exclusive cut-off timestamp
   * @param end the exclusive cut-off timestamp
   */
  public static Filter statusChanged(Optional<Instant> start, Optional<Instant> end) {
    return new InstantFilter(start, end) {

      @Override
      protected Optional<Instant> get(Action action, Information info) {
        return Optional.of(info.lastStateTransition);
      }
    };
  }

  /**
   * Check that an action matches the regular expression provided
   *
   * @param pattern the pattern
   */
  public static Filter textSearch(Pattern pattern) {
    return new Filter() {
      @Override
      protected boolean check(Action action, Information info) {
        return action.search(pattern);
      }
    };
  }

  /** Check that an action has one of the types specified */
  public static Filter type(String... types) {
    final Set<String> set = Stream.of(types).collect(Collectors.toSet());
    return new Filter() {

      @Override
      protected boolean check(Action action, Information info) {
        return set.contains(action.type());
      }
    };
  }

  private static final Bin<Instant> ADDED =
      new InstantBin() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return Optional.of(input.getValue().lastAdded);
        }

        @Override
        public String name() {
          return "added";
        }
      };
  private static final Bin<Instant> CHECKED =
      new InstantBin() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return Optional.of(input.getValue().lastChecked);
        }

        @Override
        public String name() {
          return "checked";
        }
      };
  private static final Bin<Instant> EXTERNAL =
      new InstantBin() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return input.getKey().externalTimestamp();
        }

        @Override
        public String name() {
          return "external";
        }
      };
  private static final JsonNodeFactory JSON_FACTORY = JsonNodeFactory.withExactBigDecimals(false);
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
        public String name(ActionState input) {
          return input.name();
        }
      };
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
        public String name(String input) {
          return input;
        }
      };
  private static final Property<SourceLocation> SOURCE_LOCATION =
      new Property<SourceLocation>() {

        @Override
        public Stream<SourceLocation> extract(Entry<Action, Information> input) {
          return input.getValue().locations.stream();
        }

        @Override
        public JsonNode json(SourceLocation input) {
          final ObjectNode node = JSON_FACTORY.objectNode();
          node.put("file", input.fileName());
          node.put("line", input.line());
          node.put("column", input.column());
          node.put("time", input.time().toEpochMilli());

          return node;
        }

        @Override
        public String name() {
          return "sourcelocation";
        }

        @Override
        public String name(SourceLocation input) {
          return String.format(
              "%s:%d:%d[%s]",
              input.fileName(),
              input.line(),
              input.column(),
              DateTimeFormatter.ISO_INSTANT.format(input.time()));
        }
      };
  private static final Bin<Instant> STATUS_CHANGED =
      new InstantBin() {

        @Override
        public Optional<Instant> extract(Entry<Action, Information> input) {
          return Optional.of(input.getValue().lastStateTransition);
        }

        @Override
        public String name() {
          return "statuschanged";
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
        public String name(String input) {
          return input;
        }
      };
  private static final Gauge actionThrows =
      Gauge.build(
              "shesmu_action_perform_throw",
              "The number of actions that threw an exception in their last attempt.")
          .register();
  private static final AtomicLong alertIdGenerator = new AtomicLong();
  private static final Gauge lastAdd =
      Gauge.build("shesmu_action_add_last_time", "The last time an actions was added.").register();
  private static final Gauge lastRun =
      Gauge.build("shesmu_action_perform_last_time", "The last time the actions were processed.")
          .register();
  private static final Gauge oldest =
      Gauge.build("shesmu_action_oldest_time", "The oldest action in a particular state.")
          .labelNames("state")
          .register();
  private static final Gauge stateCount =
      Gauge.build("shesmu_action_state_count", "The number of actions in a particular state.")
          .labelNames("state")
          .register();
  private final ActionServices actionServices;
  private final Map<Action, Information> actions = new ConcurrentHashMap<>();
  private final AutoLock alertLock = new AutoLock();
  private final Map<Map<String, String>, Alert> alerts = new HashMap<>();
  private final String baseUri;
  private String currentAlerts = "[]";
  private final PluginManager manager;
  private final Set<SourceLocation> pausedOlives = ConcurrentHashMap.newKeySet();
  private final Set<SourceLocation> sourceLocations = ConcurrentHashMap.newKeySet();

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
      Action action, String filename, int line, int column, long time) {
    Information information;
    boolean isDuplicate;
    if (!actions.containsKey(action)) {
      action.accepted();
      information = new Information();
      actions.put(action, information);
      stateCount.labels(ActionState.UNKNOWN.name()).inc();
      isDuplicate = false;
    } else {
      information = actions.get(action);
      information.lastAdded = Instant.now();
      isDuplicate = true;
    }
    final SourceLocation location =
        new SourceLocation(filename, line, column, Instant.ofEpochMilli(time));
    information.locations.add(location);
    sourceLocations.add(location);
    lastAdd.setToCurrentTime();
    return isDuplicate;
  }

  @Override
  public boolean accept(String[] labels, String[] annotations, long ttl) throws Exception {
    final Map<String, String> labelMap = repack(labels, "Labels");
    try (AutoCloseable lock = alertLock.acquire()) {
      Alert alert;
      final boolean duplicate = alerts.containsKey(labelMap);
      if (duplicate) {
        alert = alerts.get(labelMap);
      } else {
        alert = new Alert();
        alert.setLabels(labelMap);
        alert.setStartsAt(Instant.now());
        alerts.put(labelMap, alert);
        final URIBuilder builder = new URIBuilder(baseUri);
        builder.setFragment("alert-" + alert.id());
        alert.setGeneratorURL(builder.build().toASCIIString());
      }
      alert.setAnnotations(repack(annotations, "Annotations"));
      alert.expiresIn(ttl);
    }
    return false;
  }

  public void alerts(Consumer<Alert> consumer, Runnable noAlerts) {
    try (AutoCloseable lock = alertLock.acquire()) {
      if (alerts.isEmpty()) {
        noAlerts.run();
      } else {
        alerts.values().stream().forEach(consumer);
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }
  }

  private <T extends Comparable<T>, U extends Comparable<U>> void crosstab(
      ArrayNode output,
      List<Entry<Action, Information>> input,
      Property<T> row,
      Property<U> column) {
    final Set<T> rows = input.stream().flatMap(row::extract).collect(Collectors.toSet());
    final Set<U> columns = input.stream().flatMap(column::extract).collect(Collectors.toSet());
    if (rows.size() < 2 && columns.size() < 2) {
      return;
    }
    final ObjectNode node = output.addObject();
    node.put("type", "crosstab");
    node.put("column", column.name());
    node.put("row", row.name());
    final ObjectNode rowsJson = node.putObject("rows");
    final ArrayNode columnsJson = node.putArray("columns");
    columns
        .stream()
        .map(c -> new Pair<>(column.name(c), column.json(c)))
        .sorted(Comparator.comparing(Pair::first))
        .forEach(
            colPair -> {
              final ObjectNode columnJson = columnsJson.addObject();
              columnJson.put("name", colPair.first());
              columnJson.set("value", colPair.second());
            });
    final Map<T, List<Entry<Action, Information>>> map =
        input
            .stream()
            .flatMap(e -> row.extract(e).map(t -> new Pair<>(t, e)))
            .collect(
                Collectors.groupingBy(
                    Pair::first, Collectors.mapping(Pair::second, Collectors.toList())));
    final ObjectNode data = node.putObject("data");
    for (final Entry<T, List<Entry<Action, Information>>> entry : map.entrySet()) {
      final String name = row.name(entry.getKey());
      final ObjectNode inner = data.putObject(name);
      rowsJson.set(name, row.json(entry.getKey()));

      for (final Entry<U, Long> i :
          entry
              .getValue()
              .stream()
              .flatMap(e -> column.extract(e).map(u -> new Pair<>(u, e)))
              .collect(Collectors.groupingBy(Pair::first, Collectors.counting()))
              .entrySet()) {
        inner.put(column.name(i.getKey()), i.getValue());
      }
    }
  }

  public String currentAlerts() {
    return currentAlerts;
  }

  @Override
  public Stream<Object> fetch(String format) {
    return format.equals("shesmu")
        ? actions
            .entrySet()
            .stream()
            .map(
                entry ->
                    new ShesmuIntrospectionValue(
                        entry.getKey(),
                        entry.getValue().lastStateTransition,
                        entry.getValue().lastChecked,
                        entry.getValue().lastAdded,
                        entry.getValue().lastState,
                        entry.getValue().locations))
        : Stream.empty();
  }

  @Override
  public Dumper findDumper(String name, Imyhat... types) {
    return null;
  }

  private <T> void histogram(
      ArrayNode output, int count, List<Entry<Action, Information>> input, Bin<T> bin) {
    final Optional<T> min = input.stream().flatMap(bin::flatExtract).min(bin);
    final Optional<T> max = input.stream().flatMap(bin::flatExtract).max(bin);
    if (!min.isPresent() || !max.isPresent() || min.get().equals(max.get())) {
      return;
    }
    long width = bin.span(min.get(), max.get()) / count;
    final int[] buckets;
    if (width < bin.minWidth()) {
      // If the buckets are less than a minimum width, use buckets of the minimum width over the
      // range
      width = bin.minWidth();
      buckets = new int[(int) Math.ceil(bin.span(min.get(), max.get()) / bin.minWidth()) + 1];
    } else {
      buckets = new int[count];
    }
    if (buckets.length < 2) {
      return;
    }
    final long binWidth = width;
    input
        .stream()
        .flatMap(bin::flatExtract)
        .forEach(
            value -> {
              final int index = (int) bin.bucket(min.get(), binWidth, value);
              buckets[index >= buckets.length ? buckets.length - 1 : index]++;
            });
    final ObjectNode node = output.addObject();
    node.put("type", "histogram");
    node.put("bin", bin.name());
    final ArrayNode boundaries = node.putArray("boundaries");
    final ArrayNode counts = node.putArray("counts");
    for (int i = 0; i < buckets.length; i++) {
      boundaries.add(bin.name(min.get(), i * width));
      counts.add(buckets[i]);
    }
    boundaries.add(bin.name(max.get(), 0));
  }

  @Override
  public boolean isOverloaded(String... services) {
    return false;
  }

  public boolean isPaused(SourceLocation location) {
    return pausedOlives.contains(location);
  }

  public void pause(SourceLocation location) {
    pausedOlives.add(location);
  }

  public Stream<SourceLocation> pauses() {
    return pausedOlives.stream();
  }

  public long purge(Filter... filters) {
    final Set<Action> deadActions =
        startStream(filters)
            .peek(e -> stateCount.labels(e.getValue().lastState.name()).dec())
            .map(Entry::getKey)
            .collect(Collectors.toSet());
    deadActions.forEach(Action::purgeCleanup);
    actions.keySet().removeAll(deadActions);
    return deadActions.size();
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

  public Stream<SourceLocation> sources() {
    return sourceLocations.stream();
  }

  /** Begin the action processor */
  public ScheduledFuture<?> start(ScheduledExecutorService executor) {
    return executor.scheduleWithFixedDelay(this::update, 5, 5, TimeUnit.MINUTES);
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
    propertySummary(table, SOURCE_LOCATION, actions);

    binSummary(table, ADDED, actions);
    binSummary(table, CHECKED, actions);
    binSummary(table, STATUS_CHANGED, actions);
    binSummary(table, EXTERNAL, actions);

    crosstab(array, actions, ACTION_STATE, TYPE);
    crosstab(array, actions, ACTION_STATE, SOURCE_FILE);
    crosstab(array, actions, ACTION_STATE, SOURCE_LOCATION);
    crosstab(array, actions, TYPE, SOURCE_FILE);
    crosstab(array, actions, TYPE, SOURCE_LOCATION);
    histogram(array, 10, actions, ADDED);
    histogram(array, 10, actions, CHECKED);
    histogram(array, 10, actions, STATUS_CHANGED);
    histogram(array, 10, actions, EXTERNAL);
    return array;
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
  public Stream<ObjectNode> stream(
      ObjectMapper mapper, SourceLoctionLinker linker, Filter... filters) {
    return startStream(filters)
        .map(
            entry -> {
              final ObjectNode node = entry.getKey().toJson(mapper);
              node.put("state", entry.getValue().lastState.name());
              node.put("lastAdded", entry.getValue().lastAdded.toEpochMilli());
              node.put("lastChecked", entry.getValue().lastChecked.toEpochMilli());
              node.put("lastStatusChange", entry.getValue().lastStateTransition.toEpochMilli());
              entry
                  .getKey()
                  .externalTimestamp()
                  .ifPresent(external -> node.put("external", external.toEpochMilli()));
              node.put("type", entry.getKey().type());
              final ArrayNode locations = node.putArray("locations");
              entry
                  .getValue()
                  .locations
                  .stream()
                  .sorted()
                  .forEach(location -> location.toJson(locations, linker));
              return node;
            });
  }

  private void update() {
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

    final Instant now = Instant.now();
    actions
        .entrySet()
        .stream()
        .sorted(Comparator.comparingInt(e -> e.getKey().priority()))
        .filter(
            entry ->
                entry.getValue().lastState != ActionState.SUCCEEDED
                    && Duration.between(entry.getValue().lastChecked, now).toMinutes()
                        >= Math.max(5, entry.getKey().retryMinutes()))
        .forEach(
            entry -> {
              entry.getValue().lastChecked = Instant.now();
              final ActionState oldState = entry.getValue().lastState;
              final boolean oldThrown = entry.getValue().thrown;
              try (AutoCloseable inflight =
                  Server.inflightCloseable(
                      String.format(
                          "Performing action %s of type %s",
                          entry.getValue(), entry.getKey().type()))) {
                entry.getValue().lastState =
                    entry.getValue().locations.stream().anyMatch(pausedOlives::contains)
                        ? ActionState.THROTTLED
                        : entry.getKey().perform(actionServices);
              } catch (final Throwable e) {
                entry.getValue().lastState = ActionState.UNKNOWN;
                entry.getValue().thrown = true;
                e.printStackTrace();
                if (e instanceof Error) {
                  throw (Error) e;
                }
              }
              if (oldState != entry.getValue().lastState) {
                entry.getValue().lastStateTransition = Instant.now();
                stateCount.labels(oldState.name()).dec();
                stateCount.labels(entry.getValue().lastState.name()).inc();
              }
              actionThrows.inc((entry.getValue().thrown ? 0 : 1) - (oldThrown ? 0 : 1));
            });
    lastRun.setToCurrentTime();
    final Map<ActionState, List<Information>> lastTransitions =
        actions.values().stream().collect(Collectors.groupingBy((Information i) -> i.lastState));
    for (final ActionState state : ActionState.values()) {
      final long time =
          lastTransitions
              .getOrDefault(state, Collections.emptyList())
              .stream()
              .map(i -> i.lastStateTransition)
              .sorted()
              .findFirst()
              .orElse(Instant.now())
              .getEpochSecond();
      oldest.labels(state.name()).set(time);
    }
  }
}
