package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class MatchBranchNodeObject extends MatchBranchNode {

  private Imyhat argumentType;
  private final List<Pair<String, DestructuredArgumentNode>> fields;

  public MatchBranchNodeObject(
      int line,
      int column,
      String name,
      ExpressionNode value,
      List<Pair<String, DestructuredArgumentNode>> fields) {
    super(line, column, name, value);
    this.fields = fields;
    for (final Pair<String, DestructuredArgumentNode> field : fields) {
      field.second().setFlavour(Flavour.LAMBDA);
    }
  }

  @Override
  protected NameDefinitions bind(NameDefinitions definitions) {
    return definitions.bind(
        fields.stream().flatMap(f -> f.second().targets()).collect(Collectors.toList()));
  }

  @Override
  protected Stream<Target> boundNames() {
    return fields.stream().flatMap(p -> p.second().targets());
  }

  @Override
  protected Renderer prepare(Renderer renderer, BiConsumer<Renderer, Integer> loadElement) {
    final List<String> fieldNames =
        fields.stream().map(Pair::first).sorted().collect(Collectors.toList());
    final Renderer result = renderer.duplicate();
    fields
        .stream()
        .flatMap(
            f -> {
              final int i = fieldNames.indexOf(f.first());
              final Type type = ((ObjectImyhat) argumentType).get(f.first()).apply(TO_ASM);
              return f.second()
                  .render(
                      r -> {
                        loadElement.accept(r, i);
                        r.methodGen().unbox(type);
                      });
            })
        .forEach(v -> result.define(v.name(), v));
    return result;
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    this.argumentType = argumentType;
    return fields
                .stream()
                .filter(f -> f.second().checkWildcard(errorHandler) != WildcardCheck.BAD)
                .count()
            == fields.size()
        && new DestructuredArgumentNodeObject(line(), column(), fields)
            .typeCheck(argumentType, errorHandler);
  }
}
