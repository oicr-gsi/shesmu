package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WizardMatchBranchNodeTuple extends WizardMatchBranchNode {

  private final List<DestructuredArgumentNode> elements;

  public WizardMatchBranchNodeTuple(
      int line,
      int column,
      String name,
      WizardNode value,
      List<DestructuredArgumentNode> elements) {
    super(line, column, name, value);
    this.elements = elements;
    for (final var element : elements) {
      element.setFlavour(Flavour.LAMBDA);
    }
  }

  @Override
  protected NameDefinitions bind(NameDefinitions definitions) {
    return definitions.bind(
        elements.stream().flatMap(DestructuredArgumentNode::targets).collect(Collectors.toList()));
  }

  @Override
  protected Stream<Target> boundNames() {
    return elements.stream().flatMap(DestructuredArgumentNode::targets);
  }

  @Override
  protected Stream<EcmaLoadableValue> loadBoundNames(String base) {
    return elements.stream()
        .flatMap(
            new Function<>() {
              private int index;

              @Override
              public Stream<? extends EcmaLoadableValue> apply(DestructuredArgumentNode element) {
                final var current = index++;
                return element.renderEcma(String.format("%s.contents[%d]", base, current));
              }
            });
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    return elements.stream().filter(e -> e.checkWildcard(errorHandler) != WildcardCheck.BAD).count()
            == elements.size()
        && new DestructuredArgumentNodeTuple(line(), column(), elements)
            .typeCheck(argumentType, errorHandler);
  }
}
