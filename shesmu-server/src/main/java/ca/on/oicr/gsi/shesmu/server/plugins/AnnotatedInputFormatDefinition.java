package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Server;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.cache.InvalidatableRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackStreaming;
import ca.on.oicr.gsi.shesmu.plugin.json.UnpackJson;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.runtime.InputProvider;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
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
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/** Define a <tt>Input</tt> format for olives to consume */
public final class AnnotatedInputFormatDefinition implements InputFormatDefinition, InputProvider {
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

  public interface JsonFieldWriter {
    void write(JsonGenerator generator, Object value);
  }

  private class LocalJsonFile implements WatchedFileListener {
    private final ValueCache<Optional<List<Object>>> values;
    private volatile boolean dirty = true;

    public LocalJsonFile(Path fileName) {
      values =
          new ValueCache<Optional<List<Object>>>(
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
    }

    @Override
    public void start() {
      update();
    }

    @Override
    public void stop() {}

    @Override
    public Optional<Integer> update() {
      dirty = true;
      return Optional.empty();
    }

    public Stream<Object> variables() {
      return values.get().map(List::stream).orElseGet(Stream::empty);
    }
  }

  private class RemoteJsonSource implements WatchedFileListener {
    private class RemoteReloader extends ValueCache<Stream<Object>> {
      public RemoteReloader(Path fileName) {
        super("remotejson " + format.name() + " " + fileName.toString(), 10, ReplacingRecord::new);
      }

      @Override
      protected Stream<Object> fetch(Instant lastUpdated) throws Exception {
        if (!config.isPresent()) return Stream.empty();
        final String url = config.get().getUrl();
        try (CloseableHttpResponse response = Server.HTTP_CLIENT.execute(new HttpGet(url));
            JsonParser parser =
                RuntimeSupport.MAPPER
                    .getFactory()
                    .createParser(response.getEntity().getContent())) {
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

    @Override
    public void start() {
      update();
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

    public Stream<Object> variables() {
      return cache.get();
    }
  }

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Handle BSM_INPUT_VARIABLE =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          Type.getType(AnnotatedInputFormatDefinition.class).getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(Lookup.class),
              Type.getType(String.class),
              Type.getType(MethodType.class),
              Type.getType(String.class)),
          false);
  private static final List<AnnotatedInputFormatDefinition> FORMATS = new ArrayList<>();
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
                  PackStreaming.class, MethodType.methodType(void.class, JsonGenerator.class))
              .asType(MethodType.methodType(ImyhatConsumer.class, JsonGenerator.class));
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
    for (final InputFormat format : ServiceLoader.load(InputFormat.class)) {
      try {
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

  private final InputFormat format;;
  private final AutoUpdatingDirectory<LocalJsonFile> local;

  private final AutoUpdatingDirectory<RemoteJsonSource> remotes;

  private final List<InputVariable> variables = new ArrayList<>();

  public AnnotatedInputFormatDefinition(InputFormat format) throws IllegalAccessException {
    this.format = format;
    // Get all the candidate methods in this class and sort them alphabetically
    final SortedMap<String, Pair<ShesmuVariable, Method>> sortedMethods = new TreeMap<>();
    for (final Method method : format.type().getMethods()) {
      final ShesmuVariable info = method.getAnnotation(ShesmuVariable.class);
      if (info == null) {
        continue;
      }
      sortedMethods.put(method.getName(), new Pair<>(info, method));
    }
    for (final Pair<ShesmuVariable, Method> entry : sortedMethods.values()) {
      // Create a target for each method for the compiler to use
      final String name = entry.second().getName();
      final Imyhat type =
          Imyhat.convert(
              String.format(
                  "Return type of %s in %s", name, entry.second().getDeclaringClass().getName()),
              entry.first().type(),
              entry.second().getGenericReturnType());
      final Flavour flavour = entry.first().signable() ? Flavour.STREAM_SIGNABLE : Flavour.STREAM;
      final MethodType methodType = MethodType.methodType(type.javaType(), Object.class);
      // Register this variable with the compiler
      variables.add(
          new InputVariable() {

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
            public Imyhat type() {
              return type;
            }
          });
      // Now we need to make a call site for this variable. It will happen in two
      // cases: either we have an instance of the real type and we should call the
      // method on it, or we have a Tuple that was generated generically by one of our
      // JSON readers
      final MethodHandle getter = LOOKUP.unreflect(entry.second()).asType(methodType);
      final MethodHandle handle =
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
                          MH_IMYHAT__ACCEPT.bindTo(type), 0, MH_PACK_STREAMING__CTOR),
                      1,
                      handle.asType(MethodType.methodType(Object.class, Object.class))))));
    }
    local = new AutoUpdatingDirectory<>("." + format.name() + "-input", LocalJsonFile::new);
    remotes = new AutoUpdatingDirectory<>("." + format.name() + "-remote", RemoteJsonSource::new);
  }

  /** Get all the variables available for this format */
  @Override
  public Stream<InputVariable> baseStreamVariables() {
    return variables.stream();
  }

  @Override
  public Stream<Object> fetch(String name) {
    if (name.equals(format.name())) {
      return variables();
    } else {
      return Stream.empty();
    }
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
    final Object[] values = new Object[variables.size()];
    for (int i = 0; i < values.length; i++) {
      values[i] = variables.get(i).type().apply(new UnpackJson(node.get(variables.get(i).name())));
    }
    return new Tuple(values);
  }

  @Override
  public Type type() {
    return A_OBJECT_TYPE;
  }

  private Stream<Object> variables() {
    return Stream.concat(
        local.stream().flatMap(LocalJsonFile::variables),
        remotes.stream().flatMap(RemoteJsonSource::variables));
  }

  public void writeJson(JsonGenerator generator, InputProvider inputProvider) throws IOException {
    generator.writeStartArray();
    inputProvider
        .fetch(format.name())
        .forEach(
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
}
