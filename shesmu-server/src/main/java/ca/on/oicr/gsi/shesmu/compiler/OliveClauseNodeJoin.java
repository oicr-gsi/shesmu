package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OliveClauseNodeJoin extends OliveClauseNode {

  private final int column;
  private final String format;
  private InputFormatDefinition innerInputFormat;
  private final List<Consumer<JoinBuilder>> joins = new ArrayList<>();
  private final int line;
  private final ExpressionNode outerKey;
  private final ExpressionNode innerKey;

  public OliveClauseNodeJoin(
      int line, int column, String format, ExpressionNode outerKey, ExpressionNode innerKey) {
    super();
    this.line = line;
    this.column = column;
    this.format = format;
    this.outerKey = outerKey;
    this.innerKey = innerKey;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    outerKey.collectPlugins(pluginFileNames);
    innerKey.collectPlugins(pluginFileNames);
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    return Stream.of(
        new OliveClauseRow(
            "Join",
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
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    return state == ClauseStreamOrder.PURE ? ClauseStreamOrder.TRANSFORMED : state;
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
    outerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);
    innerKey.collectFreeVariables(freeVariables, Flavour::needsCapture);

    final JoinBuilder join =
        oliveBuilder.join(
            line,
            column,
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
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    innerInputFormat = oliveCompilerServices.inputFormat(format);
    if (innerInputFormat == null) {
      errorHandler.accept(
          String.format("%d:%d: Unknown input format “%s” in Join.", line, column, format));
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
              "%d:%d: Duplicate variables on both sides of Join. Please rename or drop the following using a Let: %s",
              line, column, String.join(", ", duplicates)));
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
        duplicates.isEmpty() & ok);
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return outerKey.resolveDefinitions(oliveCompilerServices, errorHandler)
        & innerKey.resolveDefinitions(oliveCompilerServices, errorHandler);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    boolean ok = outerKey.typeCheck(errorHandler) & innerKey.typeCheck(errorHandler);
    if (ok && !outerKey.type().isSame(innerKey.type())) {
      innerKey.typeError(outerKey.type(), innerKey.type(), errorHandler);
      ok = false;
    }
    return ok;
  }
}
