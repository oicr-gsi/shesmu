package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DiscriminatorNodeSimple extends DiscriminatorNode {

  private Target inputTarget = Target.BAD;

  private final DefinedTarget outputTarget;

  public DiscriminatorNodeSimple(int line, int column, String name) {
    outputTarget =
        new DefinedTarget() {
          @Override
          public int column() {
            return column;
          }

          @Override
          public Flavour flavour() {
            return Flavour.STREAM;
          }

          @Override
          public int line() {
            return line;
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public void read() {
            // We don't care if discriminators are read because they are implicitly read by
            // grouping.
          }

          @Override
          public Imyhat type() {
            return inputTarget.type();
          }
        };
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Target.Flavour> predicate) {
    // Nothing to do.

  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    // Do nothing.
  }

  @Override
  public Stream<VariableInformation> dashboard() {
    return Stream.of(
        new VariableInformation(
            outputTarget.name(),
            inputTarget.type(),
            Stream.of(outputTarget.name()),
            Behaviour.DEFINITION_BY));
  }

  @Override
  public void render(RegroupVariablesBuilder builder) {
    builder.addKey(
        inputTarget.type().apply(TypeUtils.TO_ASM),
        outputTarget.name(),
        context -> {
          context.loadStream();
          if (inputTarget instanceof InputVariable) {
            ((InputVariable) inputTarget).extract(context.methodGen());
          } else {
            context
                .methodGen()
                .invokeVirtual(
                    context.streamType(),
                    new Method(
                        outputTarget.name(),
                        inputTarget.type().apply(TypeUtils.TO_ASM),
                        new Type[] {}));
          }
        });
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<Target> target = defs.get(outputTarget.name());
    if (!target.isPresent()) {
      errorHandler.accept(
          String.format(
              "%d:%d: Undefined variable “%s” in “By”.",
              outputTarget.line(), outputTarget.column(), outputTarget.name()));
      return false;
    }
    if (!target.map(Target::flavour).map(Target.Flavour::isStream).orElse(false)) {
      errorHandler.accept(
          String.format(
              "%d:%d: Non-stream variable “%s” in “By”.",
              outputTarget.line(), outputTarget.column(), outputTarget.name()));
      return false;
    }
    this.inputTarget = target.get();
    this.inputTarget.read();
    return true;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return Stream.of(outputTarget);
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    return true;
  }
}
