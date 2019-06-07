package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DiscriminatorNodeSimple extends DiscriminatorNode {

  private final String name;

  private Target target = Target.BAD;

  public DiscriminatorNodeSimple(int line, int column, String name) {
    super(line, column);
    this.name = name;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    // Nothing to do.

  }

  @Override
  public VariableInformation dashboard() {
    return new VariableInformation(name, target.type(), Stream.of(name), Behaviour.DEFINITION_BY);
  }

  @Override
  public Flavour flavour() {
    return Flavour.STREAM;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public void render(RegroupVariablesBuilder builder) {
    builder.addKey(
        target.type().apply(TypeUtils.TO_ASM),
        name,
        context -> {
          context.loadStream();
          if (target instanceof InputVariable) {
            ((InputVariable) target).extract(context.methodGen());
          } else {
            context
                .methodGen()
                .invokeVirtual(
                    context.streamType(),
                    new Method(name, target.type().apply(TypeUtils.TO_ASM), new Type[] {}));
          }
        });
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<Target> target = defs.get(name);
    if (!target.isPresent()) {
      errorHandler.accept(
          String.format("%d:%d: Undefined variable “%s” in “By”.", line(), column(), name));
      return false;
    }
    if (!target.map(Target::flavour).map(Flavour::isStream).orElse(false)) {
      errorHandler.accept(
          String.format("%d:%d: Non-stream variable “%s” in “By”.", line(), column(), name));
      return false;
    }
    this.target = target.get();
    return true;
  }

  @Override
  public boolean resolveFunctions(
      Function<String, FunctionDefinition> definedFunctions, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat type() {
    return target.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
