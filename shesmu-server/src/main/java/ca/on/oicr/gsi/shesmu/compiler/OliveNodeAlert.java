package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class OliveNodeAlert extends OliveNodeWithClauses {

  private final class ArgumentCheckAndDefiner implements Predicate<OliveArgumentNode> {
    private final Consumer<String> errorHandler;
    private int i;

    private ArgumentCheckAndDefiner(Consumer<String> errorHandler) {
      this.errorHandler = errorHandler;
    }

    @Override
    public boolean test(OliveArgumentNode argument) {
      return (argument.typeCheck(errorHandler) & argument.checkName(errorHandler))
          && argument.checkArguments(
              name ->
                  new ActionParameterDefinition() {
                    final int index = i++;

                    @Override
                    public String name() {
                      return name;
                    }

                    @Override
                    public boolean required() {
                      return false;
                    }

                    @Override
                    public void store(
                        Renderer renderer, int arrayLocal, Consumer<Renderer> loadParameter) {
                      renderer.methodGen().loadLocal(arrayLocal);
                      renderer.methodGen().push(index * 2);
                      renderer.methodGen().push(name);
                      renderer.methodGen().arrayStore(A_STRING_TYPE);
                      renderer.methodGen().loadLocal(arrayLocal);
                      renderer.methodGen().push(index * 2 + 1);
                      loadParameter.accept(renderer);
                      renderer.methodGen().arrayStore(A_STRING_TYPE);
                    }

                    @Override
                    public Imyhat type() {
                      return Imyhat.STRING;
                    }
                  },
              errorHandler);
    }
  }

  protected static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
  protected static final Type A_STRING_TYPE = Type.getType(String.class);
  private final List<OliveArgumentNode> annotations;
  private final int column;
  private final List<OliveArgumentNode> labels;
  private final int line;
  private final ExpressionNode ttl;
  private final Set<String> tags;
  private final String description;

  public OliveNodeAlert(
      int line,
      int column,
      List<OliveArgumentNode> label,
      List<OliveArgumentNode> annotations,
      ExpressionNode ttl,
      List<OliveClauseNode> clauses,
      Set<String> tags,
      String description) {
    super(clauses);
    this.line = line;
    this.column = column;
    labels = label;
    this.annotations = annotations;
    this.ttl = ttl;
    this.tags = tags;
    this.description = description;
  }

  @Override
  public void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    // Do nothing.
  }

  @Override
  protected void collectArgumentSignableVariables() {
    labels.forEach(arg -> arg.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
    annotations.forEach(
        arg -> arg.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
    ttl.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
  }

  @Override
  public boolean collectDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Map<String, Target> definedConstants,
      Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void collectPluginsExtra(Set<Path> pluginFileNames) {
    labels.forEach(arg -> arg.collectPlugins(pluginFileNames));
    annotations.forEach(arg -> arg.collectPlugins(pluginFileNames));
    ttl.collectPlugins(pluginFileNames);
  }

  @Override
  public Stream<OliveTable> dashboard() {
    final Set<String> ttlInputs = new HashSet<>();
    ttl.collectFreeVariables(ttlInputs, Flavour::isStream);

    return Stream.of(
        new OliveTable(
            "Alert",
            line,
            column,
            false,
            tags,
            description,
            clauses().stream().flatMap(OliveClauseNode::dashboard),
            Stream.concat(
                Stream.of(labels, annotations)
                    .flatMap(List::stream)
                    .flatMap(
                        arg -> {
                          final Set<String> inputs = new HashSet<>();
                          arg.collectFreeVariables(inputs, Flavour::isStream);
                          return arg.targets()
                              .map(
                                  t ->
                                      new VariableInformation(
                                          t.name(),
                                          Imyhat.STRING,
                                          inputs.parallelStream(),
                                          Behaviour.DEFINITION));
                        }),
                ttlInputs
                    .stream()
                    .map(
                        n ->
                            new VariableInformation(
                                n, Imyhat.INTEGER, Stream.of(n), Behaviour.OBSERVER)))));
  }

  @Override
  public void processExport(ExportConsumer exportConsumer) {
    // Not exportable
  }

  @Override
  public void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    final Set<String> captures = new HashSet<>();
    ttl.collectFreeVariables(captures, Flavour::needsCapture);
    labels.forEach(label -> label.collectFreeVariables(captures, Flavour::needsCapture));
    annotations.forEach(
        annotation -> annotation.collectFreeVariables(captures, Flavour::needsCapture));
    final OliveBuilder oliveBuilder = builder.buildRunOlive(line, column, signableNames);
    clauses().forEach(clause -> clause.render(builder, oliveBuilder, definitions));
    oliveBuilder.line(line);
    final Renderer action =
        oliveBuilder.finish(
            "Alert", oliveBuilder.loadableValues().filter(v -> captures.contains(v.name())));
    action.methodGen().visitCode();
    action.methodGen().visitLineNumber(line, action.methodGen().mark());

    final int labelLocal = action.methodGen().newLocal(A_STRING_ARRAY_TYPE);
    action.methodGen().push((int) labels.stream().flatMap(OliveArgumentNode::targets).count() * 2);
    action.methodGen().newArray(A_STRING_TYPE);
    action.methodGen().storeLocal(labelLocal);

    labels.forEach(l -> l.render(action, labelLocal));

    final int annotationLocal = action.methodGen().newLocal(A_STRING_ARRAY_TYPE);
    action
        .methodGen()
        .push((int) annotations.stream().flatMap(OliveArgumentNode::targets).count() * 2);
    action.methodGen().newArray(A_STRING_TYPE);
    action.methodGen().storeLocal(annotationLocal);

    annotations.forEach(a -> a.render(action, annotationLocal));

    final int ttlLocal = action.methodGen().newLocal(Type.LONG_TYPE);
    ttl.render(action);
    action.methodGen().storeLocal(ttlLocal);

    oliveBuilder.emitAlert(action.methodGen(), labelLocal, annotationLocal, ttlLocal);
    action.methodGen().visitInsn(Opcodes.RETURN);
    action.methodGen().visitMaxs(0, 0);
    action.methodGen().visitEnd();
  }

  @Override
  public boolean resolve(
      InputFormatDefinition inputFormatDefinition,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      Function<String, InputFormatDefinition> definedFormats,
      Consumer<String> errorHandler,
      ConstantRetriever constants) {
    final NameDefinitions defs =
        clauses()
            .stream()
            .reduce(
                NameDefinitions.root(
                    inputFormatDefinition, constants.get(true), signatureDefinitions.get()),
                (d, clause) ->
                    clause.resolve(
                        inputFormatDefinition,
                        definedFormats,
                        d,
                        signatureDefinitions,
                        constants,
                        errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    return defs.isGood()
        & labels.stream().filter(argument -> argument.resolve(defs, errorHandler)).count()
            == labels.size()
        & annotations.stream().filter(argument -> argument.resolve(defs, errorHandler)).count()
            == annotations.size()
        & ttl.resolve(defs, errorHandler);
  }

  @Override
  protected boolean resolveDefinitionsExtra(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Consumer<String> errorHandler) {
    boolean ok =
        labels.stream().filter(arg -> arg.resolveFunctions(definedFunctions, errorHandler)).count()
                == labels.size()
            & annotations
                    .stream()
                    .filter(arg -> arg.resolveFunctions(definedFunctions, errorHandler))
                    .count()
                == annotations.size()
            & ttl.resolveFunctions(definedFunctions, errorHandler);

    final Map<String, Long> argumentNames =
        Stream.concat(labels.stream(), annotations.stream())
            .flatMap(OliveArgumentNode::targets)
            .map(Target::name)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    for (final Map.Entry<String, Long> argumentName : argumentNames.entrySet()) {
      if (argumentName.getValue() == 1) {
        continue;
      }
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate arguments %s to alert.", line, column, argumentName.getKey()));
      ok = false;
    }
    if (labels
        .stream()
        .flatMap(OliveArgumentNode::targets)
        .noneMatch(l -> l.name().equals("alertname"))) {
      errorHandler.accept(
          String.format("%d:%d: Alert should have an “alertname” label.", line, column));
    }
    return ok;
  }

  @Override
  public boolean resolveTypes(
      Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    boolean ok =
        labels.stream().filter(new ArgumentCheckAndDefiner(errorHandler)).count() == labels.size()
            & annotations.stream().filter(new ArgumentCheckAndDefiner(errorHandler)).count()
                == annotations.size()
            & ttl.typeCheck(errorHandler);
    if (ok && !ttl.type().isSame(Imyhat.INTEGER)) {
      ttl.typeError(Imyhat.INTEGER.name(), ttl.type(), errorHandler);
      ok = false;
    }
    return ok;
  }
}
