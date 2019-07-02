package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ListNodeFlatten extends ListNode {

  private final String childName;

  private Imyhat incoming;

  private String incomingName;

  private String nextName;
  private Ordering ordering;

  private final SourceNode source;

  private final List<ListNode> transforms;

  private Imyhat type;

  public ListNodeFlatten(
      int line, int column, String childName, SourceNode source, List<ListNode> transforms) {
    super(line, column);
    ordering = source.ordering();
    this.childName = childName;
    nextName = childName;
    this.source = source;
    this.transforms = transforms;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    source.collectFreeVariables(names, predicate);
    transforms.forEach(t -> t.collectFreeVariables(names, predicate));
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    source.collectPlugins(pluginFileNames);
    transforms.forEach(t -> t.collectPlugins(pluginFileNames));
  }

  @Override
  public String name() {
    return nextName;
  }

  @Override
  public final String nextName() {
    return nextName;
  }

  @Override
  public final Imyhat nextType() {
    return type;
  }

  @Override
  public final Ordering order(Ordering previous, Consumer<String> errorHandler) {
    if (previous == Ordering.BAD || ordering == Ordering.BAD) {
      return Ordering.BAD;
    }
    if (previous == Ordering.REQESTED && ordering == Ordering.REQESTED) {
      return Ordering.REQESTED;
    }
    return Ordering.RANDOM;
  }

  @Override
  public void render(JavaStreamBuilder builder) {
    final Set<String> freeVariables = new HashSet<>();
    collectFreeVariables(freeVariables, Flavour::needsCapture);
    final Renderer renderer =
        builder.flatten(
            line(),
            column(),
            incomingName,
            type,
            builder
                .renderer()
                .allValues()
                .filter(v -> freeVariables.contains(v.name()))
                .toArray(LoadableValue[]::new));
    renderer.methodGen().visitCode();
    final JavaStreamBuilder flattenBuilder = source.render(renderer);
    transforms.forEach(t -> t.render(flattenBuilder));
    flattenBuilder.finish();
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
  }

  @Override
  public Optional<String> resolve(
      String name, NameDefinitions defs, Consumer<String> errorHandler) {
    incomingName = name;
    final NameDefinitions innerDefs =
        defs.bind(
            new Target() {

              @Override
              public Flavour flavour() {
                return Flavour.LAMBDA;
              }

              @Override
              public String name() {
                return name;
              }

              @Override
              public Imyhat type() {
                return incoming;
              }
            });
    if (!source.resolve(innerDefs, errorHandler)) {
      return Optional.empty();
    }
    final Optional<String> nextName =
        transforms
            .stream()
            .reduce(
                Optional.of(childName),
                (n, t) -> n.flatMap(innerName -> t.resolve(innerName, innerDefs, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    nextName.ifPresent(n -> this.nextName = n);
    return nextName;
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return source.resolveFunctions(definedFunctions, errorHandler)
        & transforms
                .stream()
                .filter(t -> t.resolveFunctions(definedFunctions, errorHandler))
                .count()
            == transforms.size();
  }

  @Override
  public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
    this.incoming = incoming;
    if (!source.typeCheck(errorHandler)) {
      return false;
    }
    Imyhat innerIncoming = source.streamType();
    for (final ListNode transform : transforms) {
      ordering = transform.order(ordering, errorHandler);
      if (!transform.typeCheck(innerIncoming, errorHandler)) {
        return false;
      }
      innerIncoming = transform.nextType();
    }
    type = innerIncoming;
    return true;
  }
}
