package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public abstract class InformationNodeBaseRepeat extends InformationNode {
  private final DestructuredArgumentNode name;
  private final SourceNode source;
  private final List<ListNode> transforms;

  public InformationNodeBaseRepeat(
      DestructuredArgumentNode name, SourceNode source, List<ListNode> transforms) {
    this.name = name;
    this.source = source;
    this.transforms = transforms;
    name.setFlavour(Flavour.LAMBDA);
  }

  protected abstract String renderBlock(EcmaScriptRenderer renderer, String data);

  @Override
  public final String renderEcma(EcmaScriptRenderer renderer) {
    final var builder = source.render(renderer);
    EcmaLoadableConstructor currentName = name::renderEcma;
    for (final var transform : transforms) {
      currentName = transform.render(builder, currentName);
    }
    builder.map(currentName, Imyhat.BAD, this::renderRow);

    return renderBlock(renderer, builder.finish());
  }

  public abstract String renderRow(EcmaScriptRenderer renderer);

  @Override
  public final boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    var ok = source.resolve(defs, errorHandler);

    final var nextName =
        transforms.stream()
            .reduce(
                Optional.of(name),
                (n, t) -> n.flatMap(name -> t.resolve(name, defs, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });

    ok =
        ok
            && nextName
                .map(
                    name -> {
                      final var collectorName = defs.replaceStream(name.targets(), true);
                      return resolveTerminal(collectorName, errorHandler);
                    })
                .orElse(false);
    return ok;
  }

  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return source.resolveDefinitions(expressionCompilerServices, errorHandler)
        & resolveTerminalDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler)
        & transforms.stream()
                .filter(t -> t.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == transforms.size()
        & name.resolve(expressionCompilerServices, errorHandler)
        & name.checkWildcard(errorHandler) != WildcardCheck.BAD;
  }

  protected abstract boolean resolveTerminal(
      NameDefinitions collectorName, Consumer<String> errorHandler);

  protected abstract boolean resolveTerminalDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler);

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (!source.typeCheck(errorHandler) || !name.typeCheck(source.streamType(), errorHandler)) {
      return false;
    }
    final var ordering =
        transforms.stream()
            .reduce(
                source.ordering(),
                (order, transform) -> transform.order(order, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    final var resultType =
        transforms.stream()
            .reduce(
                Optional.of(source.streamType()),
                (t, transform) -> t.flatMap(tt -> transform.typeCheck(tt, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    return resultType.isPresent() && typeCheckTerminal(errorHandler) && ordering != Ordering.BAD;
  }

  protected abstract boolean typeCheckTerminal(Consumer<String> errorHandler);
}
