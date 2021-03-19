package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class OliveClauseNodeDump extends OliveClauseNodeBaseDump implements RejectNode {

  private final List<Pair<Optional<String>, ExpressionNode>> columns;

  public OliveClauseNodeDump(
      Optional<String> label,
      int line,
      int column,
      String dumper,
      List<Pair<Optional<String>, ExpressionNode>> columns) {
    super(label, line, column, dumper);
    this.columns = columns;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables) {
    columns.forEach(
        column -> column.second().collectFreeVariables(freeVariables, Flavour::needsCapture));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    columns.forEach(column -> column.second().collectPlugins(pluginFileNames));
  }

  @Override
  protected Predicate<String> captureVariable() {
    final Set<String> freeVariables = new HashSet<>();
    columns.forEach(e -> e.second().collectFreeVariables(freeVariables, Flavour::needsCapture));
    return freeVariables::contains;
  }

  @Override
  protected int columnCount() {
    return columns.size();
  }

  @Override
  protected Stream<String> columnInputs(int index) {
    final Set<String> inputs = new TreeSet<>();
    columns.get(index).second().collectFreeVariables(inputs, Flavour::isStream);
    return inputs.stream();
  }

  @Override
  public Pair<String, Imyhat> columnDefinition(int index) {
    final var column = columns.get(index);
    return new Pair<>(
        column
            .first()
            .orElse(column.second().dumpColumnName().orElse(String.format("Column %d", index + 1))),
        column.second().type());
  }

  @Override
  protected void renderColumn(int index, Renderer renderer) {
    columns.get(index).second().render(renderer);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    return defs.fail(
        columns.stream().filter(e -> e.second().resolve(defs, errorHandler)).count()
            == columns.size());
  }

  @Override
  public boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return columns.stream()
            .filter(e -> e.second().resolveDefinitions(oliveCompilerServices, errorHandler))
            .count()
        == columns.size();
  }

  @Override
  public boolean typeCheckExtra(Consumer<String> errorHandler) {
    return (columns.stream().filter(e -> e.second().typeCheck(errorHandler)).count()
        == columns.size());
  }
}
