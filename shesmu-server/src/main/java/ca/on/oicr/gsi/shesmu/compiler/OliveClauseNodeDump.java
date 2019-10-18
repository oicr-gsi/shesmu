package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class OliveClauseNodeDump extends OliveClauseNodeBaseDump implements RejectNode {

  private final List<ExpressionNode> columns;

  public OliveClauseNodeDump(int line, int column, String dumper, List<ExpressionNode> columns) {
    super(line, column, dumper);
    this.columns = columns;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables) {
    columns.forEach(column -> column.collectFreeVariables(freeVariables, Flavour::needsCapture));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    columns.forEach(column -> column.collectPlugins(pluginFileNames));
  }

  @Override
  protected Predicate<String> captureVariable() {
    final Set<String> freeVariables = new HashSet<>();
    columns.forEach(e -> e.collectFreeVariables(freeVariables, Flavour::needsCapture));
    return freeVariables::contains;
  }

  @Override
  protected int columnCount() {
    return columns.size();
  }

  @Override
  protected Stream<String> columnInputs(int index) {
    final Set<String> inputs = new TreeSet<>();
    columns.get(index).collectFreeVariables(inputs, Flavour::isStream);
    return inputs.stream();
  }

  @Override
  public Imyhat columnType(int index) {
    return columns.get(index).type();
  }

  @Override
  protected void renderColumn(int index, Renderer renderer) {
    columns.get(index).render(renderer);
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    return defs.fail(
        columns.stream().filter(e -> e.resolve(defs, errorHandler)).count() == columns.size());
  }

  @Override
  public boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return columns
            .stream()
            .filter(e -> e.resolveDefinitions(oliveCompilerServices, errorHandler))
            .count()
        == columns.size();
  }

  @Override
  public boolean typeCheckExtra(Consumer<String> errorHandler) {
    return (columns.stream().filter(e -> e.typeCheck(errorHandler)).count() == columns.size());
  }
}
