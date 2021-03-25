package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangElement;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.plugin.cache.InvalidatableRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.LabelledKeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import ca.on.oicr.gsi.shesmu.plugin.input.JsonInputSource;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackStreaming;
import ca.on.oicr.gsi.shesmu.plugin.json.UnpackJson;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.InputSource;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.gsi.status.TableRowWriter;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Define a <tt>Input</tt> format for olives to consume */
public final class AnnotatedInputFormatDefinition implements InputFormatDefinition, InputSource {

  public interface JsonFieldWriter {
    void write(JsonGenerator generator, Object value);
  }

  private static class AnnotatedInputVariable implements InputVariable {

    private final Flavour flavour;
    private final InputFormat format;
    private final MethodType methodType;
    private final String name;
    private final Imyhat type;
    private final TimeFormat timeFormat;

    public AnnotatedInputVariable(
        String name,
        MethodType methodType,
        InputFormat format,
        Flavour flavour,
        Imyhat type,
        TimeFormat timeFormat) {
      this.name = name;
      this.methodType = methodType;
      this.format = format;
      this.flavour = flavour;
      this.type = type;
      this.timeFormat = timeFormat;
    }

    @Override
    public void extract(GeneratorAdapter method) {
      method.invokeDynamic(
          name, methodType.toMethodDescriptorString(), BSM_INPUT_VARIABLE, format.name());
    }

    @Override
    public Flavour flavour() {
      return flavour;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public void read() {
      // Exciting! Don't care.
    }

    public Object read(ObjectNode node) {
      return type.apply(new UnpackJson(node.get(name), timeFormat));
    }

    @Override
    public Imyhat type() {
      return type;
    }
  }

  public static final class Configuration {
    private int ttl;
    private String url;

    public int getTtl() {
      return ttl;
    }

    public String getUrl() {
      return url;
    }

    public void setTtl(int ttl) {
      this.ttl = ttl;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }

  private class DynamicInputDataSourceFromJsonStream implements DynamicInputDataSource {
    private final LabelledKeyValueCache<Object, String, Stream<Object>, Stream<Object>> cache;

    public DynamicInputDataSourceFromJsonStream(
        String name, int ttl, DynamicInputJsonSource source) {
      cache =
          new LabelledKeyValueCache<>(name() + " " + name, ttl, ReplacingRecord::new) {
            @Override
            protected Stream<Object> fetch(Object key, String label, Instant lastUpdated)
                throws Exception {
              try (final var input = source.fetch(key);
                  final var parser = RuntimeSupport.MAPPER.getFactory().createParser(input)) {
                final List<Object> results = new ArrayList<>();
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                  throw new IllegalStateException("Expected an array");
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                  results.add(readJson(RuntimeSupport.MAPPER.readTree(parser)));
                }
                if (parser.nextToken() != null) {
                  throw new IllegalStateException("Junk at end of JSON document");
                }
                return results.stream();
              }
            }

            @Override
            protected String label(Object key) {
              return ((PluginFile) key).fileName().toString();
            }
          };
    }

    @Override
    public Stream<Object> fetch(Object instance, boolean readStale) {
      try {
        return readStale ? cache.getStale(instance) : cache.get(instance);
      } catch (InitialCachePopulationException e) {
        e.printStackTrace();
        return Stream.empty();
      }
    }
  }

  private class InputDataSourceFromJsonStream implements InputDataSource {
    private final ValueCache<Stream<Object>, Stream<Object>> cache;

    public InputDataSourceFromJsonStream(String name, int ttl, JsonInputSource source) {
      cache =
          new ValueCache<>(name() + " " + name, ttl, ReplacingRecord::new) {
            @Override
            protected Stream<Object> fetch(Instant lastUpdated) throws Exception {
              source.ttl().ifPresent(cache::ttl);
              try (final var input = source.fetch();
                  final var parser = RuntimeSupport.MAPPER.getFactory().createParser(input)) {
                final List<Object> results = new ArrayList<>();
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                  throw new IllegalStateException("Expected an array");
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                  results.add(readJson(RuntimeSupport.MAPPER.readTree(parser)));
                }
                if (parser.nextToken() != null) {
                  throw new IllegalStateException("Junk at end of JSON document");
                }
                return results.stream();
              }
            }
          };
    }

    @Override
    public Stream<Object> fetch(boolean readStale) {
      try {
        return readStale ? cache.getStale() : cache.get();
      } catch (InitialCachePopulationException e) {
        e.printStackTrace();
        return Stream.empty();
      }
    }
  }

  private class LocalJsonFile implements WatchedFileListener {
    private final ConfigurationSection configuration;
    private volatile boolean dirty = true;
    private final String fileName;
    private final ValueCache<Optional<List<Object>>, Optional<List<Object>>> values;

    public LocalJsonFile(Path fileName) {
      this.fileName = fileName.toString();
      values =
          new ValueCache<>(
              format.name() + " " + fileName,
              Integer.MAX_VALUE,
              InvalidatableRecord.checking(l -> dirty, x -> {})) {
            @Override
            protected Optional<List<Object>> fetch(Instant lastUpdated) throws Exception {
              dirty = false;
              try {
                List<Object> result =
                    Stream.of(
                            RuntimeSupport.MAPPER.readValue(fileName.toFile(), ObjectNode[].class))
                        .map(AnnotatedInputFormatDefinition.this::readJson)
                        .collect(Collectors.toList());
                JsonPluginFile.GOOD_JSON.labels(fileName.toString()).set(1);
                return Optional.of(result);
              } catch (Exception e) {
                JsonPluginFile.GOOD_JSON.labels(fileName.toString()).set(0);
                throw e;
              }
            }
          };
      configuration =
          new ConfigurationSection(fileName.toString()) {
            @Override
            public void emit(SectionRenderer sectionRenderer) {
              try {
                sectionRenderer.line(
                    "Count", values.get().map(l -> Integer.toString(l.size())).orElse("Invalid"));
              } catch (InitialCachePopulationException e) {
                sectionRenderer.line("Count", "Invalid");
              }
            }
          };
    }

    public ConfigurationSection configuration() {
      return configuration;
    }

    public String fileName() {
      return fileName;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public Optional<Integer> update() {
      dirty = true;
      return Optional.empty();
    }

    public Stream<Object> variables() {
      return values.get().stream().flatMap(Collection::stream);
    }
  }

  private class RemoteJsonSource implements WatchedFileListener {
    private class RemoteReloader extends ValueCache<Stream<Object>, Stream<Object>> {
      public RemoteReloader(Path fileName) {
        super("remotejson " + format.name() + " " + fileName.toString(), 10, ReplacingRecord::new);
      }

      @Override
      protected Stream<Object> fetch(Instant lastUpdated) throws Exception {
        if (config.isEmpty()) return Stream.empty();
        final var url = config.get().getUrl();
        var response =
            Server.HTTP_CLIENT.send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                BodyHandlers.ofInputStream());
        try (var parser = RuntimeSupport.MAPPER.getFactory().createParser(response.body())) {
          final List<Object> results = new ArrayList<>();
          if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw new IllegalStateException("Expected an array");
          }
          while (parser.nextToken() != JsonToken.END_ARRAY) {
            results.add(readJson(RuntimeSupport.MAPPER.readTree(parser)));
          }
          if (parser.nextToken() != null) {
            throw new IllegalStateException("Junk at end of JSON document");
          }
          return results.stream();
        }
      }
    }

    private final ValueCache<Stream<Object>, Stream<Object>> cache;
    private Optional<Configuration> config = Optional.empty();
    private final Path fileName;

    public RemoteJsonSource(Path fileName) {
      this.fileName = fileName;
      cache = new RemoteReloader(fileName);
    }

    public ConfigurationSection configuration() {
      return new ConfigurationSection(fileName.toString()) {
        @Override
        public void emit(SectionRenderer sectionRenderer) {
          sectionRenderer.line("Input Format", format.name());
          config.ifPresent(
              c -> {
                sectionRenderer.line("URL", c.url);
                sectionRenderer.line("Time-to-live", c.ttl);
              });
        }
      };
    }

    @Override
    public void start() {
      // Do nothing
    }

    @Override
    public void stop() {
      // Do nothing
    }

    @Override
    public Optional<Integer> update() {
      try {
        config =
            Optional.of(RuntimeSupport.MAPPER.readValue(fileName.toFile(), Configuration.class));
        cache.invalidate();
        cache.ttl(config.get().getTtl());
      } catch (IOException e) {
        e.printStackTrace();
      }
      return Optional.empty();
    }

    public Stream<Object> variables(boolean readStale) {
      return readStale ? cache.getStale() : cache.get();
    }
  }

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Handle BSM_INPUT_VARIABLE =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(RuntimeSupport.class).getInternalName(),
          "inputBootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String.class)),
          false);
  private static final List<AnnotatedInputFormatDefinition> FORMATS = new ArrayList<>();
  public static final JarHashRepository<InputFormat> INPUT_FORMAT_HASHES =
      new JarHashRepository<>();
  private static final Map<Pair<String, String>, CallSite> INPUT_VARIABLES_REGISTRY =
      new ConcurrentHashMap<>();
  private static final Lookup LOOKUP = MethodHandles.publicLookup();
  private static final MethodHandle MH_IMYHAT__ACCEPT;
  private static final MethodHandle MH_PACK_STREAMING__CTOR;
  private static final MethodHandle MH_TUPLE_GET;
  private static final MethodHandle MH_TUPLE_IS_INSTANCE;

  static {
    MethodHandle mh_tuple_get = null;
    MethodHandle mh_tuple_is_instance = null;
    MethodHandle mh_pack_streaming__ctor = null;
    MethodHandle mh_imyhat__accept = null;
    try {
      mh_tuple_get =
          LOOKUP.findVirtual(Tuple.class, "get", MethodType.methodType(Object.class, int.class));
      mh_tuple_is_instance =
          LOOKUP
              .findVirtual(
                  Class.class, "isInstance", MethodType.methodType(boolean.class, Object.class))
              .bindTo(Tuple.class);
      mh_pack_streaming__ctor =
          LOOKUP
              .findConstructor(
                  PackStreaming.class,
                  MethodType.methodType(void.class, JsonGenerator.class, TimeFormat.class))
              .asType(
                  MethodType.methodType(
                      ImyhatConsumer.class, JsonGenerator.class, TimeFormat.class));
      mh_imyhat__accept =
          LOOKUP.findVirtual(
              Imyhat.class,
              "accept",
              MethodType.methodType(void.class, ImyhatConsumer.class, Object.class));
    } catch (IllegalAccessException | NoSuchMethodException e) {
      e.printStackTrace();
    }
    MH_TUPLE_GET = mh_tuple_get;
    MH_TUPLE_IS_INSTANCE = mh_tuple_is_instance;
    MH_PACK_STREAMING__CTOR = mh_pack_streaming__ctor;
    MH_IMYHAT__ACCEPT = mh_imyhat__accept;
    for (final var format : ServiceLoader.load(InputFormat.class)) {
      try {
        INPUT_FORMAT_HASHES.add(format);
        FORMATS.add(new AnnotatedInputFormatDefinition(format));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static CallSite bootstrap(
      Lookup lookup, String variableName, MethodType methodType, String inputFormatName) {
    return INPUT_VARIABLES_REGISTRY.get(new Pair<>(inputFormatName, variableName));
  }

  public static Stream<AnnotatedInputFormatDefinition> formats() {
    return FORMATS.stream();
  }

  private final List<Pair<String, JsonFieldWriter>> fieldWriters = new ArrayList<>();
  private final InputFormat format;
  private final List<GangDefinition> gangs;
  private final AutoUpdatingDirectory<LocalJsonFile> local;
  private final AutoUpdatingDirectory<RemoteJsonSource> remotes;
  private final List<AnnotatedInputVariable> variables = new ArrayList<>();

  public AnnotatedInputFormatDefinition(InputFormat format) throws IllegalAccessException {
    this.format = format;
    // Get all the candidate methods in this class and sort them alphabetically
    final SortedMap<String, Pair<ShesmuVariable, Method>> sortedMethods = new TreeMap<>();
    for (final var method : format.type().getMethods()) {
      final var info = method.getAnnotation(ShesmuVariable.class);
      if (info == null) {
        continue;
      }
      sortedMethods.put(method.getName(), new Pair<>(info, method));
    }
    final Map<String, List<Pair<Integer, GangElement>>> gangs = new HashMap<>();
    for (final var entry : sortedMethods.values()) {
      // Create a target for each method for the compiler to use
      final var name = entry.second().getName();
      final var type =
          Imyhat.convert(
              String.format(
                  "Return type of %s in %s", name, entry.second().getDeclaringClass().getName()),
              entry.first().type(),
              entry.second().getGenericReturnType());
      final var flavour = entry.first().signable() ? Flavour.STREAM_SIGNABLE : Flavour.STREAM;
      final var methodType = MethodType.methodType(type.javaType(), Object.class);
      // Register this variable with the compiler
      final var variable =
          new AnnotatedInputVariable(
              name, methodType, format, flavour, type, entry.first().timeFormat());
      variables.add(variable);
      // Now we need to make a call site for this variable. It will happen in two
      // cases: either we have an instance of the real type and we should call the
      // method on it, or we have a Tuple that was generated generically by one of our
      // JSON readers
      final var getter = LOOKUP.unreflect(entry.second()).asType(methodType);
      final var handle =
          MethodHandles.guardWithTest(
              MH_TUPLE_IS_INSTANCE,
              MethodHandles.insertArguments(MH_TUPLE_GET, 1, variables.size() - 1)
                  .asType(methodType),
              getter);
      INPUT_VARIABLES_REGISTRY.put(new Pair<>(format.name(), name), new ConstantCallSite(handle));
      // Now, we need a thing to write a field using the streaming Jackson interface,
      // so we take the type and combine it with the Imyhat.apply method
      fieldWriters.add(
          new Pair<>(
              name,
              MethodHandleProxies.asInterfaceInstance(
                  JsonFieldWriter.class,
                  MethodHandles.collectArguments(
                      MethodHandles.collectArguments(
                          MH_IMYHAT__ACCEPT.bindTo(type),
                          0,
                          MethodHandles.insertArguments(
                              MH_PACK_STREAMING__CTOR, 1, entry.first().timeFormat())),
                      1,
                      handle.asType(MethodType.methodType(Object.class, Object.class))))));

      // Now, prepare any gangs
      for (final var gang : entry.first().gangs()) {
        gangs
            .computeIfAbsent(gang.name(), k -> new ArrayList<>())
            .add(
                new Pair<>(
                    gang.order(),
                    new GangElement(variable.name(), variable.type(), gang.dropIfDefault())));
      }
    }
    local = new AutoUpdatingDirectory<>("." + format.name() + "-input", LocalJsonFile::new);
    remotes = new AutoUpdatingDirectory<>("." + format.name() + "-remote", RemoteJsonSource::new);
    this.gangs =
        gangs.entrySet().stream()
            .map(
                e ->
                    new GangDefinition() {
                      final List<GangElement> elements =
                          e.getValue().stream()
                              .sorted(
                                  Comparator.<Pair<Integer, GangElement>, Integer>comparing(
                                          Pair::first)
                                      .thenComparing(x -> x.second().name()))
                              .map(Pair::second)
                              .collect(Collectors.toList());
                      final String name = e.getKey();

                      @Override
                      public Stream<GangElement> elements() {
                        return elements.stream();
                      }

                      @Override
                      public String name() {
                        return name;
                      }
                    })
            .collect(Collectors.toList());
  }

  /** Get all the variables available for this format */
  @Override
  public Stream<InputVariable> baseStreamVariables() {
    return variables.stream().map(x -> x);
  }

  public Stream<? extends ConfigurationSection> configuration() {
    return Stream.concat(
        local.stream().map(LocalJsonFile::configuration),
        remotes.stream().map(RemoteJsonSource::configuration));
  }

  public void dumpPluginConfig(TableRowWriter row) {
    local.stream().forEach(l -> row.write(false, l.fileName(), "Input Source", format.name()));
    remotes.stream()
        .forEach(r -> row.write(false, r.fileName.toString(), "Input Source", format.name()));
  }

  @Override
  public Stream<Object> fetch(String name, boolean readStale) {
    if (name.equals(format.name())) {
      return variables(readStale);
    } else {
      return Stream.empty();
    }
  }

  public DynamicInputDataSource fromJsonStream(
      String name, int ttl, DynamicInputJsonSource source) {
    return new DynamicInputDataSourceFromJsonStream(name, ttl, source);
  }

  public InputDataSource fromJsonStream(String name, int ttl, JsonInputSource source) {
    return new InputDataSourceFromJsonStream(name, ttl, source);
  }

  @Override
  public Stream<? extends GangDefinition> gangs() {
    return gangs.stream();
  }

  public boolean isAssignableFrom(Class<?> dataType) {
    return format.type().isAssignableFrom(dataType);
  }

  /** The name of this format, which must be a valid Shesmu identifier */
  @Override
  public String name() {
    return format.name();
  }

  private Tuple readJson(ObjectNode node) {
    final var values = new Object[variables.size()];
    for (var i = 0; i < values.length; i++) {
      values[i] = variables.get(i).read(node);
    }
    return new Tuple(values);
  }

  @Override
  public Type type() {
    return A_OBJECT_TYPE;
  }

  private Stream<Object> variables(boolean readStale) {
    return Stream.concat(
        local.stream().flatMap(LocalJsonFile::variables),
        remotes.stream().flatMap(source -> source.variables(readStale)));
  }

  public void writeJson(JsonGenerator generator, InputSource inputProvider, boolean readStale)
      throws IOException {
    generator.writeStartArray();
    inputProvider
        .fetch(format.name(), readStale)
        .forEach(
            value -> {
              try {
                generator.writeStartObject();
                for (var fieldWriter : fieldWriters) {
                  generator.writeFieldName(fieldWriter.first());
                  fieldWriter.second().write(generator, value);
                }
                generator.writeEndObject();
              } catch (IOException e) {
                throw new RuntimeException(e);
              }
            });
    generator.writeEndArray();
  }
}
