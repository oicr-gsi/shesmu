package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.TupleImyhat;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class MatchBranchNodeTuple extends MatchBranchNode {

  private Imyhat argumentType;
  private final List<DestructuredArgumentNode> elements;

  public MatchBranchNodeTuple(
      int line,
      int column,
      String name,
      ExpressionNode value,
      List<DestructuredArgumentNode> elements) {
    super(line, column, name, value);
    this.elements = elements;
    for (final DestructuredArgumentNode element : elements) {
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
  protected Renderer prepare(Renderer renderer, BiConsumer<Renderer, Integer> loadElement) {
    final Renderer result = renderer.duplicate();
    for (int i = 0; i < elements.size(); i++) {
      final int index = i;
      final Type type = ((TupleImyhat) argumentType).get(index).apply(TO_ASM);
      elements
          .get(index)
          .render(
              r -> {
                loadElement.accept(r, index);
                r.methodGen().unbox(type);
              })
          .forEach(v -> result.define(v.name(), v));
    }
    return result;
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    this.argumentType = argumentType;
    return elements.stream().filter(e -> e.checkWildcard(errorHandler) != WildcardCheck.BAD).count()
            == elements.size()
        && new DestructuredArgumentNodeTuple(line(), column(), elements)
            .typeCheck(argumentType, errorHandler);
  }
}
