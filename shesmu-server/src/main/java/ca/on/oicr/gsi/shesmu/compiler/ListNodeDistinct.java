package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ListNodeDistinct extends ListNode {
  private Imyhat incoming;

  private String name;

  public ListNodeDistinct(int line, int column) {
    super(line, column);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Do nothing.
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String nextName() {
    return name();
  }

  @Override
  public Imyhat nextType() {
    return incoming;
  }

  @Override
  public Ordering order(Ordering previous, Consumer<String> errorHandler) {
    return previous;
  }

  @Override
  public void render(JavaStreamBuilder builder) {
    builder.distinct();
  }

  @Override
  public Optional<String> resolve(
      String name, NameDefinitions defs, Consumer<String> errorHandler) {
    this.name = name;
    return Optional.of(name);
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    this.incoming = incoming;
    return true;
  }
}
