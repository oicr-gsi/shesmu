package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class WizardNodeFor extends WizardNode {

  private final DestructuredArgumentNode name;
  private List<Target> outputs;
  private final SourceNode source;
  private final WizardNode step;
  private final ExpressionNode title;
  private final List<ListNode> transforms;

  public WizardNodeFor(
      DestructuredArgumentNode name,
      SourceNode source,
      List<ListNode> transforms,
      ExpressionNode title,
      WizardNode step) {
    this.name = name;
    this.source = source;
    this.title = title;
    this.transforms = transforms;
    this.step = step;
    name.setFlavour(Flavour.LAMBDA);
  }

  @Override
  public String renderEcma(EcmaScriptRenderer renderer) {
    final var builder = source.render(renderer);
    EcmaLoadableConstructor currentName = name::renderEcma;
    for (final var transform : transforms) {
      currentName = transform.render(builder, currentName);
    }
    builder.map(
        currentName,
        Imyhat.BAD,
        r ->
            String.format(
                "{title: %s, extra: %s}",
                title.renderEcma(r),
                outputs.stream()
                    .map(target -> target.name() + ": " + r.load(target))
                    .collect(Collectors.joining(", ", "{", "}"))));

    final var finalName = currentName;
    return String.format(
        "{information: [], then: {type: \"fork\",  processor: %s, items: %s}}",
        renderer.lambda(
            1,
            (r, a) -> {
              finalName.create(a.apply(0)).forEach(r::define);
              return step.renderEcma(r);
            }),
        builder.finish());
  }

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
                      final var collectorName = defs.bind(name);
                      outputs = name.targets().collect(Collectors.toList());
                      return step.resolve(collectorName, errorHandler)
                          & title.resolve(collectorName, errorHandler);
                    })
                .orElse(false);
    return ok;
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return step.resolveCrossReferences(references, errorHandler);
  }

  @Override
  public final boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return source.resolveDefinitions(expressionCompilerServices, errorHandler)
        & transforms.stream()
                .filter(t -> t.resolveDefinitions(expressionCompilerServices, errorHandler))
                .count()
            == transforms.size()
        & step.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler)
        & title.resolveDefinitions(expressionCompilerServices, errorHandler)
        & name.resolve(expressionCompilerServices, errorHandler)
        & name.checkWildcard(errorHandler) != WildcardCheck.BAD;
  }

  @Override
  public final boolean typeCheck(Consumer<String> errorHandler) {
    if (!source.typeCheck(errorHandler) || !name.typeCheck(source.streamType(), errorHandler)) {
      return false;
    }
    final var resultType =
        transforms.stream()
            .reduce(
                Optional.of(source.streamType()),
                (t, transform) -> t.flatMap(tt -> transform.typeCheck(tt, errorHandler)),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    final var ordering =
        transforms.stream()
            .reduce(
                source.ordering(),
                (order, transform) -> transform.order(order, errorHandler),
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    if (resultType.isPresent()
        && step.typeCheck(errorHandler)
        && title.typeCheck(errorHandler)
        && ordering != Ordering.BAD) {
      if (title.type().isSame(Imyhat.STRING)) {
        return true;
      } else {
        title.typeError(Imyhat.STRING, title.type(), errorHandler);
      }
    }
    return false;
  }
}
