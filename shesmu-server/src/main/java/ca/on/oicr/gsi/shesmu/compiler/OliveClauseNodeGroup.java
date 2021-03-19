package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OliveClauseNodeGroup extends OliveClauseNode {

  /**
   * Check that the list of strings provided are valid discriminators
   *
   * <p>That is, they are defined stream variables
   */
  public static Optional<List<Target>> checkDiscriminators(
      int line,
      int column,
      NameDefinitions defs,
      List<String> discriminators,
      Consumer<String> errorHandler) {
    final var discriminatorVariables =
        discriminators.stream()
            .map(
                name -> {
                  final var target = defs.get(name);
                  if (target.isEmpty()) {
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Undefined variable “%s” in “By”.", line, column, name));
                    return null;
                  }
                  if (!target.map(Target::flavour).map(Flavour::isStream).orElse(false)) {
                    errorHandler.accept(
                        String.format(
                            "%d:%d: Non-stream variable “%s” in “By”.", line, column, name));
                    return null;
                  }
                  target.ifPresent(Target::read);
                  return target.orElse(null);
                })
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

    if (discriminators.size() != discriminatorVariables.size()) {
      return Optional.empty();
    }
    return Optional.of(discriminatorVariables);
  }

  private final List<GroupNode> children;
  protected final int column;
  private final List<DiscriminatorNode> discriminators;
  private final Optional<String> label;
  protected final int line;
  private final Optional<ExpressionNode> where;

  public OliveClauseNodeGroup(
      Optional<String> label,
      int line,
      int column,
      List<GroupNode> children,
      List<DiscriminatorNode> discriminators,
      Optional<ExpressionNode> where) {
    this.label = label;
    this.line = line;
    this.column = column;
    this.children = children;
    this.discriminators = discriminators;
    this.where = where;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    var ok = true;
    for (final var child : children) {
      if (!child.isRead()) {
        ok = false;
        errorHandler.accept(
            String.format(
                "%d:%d: Collected result “%s” is never used.",
                child.line(), child.column(), child.name()));
      }
    }
    return ok;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    discriminators.forEach(discriminator -> discriminator.collectPlugins(pluginFileNames));
    children.forEach(child -> child.collectPlugins(pluginFileNames));
    where.ifPresent(w -> w.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> whereInputs = new TreeSet<>();
    where.ifPresent(w -> w.collectFreeVariables(whereInputs, Flavour::isStream));
    return Stream.of(
        new OliveClauseRow(
            label.orElse("Group"),
            line,
            column,
            true,
            true,
            Stream.concat(
                children.stream()
                    .map(
                        child -> {
                          final Set<String> inputs = new TreeSet<>(whereInputs);
                          child.collectFreeVariables(inputs, Flavour::isStream);
                          return new VariableInformation(
                              child.name(), child.type(), inputs.stream(), Behaviour.DEFINITION);
                        }),
                discriminators.stream().flatMap(DiscriminatorNode::dashboard))));
  }

  @Override
  public final ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
      discriminators.forEach(
          d -> d.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      children.forEach(c -> c.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      where.ifPresent(w -> w.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
      return ClauseStreamOrder.TRANSFORMED;
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
    children.forEach(group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));
    discriminators.forEach(
        group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));

    oliveBuilder.line(line);
    final var regroup =
        oliveBuilder.regroup(
            line,
            column,
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));

    discriminators.forEach(d -> d.render(regroup));
    final var regrouperForChildren = where.map(w -> regroup.addWhere(w::render)).orElse(regroup);
    children.forEach(group -> group.render(regrouperForChildren, builder));
    regroup.finish();

    oliveBuilder.measureFlow(line, column);
  }

  @Override
  public final NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    var ok =
        children.stream().filter(child -> child.resolve(defs, defs, errorHandler)).count()
                == children.size()
            & discriminators.stream()
                    .filter(discriminator -> discriminator.resolve(defs, errorHandler))
                    .count()
                == discriminators.size();

    ok =
        ok
            && children.stream()
                .noneMatch(
                    group -> {
                      final var isDuplicate =
                          defs.get(group.name())
                              .filter(variable -> !variable.flavour().isStream())
                              .isPresent();
                      if (isDuplicate) {
                        errorHandler.accept(
                            String.format(
                                "%d:%d: Redefinition of variable “%s”.",
                                group.line(), group.column(), group.name()));
                      }
                      return isDuplicate;
                    });

    ok =
        ok
            && Stream.concat(
                            discriminators.stream().flatMap(DiscriminatorNode::targets),
                            children.stream())
                        .collect(Collectors.groupingBy(DefinedTarget::name)).entrySet().stream()
                        .filter(e -> e.getValue().size() > 1)
                        .peek(
                            e ->
                                errorHandler.accept(
                                    String.format(
                                        "%d:%d: “Group” has duplicate name %s: %s",
                                        line,
                                        column,
                                        e.getKey(),
                                        e.getValue().stream()
                                            .sorted(
                                                Comparator.comparingInt(DefinedTarget::line)
                                                    .thenComparingInt(DefinedTarget::column))
                                            .map(l -> String.format("%d:%d", l.line(), l.column()))
                                            .collect(Collectors.joining(", ")))))
                        .count()
                    == 0
                & where.map(w -> w.resolve(defs, errorHandler)).orElse(true);
    return defs.replaceStream(
        Stream.concat(
            discriminators.stream().flatMap(DiscriminatorNode::targets), children.stream()),
        ok);
  }

  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {

    return children.stream()
                .filter(group -> group.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == children.size()
        & discriminators.stream()
                .filter(group -> group.resolveDefinitions(oliveCompilerServices, errorHandler))
                .count()
            == discriminators.size()
        & where.map(w -> w.resolveDefinitions(oliveCompilerServices, errorHandler)).orElse(true);
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    return children.stream().filter(group -> group.typeCheck(errorHandler)).count()
            == children.size()
        && discriminators.stream()
                    .filter(discriminator -> discriminator.typeCheck(errorHandler))
                    .count()
                == discriminators.size()
            & where
                .map(
                    w -> {
                      var whereOk = w.typeCheck(errorHandler);
                      if (whereOk) {
                        if (!w.type().isSame(Imyhat.BOOLEAN)) {
                          w.typeError(Imyhat.BOOLEAN, w.type(), errorHandler);
                          whereOk = false;
                        }
                      }
                      return whereOk;
                    })
                .orElse(true);
  }
}
