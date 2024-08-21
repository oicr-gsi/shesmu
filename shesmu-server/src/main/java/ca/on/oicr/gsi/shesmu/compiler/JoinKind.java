package ca.on.oicr.gsi.shesmu.compiler;

import java.util.*;
import java.util.function.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public enum JoinKind {
  DIRECT(false) {},
  INTERSECTION(true) {},
  INTERSECTION_LIFT_INNER(true) {
    @Override
    public void renderInnerKeyWrapper(Renderer renderer) {
      liftFunction(renderer);
    }
  },
  INTERSECTION_LIFT_OUTER(true) {
    public void renderOuterKeyWrapper(Renderer renderer) {
      liftFunction(renderer);
    }
  };

  private static void liftFunction(Renderer renderer) {
    LambdaBuilder.pushStatic(
        renderer, A_SET_TYPE, "of", LambdaBuilder.function(A_SET_TYPE, A_OBJECT_TYPE), true);
    renderer.methodGen().invokeInterface(A_FUNCTION_TYPE, METHOD_FUNCTION__AND_THEN);
  }

  private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Method METHOD_FUNCTION__AND_THEN =
      new Method("andThen", A_FUNCTION_TYPE, new Type[] {A_FUNCTION_TYPE});

  private final boolean intersection;

  private JoinKind(boolean intersection) {
    this.intersection = intersection;
  }

  public final boolean intersection() {
    return intersection;
  }

  public void renderInnerKeyWrapper(Renderer renderer) {
    // Do nothing.
  }

  public void renderOuterKeyWrapper(Renderer renderer) {
    // Do nothing.
  }
}
