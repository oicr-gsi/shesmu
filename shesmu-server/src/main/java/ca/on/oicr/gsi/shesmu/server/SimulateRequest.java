package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.FileTable;
import ca.on.oicr.gsi.shesmu.core.actions.fake.FakeAction;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonArray;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.plugin.wdl.PackWdlVariables;
import ca.on.oicr.gsi.shesmu.plugin.wdl.WdlInputType;
import ca.on.oicr.gsi.shesmu.runtime.*;
import ca.on.oicr.gsi.status.ConfigurationSection;
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
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class SimulateRequest {
  private static class FakeActionDefinition extends ActionDefinition {

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

  public static class FakeActionParameter {
    boolean required;
    JsonNode type;

    public JsonNode getType() {
      return type;
    }

    public boolean isRequired() {
      return required;
    }

    public void setRequired(boolean required) {
      this.required = required;
    }

    public void setType(JsonNode type) {
      this.type = type;
    }
  }

  private static final class JsonParameterDefintion implements ActionParameterDefinition {

    private final String name;
    private final boolean required;
    private final Imyhat type;

    public JsonParameterDefintion(String name, boolean required, Imyhat type) {
      this.name = name;
      this.required = required;
      this.type = type;
    }

    public JsonParameterDefintion(ActionParameterDefinition other) {
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
      renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, IMYHAT__ACCEPT);
    }

    @Override
    public Imyhat type() {
      return type;
    }
  }

  private static Imyhat convertType(JsonNode type, Consumer<String> errorHandler) {
    switch (type.getNodeType()) {
      case OBJECT:
        return PackWdlVariables.create(
                WdlInputType.of(
                    (ObjectNode) type,
                    (line, column, errorMessage) ->
                        errorHandler.accept(
                            String.format("%d:%d: %s", line, column, errorMessage))))
            .second();
      case STRING:
        return Imyhat.parse(type.asText());
      default:
        return Imyhat.BAD;
    }
  }

  private static final Type A_FAKE_ACTION_TYPE = Type.getType(FakeAction.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_OBJECT_NODE_TYPE = Type.getType(ObjectNode.class);
  private static final Type A_PACK_JSON_OBJECT_TYPE = Type.getType(PackJsonObject.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method FAKE_ACTION__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method FAKE_ACTION__PARAMETERS =
      new Method("parameters", A_OBJECT_NODE_TYPE, new Type[0]);
  private static final Method IMYHAT__ACCEPT =
      new Method(
          "accept",
          Type.VOID_TYPE,
          new Type[] {Type.getType(ImyhatConsumer.class), Type.getType(Object.class)});
  private static final Method PACK_JSON_OBJECT__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_OBJECT_NODE_TYPE, A_STRING_TYPE});
  private Map<String, Map<String, FakeActionParameter>> fakeActions = Collections.emptyMap();
  private String script;

  public Map<String, Map<String, FakeActionParameter>> getFakeActions() {
    return fakeActions;
  }

  public String getScript() {
    return script;
  }

  public void run(
      DefinitionRepository definitionRepository,
      Supplier<Stream<FunctionDefinition>> importableFunctions,
      ActionServices actionServices,
      InputProvider inputProvider,
      HttpExchange http)
      throws IOException {
    final ObjectNode response = RuntimeSupport.MAPPER.createObjectNode();
    final ObjectNode exports = response.putObject("exports");
    final ArrayNode errors = response.putArray("errors");
    final AtomicReference<FileTable> fileTable = new AtomicReference<>();
    final HotloadingCompiler compiler =
        new HotloadingCompiler(
            CompiledGenerator.SOURCES::get,
            new DefinitionRepository() {
              @Override
              public Stream<ActionDefinition> actions() {
                return Stream.concat(
                    definitionRepository
                        .actions()
                        .filter(a -> !fakeActions.containsKey(a.name()))
                        .map(
                            a ->
                                new FakeActionDefinition(
                                    a.name(),
                                    a.description(),
                                    null,
                                    a.parameters().map(JsonParameterDefintion::new))),
                    fakeActions
                        .entrySet()
                        .stream()
                        .map(
                            e ->
                                new FakeActionDefinition(
                                    e.getKey(),
                                    "Provided in simulation request",
                                    null,
                                    e.getValue()
                                        .entrySet()
                                        .stream()
                                        .map(
                                            pe ->
                                                new JsonParameterDefintion(
                                                    pe.getKey(),
                                                    pe.getValue().required,
                                                    convertType(
                                                        pe.getValue().getType(), errors::add))))));
              }

              @Override
              public Stream<ConstantDefinition> constants() {
                return definitionRepository.constants();
              }

              @Override
              public Stream<FunctionDefinition> functions() {
                return Stream.concat(definitionRepository.functions(), importableFunctions.get());
              }

              @Override
              public Stream<ConfigurationSection> listConfiguration() {
                return definitionRepository.listConfiguration();
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
            (method, name, returnType, parameters) -> {
              final ObjectNode info = exports.putObject(name);
              info.put("returns", returnType.descriptor());
              parameters
                  .get()
                  .map(FunctionParameter::type)
                  .map(Imyhat::descriptor)
                  .forEach(info.putArray("parameters")::add);
            },
            fileTable::set);
    compiler.errors().forEach(errors::add);
    if (fileTable.get() != null) {
      response.put("bytecode", fileTable.get().bytecode());
    }
    result.ifPresent(
        action -> {
          if (action
                  .inputs()
                  .filter(actionServices::isOverloaded)
                  .peek(response.putArray("overloadedInputs")::add)
                  .count()
              > 0) {
            return;
          }
          final CollectorRegistry registry = new CollectorRegistry();
          action.register(registry);
          final Set<Action> actions = new HashSet<>();
          final Set<Pair<List<String>, List<String>>> alerts = new HashSet<>();
          final ObjectNode dumpers = response.putObject("dumpers");
          final ArrayNode olives = response.putArray("olives");
          final Map<Pair<Integer, Integer>, Long> durations = new HashMap<>();
          final Map<String, List<Object>> inputs =
              action
                  .inputs()
                  .collect(
                      Collectors.toMap(
                          Function.identity(),
                          name -> inputProvider.fetch(name).collect(Collectors.toList())));
          final Map<List<Integer>, AtomicLong> flow = new HashMap<>();

          action.run(
              new OliveServices() {
                @Override
                public boolean accept(
                    Action action,
                    String filename,
                    int line,
                    int column,
                    long time,
                    String[] tags) {
                  return actions.add(action);
                }

                @Override
                public boolean accept(String[] labels, String[] annotation, long ttl)
                    throws Exception {
                  return alerts.add(new Pair<>(Arrays.asList(labels), Arrays.asList(annotation)));
                }

                @Override
                public Dumper findDumper(String name, Imyhat... types) {
                  return new Dumper() {
                    private final ArrayNode dump = dumpers.putArray(name);

                    @Override
                    public void stop() {
                      // Do nothing
                    }

                    @Override
                    public void write(Object... values) {
                      final ArrayNode row = dump.addArray();
                      for (int i = 0; i < types.length; i++) {
                        types[i].accept(new PackJsonArray(row), values[i]);
                      }
                    }
                  };
                }

                @Override
                public boolean isOverloaded(String... services) {
                  return actionServices.isOverloaded(services);
                }

                @Override
                public <T> Stream<T> measureFlow(
                    Stream<T> input,
                    String filename,
                    int line,
                    int column,
                    int oliveLine,
                    int oliveColumn) {
                  final AtomicLong counter = new AtomicLong();
                  flow.put(Arrays.asList(line, column, oliveLine, oliveColumn), counter);
                  return input.peek(i -> counter.incrementAndGet());
                }

                @Override
                public void oliveRuntime(String filename, int line, int column, long timeInNs) {
                  durations.put(new Pair<>(line, column), timeInNs);
                }
              },
              format -> inputs.get(format).stream());

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
                          (localFilePath, line, column, time) -> Stream.empty(),
                          fileTable.get().filename(),
                          fileTable.get().timestamp(),
                          olive,
                          (long) inputs.get(fileTable.get().format().name()).size(),
                          fileTable.get().format(),
                          (filename, line, column, oliveLine, oliveColumn) ->
                              flow.get(Arrays.asList(line, column, oliveLine, oliveColumn)).get());
                      xmlWriter.writeEndDocument();

                      final ObjectNode diagram = olives.addObject();
                      diagram.put("diagram", metroWriter.toString());
                      diagram.put("line", olive.line());
                      diagram.put("syntax", olive.syntax());
                      diagram.put("description", olive.description());
                      diagram.put("producesActions", olive.producesActions());
                      diagram.put("column", olive.column());
                      diagram.put(
                          "duration", durations.get(new Pair<>(olive.line(), olive.column())));

                    } catch (XMLStreamException e) {
                      e.printStackTrace();
                    }
                  });

          final ArrayNode actionsJson = response.putArray("actions");
          for (final Action a : actions) {
            final ObjectNode aj = a.toJson(RuntimeSupport.MAPPER);
            aj.put("type", a.type());
            actionsJson.add(aj);
          }
          final ArrayNode alertsJson = response.putArray("alerts");
          for (final Pair<List<String>, List<String>> a : alerts) {
            final ObjectNode alertJson = alertsJson.addObject();
            writeLabels(alertJson.putObject("labels"), a.first());
            writeLabels(alertJson.putObject("annotations"), a.second());
          }
        });
    http.getResponseHeaders().set("Content-type", "application/json");
    http.sendResponseHeaders(200, 0);
    try (OutputStream os = http.getResponseBody()) {
      RuntimeSupport.MAPPER.writeValue(os, response);
    }
  }

  public void setFakeActions(Map<String, Map<String, FakeActionParameter>> fakeActions) {
    this.fakeActions = fakeActions;
  }

  public void setScript(String script) {
    this.script = script;
  }

  private void writeLabels(ObjectNode output, List<String> input) {
    for (int i = 0; i < input.size(); i += 2) {
      output.put(input.get(i), input.get(i + 1));
    }
  }
}
