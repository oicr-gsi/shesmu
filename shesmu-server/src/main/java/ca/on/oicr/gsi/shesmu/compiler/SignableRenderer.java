package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.function.BiConsumer;
import org.objectweb.asm.commons.GeneratorAdapter;

public abstract class SignableRenderer {

  protected final Target target;

  private SignableRenderer(Target target) {
    this.target = target;
  }

  public static SignableRenderer always(Target target) {
    return new SignableRenderer(target) {
      @Override
      public void render(
          GeneratorAdapter methodGen, BiConsumer<GeneratorAdapter, Target> callback) {
        callback.accept(methodGen, target);
      }
    };
  }

  public static SignableRenderer conditional(Target target, List<SignableVariableCheck> checks) {
    return new SignableRenderer(target) {
      @Override
      public void render(
          GeneratorAdapter methodGen, BiConsumer<GeneratorAdapter, Target> callback) {
        final var end = methodGen.newLabel();
        final var present = methodGen.newLabel();
        for (final var check : checks) {
          check.render(methodGen);
          methodGen.ifZCmp(GeneratorAdapter.NE, present);
        }
        methodGen.goTo(end);
        methodGen.mark(present);
        callback.accept(methodGen, target);
        methodGen.mark(end);
      }
    };
  }

  public abstract void render(
      GeneratorAdapter methodGen, BiConsumer<GeneratorAdapter, Target> callback);
}
