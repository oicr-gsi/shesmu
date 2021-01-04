package ca.on.oicr.gsi.shesmu.server;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.LiveExportConsumer;
import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.RefillerParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.core.actions.fake.FakeAction;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation.SourceLocationLinker;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.runtime.ActionGenerator;
import ca.on.oicr.gsi.shesmu.runtime.CompiledGenerator;
import ca.on.oicr.gsi.shesmu.runtime.OliveServices;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.status.ConfigurationSection;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.common.TextFormat;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public abstract class BaseSimulateRequest {

  protected static final class FakeActionDefinition extends ActionDefinition {

    public FakeActionDefinition(
        String name,
        String description,
        Path filename,
        Stream<ActionParameterDefinition> parameters) {
      super(name, description, filename, parameters);
    }

    @Override
    public void initialize(GeneratorAdapter methodGen) {
      methodGen.newInstance(A_FAKE_ACTION_TYPE);
      methodGen.dup();
      methodGen.push(name());
      methodGen.invokeConstructor(A_FAKE_ACTION_TYPE, FAKE_ACTION__CTOR);
    }
  }

  public static final class FakeConstant {
    private Imyhat type;
    private JsonNode value;

    public Imyhat getType() {
      return type;
    }

    public JsonNode getValue() {
      return value;
    }

    public void setType(Imyhat type) {
      this.type = type;
    }

    public void setValue(JsonNode value) {
      this.value = value;
    }
  }

  public static final class FakeRefiller<T> extends Refiller<T> {
    private final String name;
    private final List<BiConsumer<T, ObjectNode>> parameters = new ArrayList<>();

    public FakeRefiller(String name) {
      this.name = name;
    }

    @Override
    public void consume(Stream<T> items) {
      final ArrayNode result = RESULTS.get().putArray(name);
      items.forEach(
          i -> {
            final ObjectNode o = result.addObject();
            for (final BiConsumer<T, ObjectNode> parameter : parameters) {
              parameter.accept(i, o);
            }
          });
    }

    public void parameter(String name, Imyhat type, Function<T, Object> function) {
      parameters.add((i, o) -> type.accept(new PackJsonObject(o, name), function.apply(i)));
    }
  }

  protected static final class FakeRefillerDefinition implements RefillerDefinition {

    private final String description;
    private final Path filename;
    private final String name;
    private final List<RefillerParameterDefinition> parameters;

    public FakeRefillerDefinition(
        String name,
        String description,
        Path filename,
        Stream<RefillerParameterDefinition> parameters) {
      this.name = name;
      this.description = description;
      this.filename = filename;
      this.parameters = parameters.collect(Collectors.toList());
    }

    @Override
    public String description() {
      return description;
    }

    @Override
    public Path filename() {
      return filename;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Stream<RefillerParameterDefinition> parameters() {
      return parameters.stream();
    }

    @Override
    public void render(Renderer renderer) {
      renderer.methodGen().newInstance(A_FAKE_REFILLER_TYPE);
      renderer.methodGen().dup();
      renderer.methodGen().push(name);
      renderer.methodGen().invokeConstructor(A_FAKE_REFILLER_TYPE, FAKE_REFILLER__CTOR);
    }
  }

  protected static final class JsonActionParameterDefinition implements ActionParameterDefinition {

    private final String name;
    private final boolean required;
    private final Imyhat type;

    public JsonActionParameterDefinition(String name, boolean required, Imyhat type) {
      this.name = name;
      this.required = required;
      this.type = type;
    }

    public JsonActionParameterDefinition(ActionParameterDefinition other) {
      this(other.name(), other.required(), other.type());
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public boolean required() {
      return required;
    }

    @Override
    public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
      renderer.loadImyhat(type.descriptor());
      renderer.methodGen().newInstance(A_PACK_JSON_OBJECT_TYPE);
      renderer.methodGen().dup();
      renderer.methodGen().loadLocal(actionLocal);
      renderer.methodGen().checkCast(A_FAKE_ACTION_TYPE);
      renderer.methodGen().invokeVirtual(A_FAKE_ACTION_TYPE, FAKE_ACTION__PARAMETERS);
      renderer.methodGen().push(name);
      renderer.methodGen().invokeConstructor(A_PACK_JSON_OBJECT_TYPE, PACK_JSON_OBJECT__CTOR);
      loadParameter.accept(renderer);
      renderer.methodGen().box(type.apply(TO_ASM));
      renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, IMYHAT__ACCEPT);
    }

    @Override
    public Imyhat type() {
      return type;
    }
  }

  protected static final class JsonConstant extends ConstantDefinition {
    private static final Handle JSON_BOOTSTRAP =
        new Handle(
            Opcodes.H_INVOKESTATIC,
            Type.getInternalName(RuntimeSupport.class),
            "jsonBootstrap",
            Type.getMethodDescriptor(
                Type.getType(CallSite.class),
                Type.getType(MethodHandles.Lookup.class),
                Type.getType(String.class),
                Type.getType(MethodType.class),
                Type.getType(String.class)),
            false);
    private final String value;

    public JsonConstant(String name, Imyhat type, JsonNode node) {
      super(
          String.join(Parser.NAMESPACE_SEPARATOR, "shesmu", "simulated", name),
          type,
          "Simulated constant",
          null);
      try {
        this.value = RuntimeSupport.MAPPER.writeValueAsString(node);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public void load(GeneratorAdapter methodGen) {
      methodGen.invokeDynamic(
          type().descriptor(),
          Type.getMethodDescriptor(type().apply(TO_ASM)),
          JSON_BOOTSTRAP,
          value);
    }

    @Override
    public String load() {
      return value;
    }
  }

  protected static final class JsonRefillerParameterDefinition
      implements RefillerParameterDefinition {

    private final String name;
    private final Imyhat type;

    public JsonRefillerParameterDefinition(String name, Imyhat type) {
      this.name = name;
      this.type = type;
    }

    public JsonRefillerParameterDefinition(RefillerParameterDefinition other) {
      this(other.name(), other.type());
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public void render(Renderer renderer, int refillerLocal, int functionLocal) {
      renderer.methodGen().loadLocal(refillerLocal);
      renderer.methodGen().checkCast(A_FAKE_REFILLER_TYPE);
      renderer.methodGen().push(name);
      renderer.loadImyhat(type.descriptor());
      renderer.methodGen().loadLocal(functionLocal);
      renderer.methodGen().invokeVirtual(A_FAKE_REFILLER_TYPE, FAKE_REFILLER__PARAMETER);
    }

    @Override
    public Imyhat type() {
      return type;
    }
  }

  private static final Type A_FAKE_ACTION_TYPE = Type.getType(FakeAction.class);
  private static final Type A_FAKE_REFILLER_TYPE = Type.getType(FakeRefiller.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_OBJECT_NODE_TYPE = Type.getType(ObjectNode.class);
  private static final Type A_PACK_JSON_OBJECT_TYPE = Type.getType(PackJsonObject.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method FAKE_ACTION__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method FAKE_ACTION__PARAMETERS =
      new Method("parameters", A_OBJECT_NODE_TYPE, new Type[0]);
  private static final Method FAKE_REFILLER__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method FAKE_REFILLER__PARAMETER =
      new Method(
          "parameter",
          Type.VOID_TYPE,
          new Type[] {A_STRING_TYPE, Type.getType(Imyhat.class), Type.getType(Function.class)});
  private static final Method IMYHAT__ACCEPT =
      new Method(
          "accept",
          Type.VOID_TYPE,
          new Type[] {Type.getType(ImyhatConsumer.class), Type.getType(Object.class)});
  private static final Method PACK_JSON_OBJECT__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_OBJECT_NODE_TYPE, A_STRING_TYPE});
  private static final ThreadLocal<ObjectNode> RESULTS = new ThreadLocal<>();
  private Map<String, FakeConstant> fakeConstants = Collections.emptyMap();
  private boolean readStale;

  protected abstract boolean allowUnused();

  protected abstract boolean dryRun();

  protected abstract Stream<FakeActionDefinition> fakeActions();

  protected abstract Stream<FakeRefillerDefinition> fakeRefillers();

  public final Map<String, FakeConstant> getFakeConstants() {
    return fakeConstants;
  }

  protected abstract boolean keepNativeAction(String name);

  protected final void run(
      DefinitionRepository definitionRepository,
      ActionServices actionServices,
      InputSource inputSource,
      String script,
      HttpExchange http)
      throws IOException {
    final ObjectNode response = RuntimeSupport.MAPPER.createObjectNode();
    final ArrayNode exports = response.putArray("exports");
    final ArrayNode errors = response.putArray("errors");
    final AtomicReference<FileTable> fileTable = new AtomicReference<>();
    final AtomicBoolean exceptionThrown = new AtomicBoolean();
    try {
      final HotloadingCompiler compiler =
          new HotloadingCompiler(
              CompiledGenerator.SOURCES::get,
              new DefinitionRepository() {
                private final List<ConstantDefinition> jsonConstants =
                    fakeConstants
                        .entrySet()
                        .stream()
                        .map(
                            e ->
                                new JsonConstant(
                                    e.getKey(), e.getValue().getType(), e.getValue().getValue()))
                        .collect(Collectors.toList());

                @Override
                public Stream<ActionDefinition> actions() {
                  return Stream.concat(
                      definitionRepository
                          .actions()
                          .filter(a -> keepNativeAction(a.name()))
                          .map(
                              a ->
                                  new FakeActionDefinition(
                                      a.name(),
                                      a.description(),
                                      null,
                                      a.parameters().map(JsonActionParameterDefinition::new))),
                      fakeActions());
                }

                @Override
                public Stream<ConstantDefinition> constants() {
                  return Stream.concat(definitionRepository.constants(), jsonConstants.stream());
                }

                @Override
                public Stream<FunctionDefinition> functions() {
                  return definitionRepository.functions();
                }

                @Override
                public Stream<ConfigurationSection> listConfiguration() {
                  return definitionRepository.listConfiguration();
                }

                @Override
                public Stream<CallableOliveDefinition> oliveDefinitions() {
                  return definitionRepository.oliveDefinitions();
                }

                @Override
                public Stream<RefillerDefinition> refillers() {
                  return Stream.concat(
                      definitionRepository
                          .refillers()
                          .map(
                              refiller ->
                                  new FakeRefillerDefinition(
                                      refiller.name(),
                                      refiller.description(),
                                      null,
                                      refiller
                                          .parameters()
                                          .map(JsonRefillerParameterDefinition::new))),
                      fakeRefillers());
                }

                @Override
                public Stream<SignatureDefinition> signatures() {
                  return definitionRepository.signatures();
                }

                @Override
                public void writeJavaScriptRenderer(PrintStream writer) {
                  definitionRepository.writeJavaScriptRenderer(writer);
                }
              });
      final Optional<ActionGenerator> result =
          compiler.compile(
              "Uploaded Simulation Request.shesmu",
              script,
              new LiveExportConsumer() {
                @Override
                public void constant(MethodHandle method, String name, Imyhat type) {
                  final ObjectNode info = exports.addObject();
                  info.put("name", name);
                  info.put("type", "constant");
                  info.put("returns", type.descriptor());
                }

                @Override
                public void defineOlive(
                    MethodHandle method,
                    String name,
                    String inputFormatName,
                    boolean isRoot,
                    List<Imyhat> parameterTypes,
                    List<DefineVariableExport> variables,
                    List<DefineVariableExport> checks) {
                  final ObjectNode info = exports.addObject();
                  info.put("type", "define");
                  info.put("name", name);
                  info.put("isRoot", isRoot);
                  info.put("inputFormat", inputFormatName);
                  final ObjectNode output = info.putObject("output");
                  for (final DefineVariableExport variable : variables) {
                    output.put(variable.name(), variable.type().name());
                  }
                  parameterTypes
                      .stream()
                      .map(Imyhat::descriptor)
                      .forEach(info.putArray("parameters")::add);
                }

                @Override
                public void function(
                    MethodHandle method,
                    String name,
                    Imyhat returnType,
                    Supplier<Stream<FunctionParameter>> parameters) {
                  final ObjectNode info = exports.addObject();
                  info.put("name", name);
                  info.put("type", "function");
                  info.put("returns", returnType.descriptor());
                  parameters
                      .get()
                      .map(FunctionParameter::type)
                      .map(Imyhat::descriptor)
                      .forEach(info.putArray("parameters")::add);
                }
              },
              fileTable::set,
              importVerifier -> {},
              allowUnused());
      compiler.errors().forEach(errors::add);
      if (fileTable.get() != null) {
        response.put("bytecode", fileTable.get().bytecode());
      }
      if (!dryRun()) {
        result.ifPresent(
            action -> {
              final Set<String> overloadedInputs =
                  actionServices.isOverloaded(action.inputs().collect(Collectors.toSet()));
              if (!overloadedInputs.isEmpty()) {
                overloadedInputs.forEach(response.putArray("overloadedInputs")::add);
                return;
              }

              final CollectorRegistry registry = new CollectorRegistry();
              action.register(registry);
              final Map<Action, Pair<Set<String>, Set<SourceLocation>>> actions = new HashMap<>();
              final Map<Pair<List<String>, List<String>>, Set<SourceLocation>> alerts =
                  new HashMap<>();
              final ObjectNode dumpers = response.putObject("dumpers");
              final ArrayNode olives = response.putArray("olives");
              final ArrayNode overloadedServices = response.putArray("overloadedServices");
              final Map<Pair<Integer, Integer>, Long> durations = new HashMap<>();

              final Map<Pair<SourceLocation, SourceLocation>, AtomicLong> flow = new HashMap<>();
              RESULTS.set(response.putObject("refillers"));
              final Map<String, Long> counts = new HashMap<>();

              try {
                final Map<String, List<Object>> inputs =
                    action
                        .inputs()
                        .collect(
                            Collectors.toMap(
                                Function.identity(),
                                name ->
                                    inputSource
                                        .fetch(name, readStale)
                                        .collect(Collectors.toList())));
                for (final Map.Entry<String, List<Object>> input : inputs.entrySet()) {
                  counts.put(input.getKey(), (long) input.getValue().size());
                }
                action.run(
                    new OliveServices() {
                      @Override
                      public boolean accept(
                          Action action,
                          String filename,
                          int line,
                          int column,
                          String hash,
                          String[] tags) {
                        final Pair<Set<String>, Set<SourceLocation>> info =
                            actions.computeIfAbsent(
                                action, k -> new Pair<>(new TreeSet<>(), new HashSet<>()));
                        return info.first().addAll(Arrays.asList(tags))
                            | info.second().add(new SourceLocation(filename, line, column, hash));
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
                        return alerts
                            .computeIfAbsent(
                                new Pair<>(Arrays.asList(labels), Arrays.asList(annotation)),
                                k -> new HashSet<>())
                            .add(new SourceLocation(filename, line, column, hash));
                      }

                      @Override
                      public Dumper findDumper(String name, String[] columns, Imyhat... types) {
                        return new Dumper() {
                          private final ArrayNode dump = dumpers.putArray(name);

                          @Override
                          public void stop() {
                            // Do nothing
                          }

                          @Override
                          public void write(Object... values) {
                            final ObjectNode row = dump.addObject();
                            for (int i = 0; i < types.length; i++) {
                              types[i].accept(new PackJsonObject(row, columns[i]), values[i]);
                            }
                          }
                        };
                      }

                      @Override
                      public boolean isOverloaded(String... services) {
                        final Set<String> overloads = actionServices.isOverloaded(services);
                        overloads.forEach(overloadedServices::add);
                        return !overloads.isEmpty();
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
                        final AtomicLong counter = new AtomicLong();
                        flow.put(
                            new Pair<>(
                                new SourceLocation(filename, line, column, hash),
                                new SourceLocation(oliveFile, oliveLine, oliveColumn, oliveHash)),
                            counter);
                        return input.peek(i -> counter.incrementAndGet());
                      }

                      @Override
                      public void oliveRuntime(
                          String filename, int line, int column, long timeInNs) {
                        durations.put(new Pair<>(line, column), timeInNs);
                      }
                    },
                    format -> inputs.get(format).stream());
              } catch (InitialCachePopulationException e) {
                exceptionThrown.set(true);
                errors.add(String.format("Failed to populate %s cache", e.getMessage()));
              }

              final StringWriter writer = new StringWriter();
              try {
                TextFormat.write004(writer, registry.metricFamilySamples());
              } catch (IOException e) {
                e.printStackTrace();
              }
              response.put("metrics", writer.toString());
              action.unregister(registry);

              fileTable
                  .get()
                  .olives()
                  .forEach(
                      olive -> {
                        try {
                          final StringWriter metroWriter = new StringWriter();
                          final XMLStreamWriter xmlWriter =
                              XMLOutputFactory.newFactory().createXMLStreamWriter(metroWriter);
                          xmlWriter.writeStartDocument("utf-8", "1.0");
                          MetroDiagram.draw(
                              xmlWriter,
                              (localFilePath, line, column, hash) -> Stream.empty(),
                              fileTable.get().filename(),
                              fileTable.get().hash(),
                              olive,
                              counts.get(fileTable.get().format().name()),
                              fileTable.get().format(),
                              (filename,
                                  line,
                                  column,
                                  hash,
                                  oliveFilename,
                                  oliveLine,
                                  oliveColumn,
                                  oliveHash) ->
                                  Optional.ofNullable(
                                          flow.get(
                                              new Pair<>(
                                                  new SourceLocation(filename, line, column, hash),
                                                  new SourceLocation(
                                                      oliveFilename,
                                                      oliveLine,
                                                      oliveColumn,
                                                      oliveHash))))
                                      .map(AtomicLong::get)
                                      .orElse(null));
                          xmlWriter.writeEndDocument();

                          final ObjectNode diagram = olives.addObject();
                          diagram.put("diagram", metroWriter.toString());
                          diagram.put("line", olive.line());
                          diagram.put("syntax", olive.syntax());
                          diagram.put("description", olive.description());
                          diagram.put("produces", olive.produces().name());
                          diagram.put("column", olive.column());
                          diagram.put(
                              "duration", durations.get(new Pair<>(olive.line(), olive.column())));

                        } catch (XMLStreamException e) {
                          e.printStackTrace();
                        }
                      });

              final ArrayNode actionsJson = response.putArray("actions");
              for (final Map.Entry<Action, Pair<Set<String>, Set<SourceLocation>>> a :
                  actions.entrySet()) {
                final ObjectNode aj = a.getKey().toJson(RuntimeSupport.MAPPER);
                aj.put("type", a.getKey().type());
                a.getValue().first().forEach(aj.putArray("tags")::add);
                final ArrayNode locations = aj.putArray("locations");
                a.getValue().second().forEach(l -> l.toJson(locations, SourceLocationLinker.EMPTY));
                actionsJson.add(aj);
              }
              final ArrayNode alertsJson = response.putArray("alerts");
              for (final Map.Entry<Pair<List<String>, List<String>>, Set<SourceLocation>> a :
                  alerts.entrySet()) {
                final ObjectNode alertJson = alertsJson.addObject();
                writeLabels(alertJson.putObject("labels"), a.getKey().first());
                writeLabels(alertJson.putObject("annotations"), a.getKey().second());
                final ArrayNode locations = alertJson.putArray("locations");
                a.getValue().forEach(l -> l.toJson(locations, SourceLocationLinker.EMPTY));
              }
            });
      }
    } catch (Exception e) {
      exceptionThrown.set(true);
      errors.add(e.getMessage());
      e.printStackTrace();
    }
    response.put("exceptionThrown", exceptionThrown.get());
    http.getResponseHeaders().set("Content-type", "application/json");
    http.sendResponseHeaders(200, 0);
    try (OutputStream os = http.getResponseBody()) {
      RuntimeSupport.MAPPER.writeValue(os, response);
    }
  }

  public final void setFakeConstants(Map<String, FakeConstant> fakeConstants) {
    this.fakeConstants = fakeConstants;
  }

  public final void setReadStale(boolean readStale) {
    this.readStale = readStale;
  }

  private void writeLabels(ObjectNode output, List<String> input) {
    for (int i = 0; i < input.size(); i += 2) {
      output.put(input.get(i), input.get(i + 1));
    }
  }
}
