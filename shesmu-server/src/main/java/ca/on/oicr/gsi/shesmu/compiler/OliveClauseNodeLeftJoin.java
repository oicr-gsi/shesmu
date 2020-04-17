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
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
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
  private final Optional<ExpressionNode> where;

  public OliveClauseNodeLeftJoin(
      int line,
      int column,
      String format,
      ExpressionNode outerKey,
      ExpressionNode innerKey,
      List<GroupNode> children,
      Optional<ExpressionNode> where) {
    this.line = line;
    this.column = column;
    this.format = format;
    this.outerKey = outerKey;
    this.innerKey = innerKey;
    this.children = children;
    this.where = where;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    boolean ok = true;
    for (final GroupNode child : children) {
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
    outerKey.collectPlugins(pluginFileNames);
    innerKey.collectPlugins(pluginFileNames);
    children.forEach(child -> child.collectPlugins(pluginFileNames));
    where.ifPresent(w -> w.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    final Set<String> joinedNames =
        inputFormat.baseStreamVariables().map(Target::name).collect(Collectors.toSet());
    final Set<String> whereInputs = new TreeSet<>();
    where.ifPresent(w -> w.collectFreeVariables(whereInputs, Flavour::isStream));
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
                          final Set<String> inputs = new TreeSet<>(whereInputs);
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
      // This should be impossible since a pure stream would have duplicate signatures from both
      // sides and fail.
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
    final String prefix = String.format("LeftJoin %d:%d To %s ", line, column, inputFormat.name());
    final Set<String> freeVariables = new HashSet<>();
    children.forEach(group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));
    outerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    innerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    where.ifPresent(w -> w.collectFreeVariables(freeVariables, Flavour::needsCapture));
    final Set<String> innerSignatures = new HashSet<>();
    children.forEach(
        group -> group.collectFreeVariables(innerSignatures, Flavour.STREAM_SIGNATURE::equals));
    innerKey.collectFreeVariables(innerSignatures, Flavour.STREAM_SIGNATURE::equals);
    final Set<String> innerSignables = new HashSet<>();
    children.forEach(
        group -> group.collectFreeVariables(innerSignables, Flavour.STREAM_SIGNABLE::equals));
    innerKey.collectFreeVariables(innerSignables, Flavour.STREAM_SIGNABLE::equals);
    final List<Target> signables =
        inputFormat
            .baseStreamVariables()
            .filter(input -> innerSignables.contains(input.name()))
            .collect(Collectors.toList());

    builder
        .signatureVariables()
        .filter(signature -> innerSignatures.contains(signature.name()))
        .forEach(
            signatureDefinition ->
                oliveBuilder.createSignature(prefix, inputFormat, signables, signatureDefinition));

    oliveBuilder.line(line);
    final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin =
        oliveBuilder.leftJoin(
            line,
            column,
            inputFormat,
            outerKey.type(),
            (signatureDefinition, renderer) -> {
              oliveBuilder.renderSigner(prefix, signatureDefinition, renderer);
            },
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

    final Regrouper regrouper =
        where.map(w -> leftJoin.second().addWhere(w::render)).orElse(leftJoin.second());
    children.forEach(group -> group.render(regrouper, builder));

    leftJoin.second().finish();

    oliveBuilder.measureFlow(builder.sourcePath(), line, column);
  }

  @Override
  public final NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    inputFormat = oliveCompilerServices.inputFormat(format);
    if (inputFormat == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown input format “%s” in LeftJoin.", line, column, format));
      return defs.fail(false);
    }

    final Set<String> newNames =
        Stream.concat(inputFormat.baseStreamVariables(), oliveCompilerServices.signatures())
            .map(Target::name)
            .collect(Collectors.toSet());

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
            Stream.of(
                    discriminators.stream(),
                    inputFormat.baseStreamVariables().map(Target::softWrap),
                    oliveCompilerServices.signatures())
                .flatMap(Function.identity()),
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
                errorHandler)
            & where.map(w -> w.resolve(joinedDefs, errorHandler)).orElse(true);

    return defs.replaceStream(
        Stream.concat(discriminators.stream().map(Target::wrap), children.stream()), ok);
  }

  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    boolean ok =
        children
                .stream()
                .filter(group -> group.resolveDefinitions(oliveCompilerServices, errorHandler))
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
        & outerKey.resolveDefinitions(oliveCompilerServices, errorHandler)
        & innerKey.resolveDefinitions(oliveCompilerServices, errorHandler)
        & where.map(w -> w.resolveDefinitions(oliveCompilerServices, errorHandler)).orElse(true);
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = outerKey.typeCheck(errorHandler) & innerKey.typeCheck(errorHandler);
    if (ok && !outerKey.type().isSame(innerKey.type())) {
      innerKey.typeError(outerKey.type(), innerKey.type(), errorHandler);
      ok = false;
    }
    return ok
        & children.stream().filter(group -> group.typeCheck(errorHandler)).count()
            == children.size()
        & where
            .map(
                w -> {
                  boolean whereOk = w.typeCheck(errorHandler);
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
