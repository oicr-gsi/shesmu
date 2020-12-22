package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ExpressionNodeVariable extends ExpressionNode {

  private final String name;
  private Target target;

  public ExpressionNodeVariable(int line, int column, String name) {
    super(line, column);
    this.name = name;
    target =
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
          public void read() {
            // Gaze into the bad value and ignore that you read it.
          }

          @Override
          public Imyhat type() {
            return Imyhat.BAD;
          }
        };
  }

  @Override
  public Optional<String> dumpColumnName() {
    return Optional.of(name);
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    if (predicate.test(target.flavour())) {
      names.add(name);
    }
    // We also need to get an accessor when in a Define olive and we need to lift the accessor
    // along the way; in a other olives, this will just get ignored.
    if (predicate.test(Flavour.CONSTANT)) {
      names.add(BaseOliveBuilder.SIGNER_ACCESSOR_NAME);
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    if (target instanceof ConstantDefinition) {
      final ConstantDefinition constant = (ConstantDefinition) target;
      if (constant.filename() != null) {
        pluginFileNames.add(constant.filename());
      }
    } else if (target instanceof SignatureDefinition) {
      final SignatureDefinition signature = (SignatureDefinition) target;
      if (signature.filename() != null) {
        pluginFileNames.add(signature.filename());
      }
    }
    // There are many other targets that aren't from plugins, so ignore them
  }

  @Override
  public void render(Renderer renderer) {
    renderer.loadTarget(target);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<Target> result = defs.get(name);
    if (result.isPresent()) {
      target = result.get();
    } else if (defs.hasShadowName(name)) {
      errorHandler.accept(
          String.format(
              "%d:%d: Variable “%s” is locally defined, but this cannot be used with the “?” operator. Only variables outside the `` may be used. Maybe add `` in a narrower place.",
              line(), column(), name));
    } else {
      errorHandler.accept(String.format("%d:%d: Undefined variable “%s”.", line(), column(), name));
    }
    target.read();
    return result.isPresent();
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public Imyhat type() {
    return target.type();
  }

  @Override
  public boolean typeCheck(Consumer<String> errorHandler) {
    final boolean ok = !target.type().isBad();
    if (!ok) {
      errorHandler.accept(
          String.format(
              "%d:%d: Variable %s is bad but still being type checked. This is a compiler bug.",
              line(), column(), name));
    }
    return ok;
  }
}
