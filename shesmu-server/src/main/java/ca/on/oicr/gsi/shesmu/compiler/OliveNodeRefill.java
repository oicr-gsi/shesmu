package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.compiler.description.Produces;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OliveNodeRefill extends OliveNodeWithClauses {

  private static class ArgBuilder {
    private final ExpressionNode expression;
    private final DestructuredArgumentNode name;
    private final Map<String, RefillerParameterDefinition> parameters;

    public ArgBuilder(
        Pair<DestructuredArgumentNode, ExpressionNode> arg,
        Map<String, RefillerParameterDefinition> parameters) {
      name = arg.first();
      expression = arg.second();
      this.parameters = parameters;
    }

    Stream<RefillerParameterBuilder> render(OliveBuilder oliveBuilder) {
      final Set<String> captures = new HashSet<>();
      expression.collectFreeVariables(captures, Flavour::needsCapture);
      final LoadableValue[] capturedValues =
          oliveBuilder
              .loadableValues()
              .filter(v -> captures.contains(v.name()))
              .toArray(LoadableValue[]::new);
      return name.render(expression::render)
          .map(
              v ->
                  new RefillerParameterBuilder() {
                    @Override
                    public LoadableValue[] captures() {
                      return capturedValues;
                    }

                    @Override
                    public RefillerParameterDefinition parameter() {
                      return parameters.get(v.name());
                    }

                    @Override
                    public void render(Renderer renderer) {
                      renderer.methodGen().visitCode();
                      v.accept(renderer);
                      renderer.methodGen().returnValue();
                      renderer.methodGen().endMethod();
                    }
                  });
    }
  }

  private List<ArgBuilder> argumentBuilders;
  private final List<Pair<DestructuredArgumentNode, ExpressionNode>> arguments;
  private final int column;
  private final String refillerName;
  private RefillerDefinition definition;
  private String description;
  private final int line;
  private Set<String> tags;

  public OliveNodeRefill(
      int line,
      int column,
      String refillerName,
      List<Pair<DestructuredArgumentNode, ExpressionNode>> arguments,
      List<OliveClauseNode> clauses,
      Set<String> tags,
      String description) {
    super(clauses);
    this.line = line;
    this.column = column;
    this.refillerName = refillerName;
    this.arguments = arguments;
    this.description = description;
    this.tags = tags;
  }

  @Override
  public void build(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    // Do nothing.
  }

  @Override
  protected void collectArgumentSignableVariables() {
    arguments.forEach(
        arg -> arg.second().collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
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
    if (definition.filename() != null) {
      pluginFileNames.add(definition.filename());
    }
    arguments.forEach(arg -> arg.second().collectPlugins(pluginFileNames));
  }

  @Override
  public Stream<OliveTable> dashboard() {
    return Stream.of(
        new OliveTable(
            "Refill " + refillerName,
            line,
            column,
            Produces.REFILL,
            tags,
            description,
            clauses().stream().flatMap(OliveClauseNode::dashboard),
            arguments
                .stream()
                .flatMap(
                    arg -> {
                      final Set<String> inputs = new HashSet<>();
                      arg.second().collectFreeVariables(inputs, Flavour::isStream);
                      return arg.first()
                          .targets()
                          .map(
                              target ->
                                  new VariableInformation(
                                      target.name(),
                                      target.type(),
                                      inputs.stream(),
                                      Behaviour.DEFINITION));
                    })));
  }

  @Override
  public void processExport(ExportConsumer exportConsumer) {
    // Not exportable
  }

  @Override
  public void render(RootBuilder builder, Map<String, OliveDefineBuilder> definitions) {
    final OliveBuilder oliveBuilder = builder.buildRunOlive(line, column, signableNames);
    clauses().forEach(clause -> clause.render(builder, oliveBuilder, definitions));
    oliveBuilder.line(line);
    oliveBuilder.finish(
        refillerName,
        definition::render,
        argumentBuilders.stream().flatMap(arg -> arg.render(oliveBuilder)));
  }

  @Override
  public boolean resolve(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    final NameDefinitions defs =
        clauses()
            .stream()
            .reduce(
                NameDefinitions.root(
                    oliveCompilerServices.inputFormat(),
                    oliveCompilerServices.constants(true),
                    oliveCompilerServices.signatures()),
                (d, clause) -> clause.resolve(oliveCompilerServices, d, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    return defs.isGood()
        & arguments
                .stream()
                .filter(argument -> argument.second().resolve(defs, errorHandler))
                .count()
            == arguments.size();
  }

  @Override
  protected boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    boolean ok =
        arguments
                .stream()
                .filter(arg -> arg.second().resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == arguments.size();

    final Map<String, Long> argumentNames =
        arguments
            .stream()
            .flatMap(p -> p.first().targets())
            .collect(Collectors.groupingBy(Target::name, Collectors.counting()));
    for (final Map.Entry<String, Long> argumentName : argumentNames.entrySet()) {
      if (argumentName.getValue() > 1) {
        errorHandler.accept(
            String.format(
                "%d:%d: Duplicate argument %s to refill %s.",
                line, column, argumentName.getKey(), refillerName));
        ok = false;
      }
    }

    definition = oliveCompilerServices.refiller(refillerName);
    if (definition != null) {

      final Set<String> definedArgumentNames =
          definition
              .parameters()
              .map(RefillerParameterDefinition::name)
              .collect(Collectors.toSet());
      final Set<String> requiredArgumentNames =
          definition
              .parameters()
              .map(RefillerParameterDefinition::name)
              .collect(Collectors.toSet());
      if (!definedArgumentNames.containsAll(argumentNames.keySet())) {
        ok = false;
        final Set<String> badTerms = new HashSet<>(argumentNames.keySet());
        badTerms.removeAll(definedArgumentNames);
        errorHandler.accept(
            String.format(
                "%d:%d: Extra arguments for refill %s: %s",
                line, column, refillerName, String.join(", ", badTerms)));
      }
      if (!argumentNames.keySet().containsAll(requiredArgumentNames)) {
        ok = false;
        final Set<String> badTerms = new HashSet<>(requiredArgumentNames);
        badTerms.removeAll(argumentNames.keySet());
        errorHandler.accept(
            String.format(
                "%d:%d: Missing arguments for refill %s: %s",
                line, column, refillerName, String.join(", ", badTerms)));
      }
    } else {
      errorHandler.accept(
          String.format("%d:%d: Unknown refiller for “%s”.", line, column, refillerName));
      ok = false;
    }
    return ok;
  }

  @Override
  public boolean resolveTypes(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    boolean ok =
        arguments.stream().filter(argument -> argument.second().typeCheck(errorHandler)).count()
            == arguments.size();
    if (ok) {
      final Map<String, RefillerParameterDefinition> parameterInfo =
          definition
              .parameters()
              .collect(Collectors.toMap(RefillerParameterDefinition::name, Function.identity()));
      ok =
          arguments
                  .stream()
                  .filter(
                      argument ->
                          argument.second().typeCheck(errorHandler)
                              && argument.first().typeCheck(argument.second().type(), errorHandler))
                  .count()
              == arguments.size();
      ok &=
          arguments
                  .stream()
                  .flatMap(
                      p ->
                          p.first()
                              .targets()
                              .filter(
                                  t -> {
                                    final Imyhat requiredType = parameterInfo.get(t.name()).type();
                                    if (requiredType.isSame(t.type())) {
                                      return false;
                                    }
                                    errorHandler.accept(
                                        String.format(
                                            "%d:%d: Expected %s for %s, but got %s.",
                                            p.second().line(),
                                            p.second().column(),
                                            requiredType.name(),
                                            t.name(),
                                            t.type().name()));
                                    return true;
                                  }))
                  .count()
              == 0;
      if (ok) {
        argumentBuilders =
            arguments
                .stream()
                .map(arg -> new ArgBuilder(arg, parameterInfo))
                .collect(Collectors.toList());
      }
    }
    return ok;
  }
}
