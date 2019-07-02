package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public final class OliveClauseNodeLeftJoin extends OliveClauseNode {

  private final List<GroupNode> children;
  protected final int column;
  private List<Target> discriminators;
  private final String format;
  private InputFormatDefinition inputFormat;
  private final List<Consumer<JoinBuilder>> joins = new ArrayList<>();
  protected final int line;
  private final ExpressionNode outerKey;
  private final ExpressionNode innerKey;

  public OliveClauseNodeLeftJoin(
      int line,
      int column,
      String format,
      ExpressionNode outerKey,
      ExpressionNode innerKey,
      List<GroupNode> children) {
    this.line = line;
    this.column = column;
    this.format = format;
    this.outerKey = outerKey;
    this.innerKey = innerKey;
    this.children = children;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    outerKey.collectPlugins(pluginFileNames);
    innerKey.collectPlugins(pluginFileNames);
    children.forEach(child -> child.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> joinedNames =
        inputFormat.baseStreamVariables().map(Target::name).collect(Collectors.toSet());
    return Stream.of(
        new OliveClauseRow(
            "LeftJoin",
            line,
            column,
            true,
            true,
            Stream.concat(
                children
                    .stream()
                    .map(
                        child -> {
                          final Set<String> inputs = new TreeSet<>();
                          child.collectFreeVariables(inputs, Flavour::isStream);
                          inputs.removeAll(joinedNames);
                          return new VariableInformation(
                              child.name(), child.type(), inputs.stream(), Behaviour.DEFINITION);
                        }),
                discriminators
                    .stream()
                    .map(
                        discriminator ->
                            new VariableInformation(
                                discriminator.name(),
                                discriminator.type(),
                                Stream.of(discriminator.name()),
                                Behaviour.PASSTHROUGH)))));
  }

  @Override
  public final ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE) {
      children
          .stream()
          .forEach(c -> c.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals));
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
      Map<String, OliveDefineBuilder> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    children
        .stream()
        .forEach(group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));
    outerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    innerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);

    oliveBuilder.line(line);
    final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin =
        oliveBuilder.leftJoin(
            line,
            column,
            inputFormat,
            outerKey.type(),
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));
    joins.forEach(a -> a.accept(leftJoin.first()));

    leftJoin.first().outerKey().methodGen().visitCode();
    outerKey.render(leftJoin.first().outerKey());
    leftJoin.first().outerKey().methodGen().returnValue();
    leftJoin.first().outerKey().methodGen().visitMaxs(0, 0);
    leftJoin.first().outerKey().methodGen().visitEnd();

    leftJoin.first().innerKey().methodGen().visitCode();
    innerKey.render(leftJoin.first().innerKey());
    leftJoin.first().innerKey().methodGen().returnValue();
    leftJoin.first().innerKey().methodGen().visitMaxs(0, 0);
    leftJoin.first().innerKey().methodGen().visitEnd();

    leftJoin.first().finish();

    discriminators.forEach(
        discriminator -> {
          leftJoin
              .second()
              .addKey(
                  discriminator.type().apply(TypeUtils.TO_ASM),
                  discriminator.name(),
                  context -> {
                    context.loadStream();
                    context
                        .methodGen()
                        .invokeVirtual(
                            context.streamType(),
                            new Method(
                                discriminator.name(),
                                discriminator.type().apply(TypeUtils.TO_ASM),
                                new Type[] {}));
                  });
        });
    children.stream().forEach(group -> group.render(leftJoin.second(), builder));

    leftJoin.second().finish();

    oliveBuilder.measureFlow(builder.sourcePath(), line, column);
  }

  @Override
  public final NameDefinitions resolve(
      InputFormatDefinition inputFormatDefinition,
      Function<String, InputFormatDefinition> definedFormats,
      NameDefinitions defs,
      Supplier<Stream<SignatureDefinition>> signatureDefinitions,
      ConstantRetriever constants,
      Consumer<String> errorHandler) {
    inputFormat = definedFormats.apply(format);
    if (inputFormat == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown input format “%s” in LeftJoin.", line, column, format));
      return defs.fail(false);
    }

    final Set<String> newNames =
        inputFormat.baseStreamVariables().map(Target::name).collect(Collectors.toSet());

    final List<String> duplicates =
        defs.stream()
            .filter(n -> n.flavour().isStream() && newNames.contains(n.name()))
            .map(Target::name)
            .sorted()
            .collect(Collectors.toList());

    if (duplicates.isEmpty()) {
      defs.stream()
          .filter(n -> n.flavour().isStream())
          .forEach(n -> joins.add(jb -> jb.add(n, true)));
      inputFormat.baseStreamVariables().forEach(n -> joins.add(jb -> jb.add(n, false)));
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate variables on both sides of LeftJoin. Please rename or drop the following using a Let: %s",
              line, column, String.join(", ", duplicates)));
      return defs.fail(false);
    }

    discriminators =
        defs.stream()
            .filter(t -> t.flavour().isStream() && t.flavour() != Flavour.STREAM_SIGNATURE)
            .collect(Collectors.toList());

    final NameDefinitions joinedDefs =
        defs.replaceStream(
            Stream.concat(
                discriminators.stream(), inputFormat.baseStreamVariables().map(Target::wrap)),
            true);

    final boolean ok =
        children
                    .stream()
                    .filter(
                        group -> {
                          final boolean isDuplicate =
                              discriminators.stream().anyMatch(t -> t.name().equals(group.name()));
                          if (isDuplicate) {
                            errorHandler.accept(
                                String.format(
                                    "%d:%d: Redefinition of variable “%s”.",
                                    group.line(), group.column(), group.name()));
                          }
                          return group.resolve(joinedDefs, defs, errorHandler) && !isDuplicate;
                        })
                    .count()
                == children.size()
            & outerKey.resolve(defs, errorHandler)
            & innerKey.resolve(
                defs.replaceStream(inputFormat.baseStreamVariables().map(x -> x), true),
                errorHandler);

    return defs.replaceStream(
        Stream.concat(discriminators.stream().map(Target::wrap), children.stream()), ok);
  }

  @Override
  public final boolean resolveDefinitions(
      Map<String, OliveNodeDefinition> definedOlives,
      Function<String, FunctionDefinition> definedFunctions,
      Function<String, ActionDefinition> definedActions,
      Set<String> metricNames,
      Map<String, List<Imyhat>> dumpers,
      Consumer<String> errorHandler) {
    boolean ok =
        children
                .stream()
                .filter(
                    group ->
                        group.resolveDefinitions(
                            definedOlives, definedFunctions, definedActions, errorHandler))
                .count()
            == children.size();
    if (children.stream().map(GroupNode::name).distinct().count() != children.size()) {
      ok = false;
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate collected variables in “LeftJoin” clause. Should be: %s",
              line,
              column,
              children
                  .stream()
                  .map(GroupNode::name)
                  .sorted()
                  .distinct()
                  .collect(Collectors.joining(", "))));
    }
    return ok
        & outerKey.resolveFunctions(definedFunctions, errorHandler)
        & innerKey.resolveFunctions(definedFunctions, errorHandler);
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = outerKey.typeCheck(errorHandler) & innerKey.typeCheck(errorHandler);
    if (ok && !outerKey.type().isSame(innerKey.type())) {
      innerKey.typeError(outerKey.type().name(), innerKey.type(), errorHandler);
      ok = false;
    }
    return ok
        & children.stream().filter(group -> group.typeCheck(errorHandler)).count()
            == children.size();
  }
}
