package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.OliveNode.ClauseStreamOrder;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OliveClauseNodeLet extends OliveClauseNode {

  private final List<LetArgumentNode> arguments;
  private final int column;
  private final int line;

  public OliveClauseNodeLet(int line, int column, List<LetArgumentNode> arguments) {
    super();
    this.line = line;
    this.column = column;
    this.arguments = arguments;
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    arguments.forEach(arg -> arg.collectPlugins(pluginFileNames));
  }

  @Override
  public int column() {
    return column;
  }

  @Override
  public Stream<OliveClauseRow> dashboard() {
    return Stream.of(
        new OliveClauseRow(
            "Let",
            line,
            column,
            arguments.stream().anyMatch(LetArgumentNode::filters),
            true,
            arguments
                .stream()
                .flatMap(
                    arg -> {
                      final Set<String> inputs = new HashSet<>();
                      arg.collectFreeVariables(inputs, Flavour::isStream);
                      return arg.targets()
                          .map(
                              t ->
                                  new VariableInformation(
                                      t.name(), t.type(), inputs.stream(), Behaviour.DEFINITION));
                    })));
  }

  @Override
  public ClauseStreamOrder ensureRoot(
      ClauseStreamOrder state, Set<String> signableNames, Consumer<String> errorHandler) {
    if (state == ClauseStreamOrder.PURE) {
      for (LetArgumentNode a : arguments) {
        a.collectFreeVariables(signableNames, Flavour.STREAM_SIGNABLE::equals);
      }
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
    arguments.forEach(
        argument -> argument.collectFreeVariables(freeVariables, Flavour::needsCapture));
    final LetBuilder let =
        oliveBuilder.let(
            line,
            column,
            oliveBuilder
                .loadableValues()
                .filter(loadable -> freeVariables.contains(loadable.name()))
                .toArray(LoadableValue[]::new));
    arguments.forEach(argument -> argument.render(let));
    let.finish();
    if (arguments.stream().anyMatch(LetArgumentNode::filters)) {
      oliveBuilder.filterNonNull();
      oliveBuilder.measureFlow(builder.sourcePath(), line, column);
    }
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    boolean good =
        arguments.stream().filter(argument -> argument.resolve(defs, errorHandler)).count()
            == arguments.size();
    final Map<String, Long> nameCounts =
        arguments
            .stream()
            .flatMap(LetArgumentNode::targets)
            .map(Target::name)
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    for (final Map.Entry<String, Long> nameCount : nameCounts.entrySet()) {
      if (nameCount.getValue() == 1) {
        continue;
      }
      good = false;
      errorHandler.accept(
          String.format(
              "%d:%d: Duplicate variable %s in “Let” clause", line, column, nameCount.getKey()));
    }

    for (final LetArgumentNode arg : arguments) {
      good &= arg.blankCheck(errorHandler);
    }
    return defs.replaceStream(arguments.stream().flatMap(LetArgumentNode::targets), good);
  }

  @Override
  public boolean resolveDefinitions(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    boolean ok =
        arguments
                .stream()
                .filter(argument -> argument.resolveFunctions(oliveCompilerServices, errorHandler))
                .count()
            == arguments.size();

    return ok;
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return arguments.stream().filter(argument -> argument.typeCheck(errorHandler)).count()
        == arguments.size();
  }
}
