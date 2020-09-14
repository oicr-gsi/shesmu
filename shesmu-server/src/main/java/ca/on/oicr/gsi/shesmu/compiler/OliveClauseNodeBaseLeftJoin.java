package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public abstract class OliveClauseNodeBaseLeftJoin extends OliveClauseNode {

  private class PrefixedTarget implements Target {
    private final Target backing;

    private PrefixedTarget(Target backing) {
      this.backing = backing;
    }

    @Override
    public Flavour flavour() {
      return backing.flavour();
    }

    @Override
    public String name() {
      return variablePrefix + backing.name();
    }

    @Override
    public void read() {
      backing.read();
    }

    @Override
    public Imyhat type() {
      return backing.type();
    }
  }

  private class PrefixedVariable implements InputVariable {
    private final InputVariable backing;

    private PrefixedVariable(InputVariable backing) {
      this.backing = backing;
    }

    @Override
    public void extract(GeneratorAdapter method) {
      backing.extract(method);
    }

    @Override
    public Flavour flavour() {
      return backing.flavour();
    }

    @Override
    public String name() {
      return variablePrefix + backing.name();
    }

    @Override
    public void read() {
      backing.read();
    }

    @Override
    public Imyhat type() {
      return backing.type();
    }
  }

  private final List<GroupNode> children;
  protected final int column;
  private List<Target> discriminators;
  private final ExpressionNode innerKey;
  private List<? extends Target> innerVariables;
  private final List<Consumer<JoinBuilder>> joins = new ArrayList<>();
  protected final int line;
  private final ExpressionNode outerKey;
  private final JoinSourceNode source;
  private final String variablePrefix;
  private final Optional<ExpressionNode> where;

  public OliveClauseNodeBaseLeftJoin(
      int line,
      int column,
      JoinSourceNode source,
      ExpressionNode outerKey,
      String variablePrefix,
      ExpressionNode innerKey,
      List<GroupNode> children,
      Optional<ExpressionNode> where) {
    this.line = line;
    this.column = column;
    this.source = source;
    this.outerKey = outerKey;
    this.variablePrefix = variablePrefix;
    this.innerKey = innerKey;
    this.children = children;
    this.where = where;
  }

  @Override
  public final boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
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
  public final void collectPlugins(Set<Path> pluginFileNames) {
    outerKey.collectPlugins(pluginFileNames);
    innerKey.collectPlugins(pluginFileNames);
    source.collectPlugins(pluginFileNames);
    children.forEach(child -> child.collectPlugins(pluginFileNames));
    where.ifPresent(w -> w.collectPlugins(pluginFileNames));
  }

  @Override
  public final int column() {
    return column;
  }

  @Override
  public final Stream<OliveClauseRow> dashboard() {
    final Set<String> joinedNames =
        innerVariables
            .stream()
            .map(Target::name)
            .map(variablePrefix::concat)
            .collect(Collectors.toSet());
    final Set<String> whereInputs = new TreeSet<>();
    where.ifPresent(w -> w.collectFreeVariables(whereInputs, Flavour::isStream));
    return Stream.of(
        new OliveClauseRow(
            syntax(),
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
      ClauseStreamOrder state,
      Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE || state == ClauseStreamOrder.ALMOST_PURE) {
      outerKey.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
    }
    return state;
  }

  protected abstract boolean intersection();

  @Override
  public final int line() {
    return line;
  }

  @Override
  public final void render(
      RootBuilder builder,
      BaseOliveBuilder oliveBuilder,
      Function<String, CallableDefinitionRenderer> definitions) {
    final Set<String> freeVariables = new HashSet<>();
    children.forEach(group -> group.collectFreeVariables(freeVariables, Flavour::needsCapture));
    outerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    innerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    where.ifPresent(w -> w.collectFreeVariables(freeVariables, Flavour::needsCapture));
    final Set<String> innerSignatures = new HashSet<>();
    final Set<String> innerSignables = new HashSet<>();
    if (source.canSign()) {
      children.forEach(
          group -> group.collectFreeVariables(innerSignatures, Flavour.STREAM_SIGNATURE::equals));
      innerKey.collectFreeVariables(innerSignatures, Flavour.STREAM_SIGNATURE::equals);
      children.forEach(
          group -> group.collectFreeVariables(innerSignables, Flavour.STREAM_SIGNABLE::equals));
      innerKey.collectFreeVariables(innerSignables, Flavour.STREAM_SIGNABLE::equals);
    }

    final String prefix = String.format("LeftJoin %d:%d", line, column);
    final JoinInputSource inputSource =
        source.render(
            oliveBuilder,
            definitions,
            prefix,
            variablePrefix,
            innerSignatures::contains,
            innerSignables::contains);

    oliveBuilder.line(line);
    final Pair<JoinBuilder, RegroupVariablesBuilder> leftJoin =
        oliveBuilder.leftJoin(
            line,
            column,
            intersection(),
            inputSource,
            outerKey.type(),
            (signatureDefinition, renderer) -> {
              BaseOliveBuilder.renderSigner(
                  oliveBuilder.owner, inputSource.format(), prefix, signatureDefinition, renderer);
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
    final Stream<? extends Target> innerVariables =
        source.resolve("LeftJoin", oliveCompilerServices, defs, errorHandler);
    if (innerVariables == null) {
      return defs.fail(false);
    }
    this.innerVariables = innerVariables.collect(Collectors.toList());

    final Set<String> newNames =
        this.innerVariables
            .stream()
            .map(Target::name)
            .map(variablePrefix::concat)
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
      this.innerVariables.forEach(
          n -> joins.add(jb -> jb.add(n, variablePrefix + n.name(), false)));
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate variables on both sides of %s. Please rename or drop the following using a Let: %s",
              line, column, syntax(), String.join(", ", duplicates)));
      return defs.fail(false);
    }

    /*
     * This code uses PrefixedTarget, and PrefixedInputVariable and you might wonder why
     * three given they all just slap a prefix on the name and why are they where they are. The answer is that when
     * reading a variable, the code behaves differently for three cases. If the variable is coming from the outside
     * world, it will be of type Object and we need to do a little dance to load it since it might be from a
     * user-defined class or a tuple generated from JSON importation. Similarly, signatures know how to render
     * themselves, so we need to wrap them. Contextually, the inner key deals with the raw input data while the where
     * and collectors deal with a new class that contains the outer and inner data merged. Therefore, in that context,
     * we need a non-self loading version of the input variables.
     */
    discriminators =
        defs.stream()
            .filter(t -> t.flavour().isStream() && t.flavour() != Flavour.STREAM_SIGNATURE)
            .collect(Collectors.toList());

    final NameDefinitions joinedDefs =
        defs.replaceStream(
            Stream.concat(
                discriminators.stream(),
                this.innerVariables
                    .stream()
                    .map(
                        v ->
                            v.name().contains(Parser.NAMESPACE_SEPARATOR)
                                ? v
                                : new PrefixedTarget(v))),
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
                defs.replaceStream(
                    this.innerVariables
                        .stream()
                        .flatMap(
                            v -> {
                              if (v instanceof InputVariable) {
                                return Stream.of(new PrefixedVariable((InputVariable) v));
                              } else if (v.name().contains(Parser.NAMESPACE_SEPARATOR)) {
                                return Stream.of(v);
                              } else {
                                return Stream.of(new PrefixedTarget(v));
                              }
                            }),
                    true),
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
              "%d:%d: Duplicate collected variables in “%s” clause. Should be: %s",
              line,
              column,
              syntax(),
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
        & where.map(w -> w.resolveDefinitions(oliveCompilerServices, errorHandler)).orElse(true)
        & source.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  protected abstract String syntax();

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok =
        source.typeCheck(errorHandler)
            & outerKey.typeCheck(errorHandler)
            & innerKey.typeCheck(errorHandler);
    if (ok && !outerKey.type().isSame(innerKey.type())) {
      innerKey.typeError(outerKey.type(), innerKey.type(), errorHandler);
      ok = false;
    }
    ok =
        ok
            && typeCheckExtra(outerKey.type())
                .map(
                    requiredType -> {
                      outerKey.typeError(requiredType, outerKey.type(), errorHandler);
                      return false;
                    })
                .orElse(true);
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

  protected abstract Optional<Imyhat> typeCheckExtra(Imyhat type);
}
