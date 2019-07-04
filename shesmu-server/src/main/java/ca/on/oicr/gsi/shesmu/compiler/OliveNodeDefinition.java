package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OliveNodeDefinition extends OliveNodeWithClauses {

  private final int column;
  private Function<String, FunctionDefinition> definedFunctions;
  private final int line;
  private final String name;
  private List<Target> outputStreamVariables;

  private final List<OliveParameter> parameters;

  private boolean resolveLock;

  public OliveNodeDefinition(
      int line,
      int column,
      String name,
      List<OliveParameter> parameters,
      List<OliveClauseNode> clauses) {
    super(clauses);
    this.line = line;
    this.column = column;
    this.name = name;
    this.parameters = parameters;
  }

  @Override
  public void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    definitions.put(name, builder.buildDefineOlive(name, parameters.stream()));
  }

  @Override
  protected void collectArgumentSignableVariables() {
    // Do nothing.
  }

  @Override
  public boolean collectDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Map<String, Target> definedConstants,
      Consumer<String> errorHandler) {
    if (definedOlives.containsKey(name)) {
      final OliveNodeDefinition other = definedOlives.get(name);
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate definition of “Define %s”. Previous entry on %d:%d.",
              line, column, name, other.line, other.column));
      return false;
    }
    definedOlives.put(name, this);
    return true;
  }

  @Override
  public void collectPluginsExtra(Set<Path> pluginFileNames) {
    // Nothing to do.
  }

  @Override
  public Stream<OliveTable> dashboard() {
    return Stream.empty();
  }

  public Stream<OliveClauseRow> dashboardInner() {
    return clauses().stream().flatMap(OliveClauseNode::dashboard);
  }

  public boolean isRoot() {
    return clauses().stream().noneMatch(OliveClauseNodeGroup.class::isInstance);
  }

  public Stream<Target> outputStreamVariables() {
    return outputStreamVariables.stream();
  }

  public Optional<Stream<Target>> outputStreamVariables(
      InputFormatDefinition inputFormatDefinition,
      Function<String, InputFormatDefinition> definedFormats,
      Consumer<String> errorHandler,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      ConstantRetriever constants) {
    if (outputStreamVariables != null
        || resolve(
            inputFormatDefinition, signatureDefinitions, definedFormats, errorHandler, constants)) {
      return Optional.of(outputStreamVariables.stream());
    }
    return Optional.empty();
  }

  public int parameterCount() {
    return parameters.size();
  }

  public Imyhat parameterType(int index) {
    return index < parameters.size() ? parameters.get(index).type() : Imyhat.BAD;
  }

  @Override
  public void processExport(ExportConsumer exportConsumer) {
    // Not exportable
  }

  @Override
  public void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    final OliveDefineBuilder oliveBuilder = definitions.get(name);
    clauses().forEach(clause -> clause.render(builder, oliveBuilder, definitions));
    oliveBuilder.finish();
  }

  @Override
  public boolean resolve(
      InputFormatDefinition inputFormatDefinition,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      Function<String, InputFormatDefinition> definedFormats,
      Consumer<String> errorHandler,
      ConstantRetriever constants) {
    if (resolveLock) {
      errorHandler.accept(
          String.format("%d:%d: Olive definition %s includes itself.", line, column, name));
      return false;
    }
    if (outputStreamVariables != null) {
      return true;
    }
    resolveLock = true;
    final NameDefinitions result =
        clauses()
            .stream()
            .reduce(
                NameDefinitions.root(
                    inputFormatDefinition,
                    Stream.concat(parameters.stream(), constants.get(true)),
                    signatureDefinitions.get()),
                (defs, clause) ->
                    clause.resolve(
                        inputFormatDefinition,
                        definedFormats,
                        defs,
                        signatureDefinitions,
                        constants,
                        errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    if (result.isGood()) {
      outputStreamVariables =
          result
              .stream()
              .filter(target -> target.flavour().isStream())
              .collect(Collectors.toList());
    }
    resolveLock = false;
    return result.isGood();
  }

  @Override
  protected boolean resolveDefinitionsExtra(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Function<String, RefillerDefinition> definedRefillers,
      Consumer<String> errorHandler) {
    this.definedFunctions = definedFunctions;
    return true;
  }

  @Override
  public boolean resolveTypes(
      Function<String, Imyhat> definedTypes, Consumer<String> errorHandler) {
    return parameters
            .stream()
            .filter(p -> p.resolveTypes(definedTypes, definedFunctions, errorHandler))
            .count()
        == parameters.size();
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    return true;
  }
}
