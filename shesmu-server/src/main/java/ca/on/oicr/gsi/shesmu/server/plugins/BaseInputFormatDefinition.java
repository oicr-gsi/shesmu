package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangElement;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.plugin.ErrorableStream;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.authentication.AuthenticationConfiguration;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.plugin.cache.InvalidatableRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.LabelledKeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.files.WatchedFileListener;
import ca.on.oicr.gsi.shesmu.plugin.input.JsonInputSource;
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
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Define a <tt>Input</tt> format for olives to consume */
public abstract class BaseInputFormatDefinition implements InputFormatDefinition, InputSource {

  public interface JsonFieldWriter {
    void write(JsonGenerator generator, Object value);
  }

  public static final class Configuration {
    private AuthenticationConfiguration authentication;
    private int ttl;
    private String url;

    public AuthenticationConfiguration getAuthentication() {
      return authentication;
    }

    public int getTtl() {
      return ttl;
    }

    public String getUrl() {
      return url;
    }

    public void setAuthentication(AuthenticationConfiguration authentication) {
      this.authentication = authentication;
    }

    public void setTtl(int ttl) {
      this.ttl = ttl;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }

  private class DynamicInputDataSourceFromJsonStream implements DynamicInputDataSource {
    private final LabelledKeyValueCache<Object, String, Stream<Object>> cache;

    public DynamicInputDataSourceFromJsonStream(
        String name, int ttl, DynamicInputJsonSource source) {
      cache =
          new LabelledKeyValueCache<>(name() + " " + name, ttl, ReplacingRecord::new) {
            @Override
            protected Stream<Object> fetch(Object key, String label, Instant lastUpdated)
                throws Exception {
              try (final InputStream input = source.fetch(key);
                  final JsonParser parser =
                      RuntimeSupport.MAPPER.getFactory().createParser(input)) {
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
        return new ErrorableStream<>(Stream.empty(), false);
      }
    }
  }

  private class InputDataSourceFromJsonStream implements InputDataSource {
    private final ValueCache<Stream<Object>> cache;

    public InputDataSourceFromJsonStream(String name, int ttl, JsonInputSource source) {
      cache =
          new ValueCache<>(name() + " " + name, ttl, ReplacingRecord::new) {
            @Override
            protected Stream<Object> fetch(Instant lastUpdated) throws Exception {
              source.ttl().ifPresent(cache::ttl);
              try (final InputStream input = source.fetch();
                  final JsonParser parser =
                      RuntimeSupport.MAPPER.getFactory().createParser(input)) {
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
        return new ErrorableStream<>(Stream.empty(), false);
      }
    }
  }

  protected record InputVariableDefinition(
      String name,
      MethodType methodType,
      String format,
      Flavour flavour,
      Imyhat type,
      TimeFormat timeFormat,
      MethodHandle handle)
      implements InputVariable {

    @Override
    public void extract(GeneratorAdapter method) {
      method.invokeDynamic(name, methodType.toMethodDescriptorString(), BSM_INPUT_VARIABLE, format);
    }

    @Override
    public void read() {
      // Exciting! Don't care.
    }

    public Object read(ObjectNode node) {
      return type.apply(new UnpackJson(node.get(name), timeFormat));
    }
  }

  private class LocalJsonFile implements WatchedFileListener {
    private final ConfigurationSection configuration;
    private volatile boolean dirty = true;
    private final String fileName;
    private final ValueCache<Optional<List<Object>>> values;

    public LocalJsonFile(Path fileName) {
      this.fileName = fileName.toString();
      values =
          new ValueCache<>(
              name + " " + fileName,
              Integer.MAX_VALUE,
              InvalidatableRecord.checking(l -> dirty, x -> {})) {
            @Override
            protected Optional<List<Object>> fetch(Instant lastUpdated) throws Exception {
              dirty = false;
              try {
                List<Object> result =
                    Stream.of(
                            RuntimeSupport.MAPPER.readValue(fileName.toFile(), ObjectNode[].class))
                        .map(BaseInputFormatDefinition.this::readJson)
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
    private class RemoteReloader extends ValueCache<Stream<Object>> {
      public RemoteReloader(Path fileName) {
        super("remotejson " + name + " " + fileName.toString(), 10, ReplacingRecord::new);
      }

      @Override
      protected Stream<Object> fetch(Instant lastUpdated) throws Exception {
        if (config.isEmpty()) return new ErrorableStream<>(Stream.empty(), false);
        final String url = config.get().getUrl();
        final HttpRequest.Builder request =
            HttpRequest.newBuilder(URI.create(url)).GET().version(Version.HTTP_1_1);
        AuthenticationConfiguration.addAuthenticationHeader(
            config.get().getAuthentication(), request);
        HttpResponse<InputStream> response =
            Server.HTTP_CLIENT.send(request.build(), BodyHandlers.ofInputStream());
        try (JsonParser parser = RuntimeSupport.MAPPER.getFactory().createParser(response.body())) {
          if (response.statusCode() != 200) return new ErrorableStream<>(Stream.empty(), false);
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

    private final ValueCache<Stream<Object>> cache;
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
          sectionRenderer.line("Input Format", name);
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
      try {
        return readStale ? cache.getStale() : cache.get();
      } catch (Exception e) {
        return new ErrorableStream<>(Stream.empty(), false);
      }
    }
  }

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
  private static final Map<Pair<String, String>, CallSite> INPUT_VARIABLES_REGISTRY =
      new ConcurrentHashMap<>();
  private static final Lookup LOOKUP = MethodHandles.lookup();
  private static final MethodHandle MH_IMYHAT__ACCEPT;
  private static final MethodHandle MH_PACK_STREAMING__CTOR;
  protected static final MethodHandle MH_TUPLE_GET;
  protected static final MethodHandle MH_TUPLE_IS_INSTANCE;

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
  }

  public static CallSite bootstrap(
      Lookup lookup, String variableName, MethodType methodType, String inputFormatName) {
    return INPUT_VARIABLES_REGISTRY.get(new Pair<>(inputFormatName, variableName));
  }

  private final List<Pair<String, JsonFieldWriter>> fieldWriters;
  private final List<GangDefinition> gangs;
  private final AutoUpdatingDirectory<LocalJsonFile> local;
  private final String name;
  private final AutoUpdatingDirectory<RemoteJsonSource> remotes;
  private final List<InputVariableDefinition> variables;

  BaseInputFormatDefinition(
      String name,
      List<InputVariableDefinition> variables,
      Map<String, List<Pair<Integer, GangElement>>> gangs) {
    this.name = name;
    local = new AutoUpdatingDirectory<>("." + name + "-input", LocalJsonFile::new);
    remotes = new AutoUpdatingDirectory<>("." + name + "-remote", RemoteJsonSource::new);
    this.variables = variables;
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
    // Now, we need a thing to write a field using the streaming Jackson interface,
    // so we take the type and combine it with the Imyhat.apply method
    fieldWriters =
        variables.stream()
            .map(
                variable ->
                    new Pair<>(
                        variable.name(),
                        MethodHandleProxies.asInterfaceInstance(
                            JsonFieldWriter.class,
                            MethodHandles.collectArguments(
                                MethodHandles.collectArguments(
                                    MH_IMYHAT__ACCEPT.bindTo(variable.type),
                                    0,
                                    MethodHandles.insertArguments(
                                        MH_PACK_STREAMING__CTOR, 1, variable.timeFormat())),
                                1,
                                variable
                                    .handle()
                                    .asType(MethodType.methodType(Object.class, Object.class))))))
            .toList();
    for (final InputVariableDefinition variable : variables) {
      INPUT_VARIABLES_REGISTRY.put(
          new Pair<>(name, variable.name()), new ConstantCallSite(variable.handle()));
    }
  }

  /** Get all the variables available for this format */
  @Override
  public final Stream<InputVariable> baseStreamVariables() {
    return variables.stream().map(x -> x);
  }

  public final Stream<? extends ConfigurationSection> configuration() {
    return Stream.concat(
        local.stream().map(LocalJsonFile::configuration),
        remotes.stream().map(RemoteJsonSource::configuration));
  }

  public final void dumpPluginConfig(TableRowWriter row) {
    local.stream().forEach(l -> row.write(false, l.fileName(), "Input Source", name));
    remotes.stream().forEach(r -> row.write(false, r.fileName.toString(), "Input Source", name));
  }

  @Override
  public final Stream<Object> fetch(String name, boolean readStale) {
    if (this.name.equals(name)) {
      return variables(readStale);
    } else {
      return Stream.empty();
    }
  }

  public final DynamicInputDataSource fromJsonStream(
      String name, int ttl, DynamicInputJsonSource source) {
    return new DynamicInputDataSourceFromJsonStream(name, ttl, source);
  }

  public final InputDataSource fromJsonStream(String name, int ttl, JsonInputSource source) {
    return new InputDataSourceFromJsonStream(name, ttl, source);
  }

  @Override
  public final Stream<? extends GangDefinition> gangs() {
    return gangs.stream();
  }

  /** The name of this format, which must be a valid Shesmu identifier */
  @Override
  public final String name() {
    return name;
  }

  private Tuple readJson(ObjectNode node) {
    final Object[] values = new Object[variables.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = variables.get(i).read(node);
    }
    return new Tuple(values);
  }

  public List<Object> readJsonString(String data) throws JsonProcessingException {
    return Stream.of(RuntimeSupport.MAPPER.readValue(data, ObjectNode[].class))
        .map(this::readJson)
        .collect(Collectors.toList());
  }

  private final Stream<Object> variables(boolean readStale) {
    return ErrorableStream.concatWithErrors(
        new ErrorableStream<>(local.stream()).flatMap(LocalJsonFile::variables),
        new ErrorableStream<>(remotes.stream()).flatMap(source -> source.variables(readStale)));
  }

  public final void writeJson(JsonGenerator generator, Stream<Object> stream) throws IOException {
    generator.writeStartArray();
    stream.forEach(
        value -> {
          try {
            generator.writeStartObject();
            for (Pair<String, JsonFieldWriter> fieldWriter : fieldWriters) {
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

  public final void writeJson(JsonGenerator generator, InputSource inputProvider, boolean readStale)
      throws IOException {
    writeJson(generator, inputProvider.fetch(name, readStale));
  }
}
