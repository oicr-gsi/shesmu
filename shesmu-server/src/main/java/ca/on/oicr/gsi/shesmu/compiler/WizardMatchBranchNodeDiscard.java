package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class WizardMatchBranchNodeDiscard extends WizardMatchBranchNode {

  public WizardMatchBranchNodeDiscard(int line, int column, String name, WizardNode value) {
    super(line, column, name, value);
  }

  @Override
  protected NameDefinitions bind(NameDefinitions definitions) {
    return definitions;
  }

  @Override
  protected Stream<Target> boundNames() {
    return Stream.empty();
  }

  @Override
  protected Stream<EcmaLoadableValue> loadBoundNames(String base) {
    return Stream.empty();
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    return true;
  }
}
