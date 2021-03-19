package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WizardMatchBranchNodeObject extends WizardMatchBranchNode {

  private final List<Pair<String, DestructuredArgumentNode>> fields;

  public WizardMatchBranchNodeObject(
      int line,
      int column,
      String name,
      WizardNode value,
      List<Pair<String, DestructuredArgumentNode>> fields) {
    super(line, column, name, value);
    this.fields = fields;
    for (final var field : fields) {
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
  protected Stream<EcmaLoadableValue> loadBoundNames(String base) {
    return fields.stream().flatMap(f -> f.second().renderEcma(base + ".contents." + f.first()));
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    return fields.stream()
                .filter(f -> f.second().checkWildcard(errorHandler) != WildcardCheck.BAD)
                .count()
            == fields.size()
        && new DestructuredArgumentNodeObject(line(), column(), fields)
            .typeCheck(argumentType, errorHandler);
  }
}
