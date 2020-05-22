package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class OliveClauseNodeBaseJoin extends OliveClauseNode {

  private final int column;
  private final String format;
  private InputFormatDefinition innerInputFormat;
  private final ExpressionNode innerKey;
  private final List<Consumer<JoinBuilder>> joins = new ArrayList<>();
  private final int line;
  private final ExpressionNode outerKey;

  public OliveClauseNodeBaseJoin(
      int line, int column, String format, ExpressionNode outerKey, ExpressionNode innerKey) {
    super();
    this.line = line;
    this.column = column;
    this.format = format;
    this.outerKey = outerKey;
    this.innerKey = innerKey;
  }

  @Override
  public final boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public final void collectPlugins(Set<Path> pluginFileNames) {
    outerKey.collectPlugins(pluginFileNames);
    innerKey.collectPlugins(pluginFileNames);
  }

  @Override
  public final int column() {
    return column;
  }

  @Override
  public final Stream<OliveClauseRow> dashboard() {
    return Stream.of(
        new OliveClauseRow(
            syntax(),
            line,
            column,
            true,
            false,
            innerInputFormat
                .baseStreamVariables()
                .map(
                    variable ->
                        new VariableInformation(
                            variable.name(),
                            variable.type(),
                            Stream.empty(),
                            Behaviour.DEFINITION))));
  }

  @Override
  public final ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames,
      Consumer<SignableVariableCheck> addSignableCheck,
      Consumer<String> errorHandler) {
    return state == ClauseStreamOrder.PURE ? ClauseStreamOrder.TRANSFORMED : state;
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
    outerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    innerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);

    final JoinBuilder join =
        oliveBuilder.join(
            line,
            column,
            intersection(),
            innerInputFormat,
            outerKey.type(),
            oliveBuilder
                .loadableValues()
                .filter(value -> freeVariables.contains(value.name()))
                .toArray(LoadableValue[]::new));
    joins.forEach(a -> a.accept(join));

    join.outerKey().methodGen().visitCode();
    outerKey.render(join.outerKey());
    join.outerKey().methodGen().returnValue();
    join.outerKey().methodGen().visitMaxs(0, 0);
    join.outerKey().methodGen().visitEnd();

    join.innerKey().methodGen().visitCode();
    innerKey.render(join.innerKey());
    join.innerKey().methodGen().returnValue();
    join.innerKey().methodGen().visitMaxs(0, 0);
    join.innerKey().methodGen().visitEnd();

    join.finish();

    oliveBuilder.measureFlow(builder.sourcePath(), line, column);
  }

  @Override
  public final NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    innerInputFormat = oliveCompilerServices.inputFormat(format);
    if (innerInputFormat == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown input format “%s” in %s.", line, column, format, syntax()));
      return defs.fail(false);
    }

    final Set<String> newNames =
        innerInputFormat.baseStreamVariables().map(Target::name).collect(Collectors.toSet());

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
      innerInputFormat.baseStreamVariables().forEach(n -> joins.add(jb -> jb.add(n, false)));
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate variables on both sides of %s. Please rename or drop the following using a Let: %s",
              line, column, syntax(), String.join(", ", duplicates)));
      return defs.fail(false);
    }
    boolean ok =
        outerKey.resolve(defs, errorHandler)
            & innerKey.resolve(
                defs.replaceStream(innerInputFormat.baseStreamVariables(), true), errorHandler);
    return defs.replaceStream(
        Stream.concat(
                defs.stream().filter(n -> n.flavour().isStream()),
                innerInputFormat.baseStreamVariables())
            .map(Target::wrap),
        ok);
  }

  @Override
  public final boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return outerKey.resolveDefinitions(oliveCompilerServices, errorHandler)
        & innerKey.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  public abstract String syntax();

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = outerKey.typeCheck(errorHandler) & innerKey.typeCheck(errorHandler);
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
    return ok;
  }

  protected abstract Optional<Imyhat> typeCheckExtra(Imyhat type);
}
