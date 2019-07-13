package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ListNodeReverse extends ListNode {
  public ListNodeReverse(int line, int column) {
    super(line, column);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    if (previous == Ordering.RANDOM) {
      errorHandler.accept(
          String.format(
              "%d:%d: The list is not sorted. Reversing it is a bad idea.", line(), column()));
      return Ordering.BAD;
    }
    return previous;
  }

  @Override
  public LoadableConstructor render(JavaStreamBuilder builder, LoadableConstructor name) {
    builder.reverse();
    return name;
  }

  @Override
  public Optional<List<Target>> resolve(
      List<Target> name, NameDefinitions defs, Consumer<String> errorHandler) {
    return Optional.of(name);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Optional<Imyhat> typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    return Optional.of(incoming);
  }
}
