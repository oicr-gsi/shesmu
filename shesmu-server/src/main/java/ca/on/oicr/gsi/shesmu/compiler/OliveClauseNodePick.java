package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OliveClauseNodePick extends OliveClauseNode {

  private final int column;
  private final List<PickNode> discriminators;
  private List<Target> discriminatorVariables;
  private final ExpressionNode extractor;
  private final Optional<String> label;
  private final int line;

  private final boolean max;

  public OliveClauseNodePick(
      Optional<String> label,
      int line,
      int column,
      boolean max,
      ExpressionNode extractor,
      List<PickNode> discriminators) {
    this.label = label;
    this.line = line;
    this.column = column;
    this.max = max;
    this.extractor = extractor;
    this.discriminators = discriminators;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    extractor.collectPlugins(pluginFileNames);
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> inputs = new TreeSet<>();
    extractor.collectFreeVariables(inputs, Flavour::isStream);
    return Stream.of(
        new OliveClauseRow(
            label.orElse("Pick " + (max ? "Max" : "Min")),
            line,
            column,
            true,
            false,
            Stream.concat(
                inputs.stream()
                    .map(
                        n ->
                            new VariableInformation(
                                n, Imyhat.BOOLEAN, Stream.of(n), Behaviour.OBSERVER)),
                discriminatorVariables.stream()
                    .map(
                        discriminator ->
                            new VariableInformation(
                                discriminator.name(),
                                discriminator.type(),
                                Stream.of(discriminator.name()),
                                Behaviour.PASSTHROUGH)))));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
      discriminatorVariables.stream()
          .filter(v -> v.flavour() == Flavour.STREAM_SIGNABLE)
          .map(Target::name)
          .forEach(signableNames::add);
      extractor.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
    }
    return state;
  }

  @Override
  public int line() {
    return line;
  }

  @Override
  public void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    extractor.collectFreeVariables(freeVariables, Flavour::needsCapture);

    oliveBuilder.line(line);
    final var extractorMethod =
        oliveBuilder.pick(
            line,
            column,
            extractor.type(),
            max,
            discriminatorVariables.stream(),
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));
    extractorMethod.methodGen().visitCode();
    extractor.render(extractorMethod);
    extractorMethod.methodGen().valueOf(extractor.type().apply(TypeUtils.TO_ASM));
    extractorMethod.methodGen().returnValue();
    extractorMethod.methodGen().visitMaxs(0, 0);
    extractorMethod.methodGen().visitEnd();

    oliveBuilder.measureFlow(line, column);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    final var maybeDiscriminatorVariables =
        OliveClauseNodeGroup.checkDiscriminators(
            line,
            column,
            defs,
            discriminators.stream().flatMap(PickNode::names).collect(Collectors.toList()),
            errorHandler);
    maybeDiscriminatorVariables.ifPresent(x -> discriminatorVariables = x);
    return defs.fail(
        maybeDiscriminatorVariables.isPresent() & extractor.resolve(defs, errorHandler));
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    var ok = true;
    for (final var discriminator : discriminators) {
      ok &= discriminator.isGood(oliveCompilerServices.inputFormat(), errorHandler);
    }
    return ok & extractor.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    if (!extractor.typeCheck(errorHandler)) {
      return false;
    }
    if (extractor.type().isOrderable()) {
      return true;
    }
    errorHandler.accept(
        String.format(
            "%d:%d: Expected orderable type for sorting but got %s.",
            line, column, extractor.type().name()));
    return false;
  }
}
