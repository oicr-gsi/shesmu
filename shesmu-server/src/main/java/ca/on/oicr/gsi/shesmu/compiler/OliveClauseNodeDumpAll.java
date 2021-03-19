package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class OliveClauseNodeDumpAll extends OliveClauseNodeBaseDump implements RejectNode {
  private List<Target> columns;

  public OliveClauseNodeDumpAll(Optional<String> label, int line, int column, String dumper) {
    super(label, line, column, dumper);
  }

  @Override
  protected Predicate<String> captureVariable() {
    return x -> false;
  }

  @Override
  public void collectFreeVariables(Set<String> freeVariables) {
    // No free variables.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // No plugins.
  }

  @Override
  protected int columnCount() {
    return columns.size();
  }

  @Override
  protected Stream<String> columnInputs(int index) {
    return Stream.of(columns.get(index).name());
  }

  @Override
  public Pair<String, Imyhat> columnDefinition(int index) {
    final var target = columns.get(index);
    return new Pair<>(target.name(), target.type());
  }

  @Override
  protected void renderColumn(int index, Renderer renderer) {
    renderer.loadTarget(columns.get(index));
  }

  @Override
  public NameDefinitions resolve(
      OliveCompilerServices oliveCompilerServices,
      NameDefinitions defs,
      Consumer<String> errorHandler) {
    columns =
        defs.stream()
            .filter(i -> i.flavour() == Flavour.STREAM || i.flavour() == Flavour.STREAM_SIGNABLE)
            .sorted(Comparator.comparing(Target::name))
            .collect(Collectors.toList());
    return defs;
  }

  @Override
  protected boolean resolveDefinitionsExtra(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  protected boolean typeCheckExtra(Consumer<String> errorHandler) {
    return true;
  }
}
