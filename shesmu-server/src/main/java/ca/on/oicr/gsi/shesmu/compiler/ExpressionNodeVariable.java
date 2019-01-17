package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.SignatureVariable;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

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
          public Imyhat type() {
            return Imyhat.BAD;
          }
        };
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    if (predicate.test(target.flavour())) {
      names.add(name);
    }
  }

  @Override
  public void render(Renderer renderer) {
    if (target.flavour() == Flavour.STREAM_SIGNATURE) {
      renderer.emitSigner((SignatureVariable) target);
    } else if (target.flavour().isStream()) {
      renderer.loadStream();
      renderer
          .methodGen()
          .invokeVirtual(
              renderer.streamType(),
              new Method(target.name(), target.type().asmType(), new Type[] {}));
    } else {
      renderer.emitNamed(name);
    }
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    final Optional<Target> result = defs.get(name);
    if (result.isPresent()) {
      target = result.get();
    } else {
      errorHandler.accept(String.format("%d:%d: Undefined variable “%s”.", line(), column(), name));
    }
    result.ifPresent(x -> target = x);
    return result.isPresent();
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
