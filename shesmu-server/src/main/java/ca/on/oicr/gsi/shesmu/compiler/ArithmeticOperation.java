package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public abstract class ArithmeticOperation {
  public static ArithmeticOperation primitiveMath(Imyhat type, int opcode) {
    return new ArithmeticOperation(type, type, type) {

      @Override
      public void render(
          Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

        leftValue.accept(renderer);
        rightValue.accept(renderer);
        renderer.methodGen().math(opcode, type.apply(TypeUtils.TO_ASM));
      }
    };
  }

  public static ArithmeticOperation shortCircuit(int condition) {
    return new ArithmeticOperation(Imyhat.BOOLEAN, Imyhat.BOOLEAN, Imyhat.BOOLEAN) {

      @Override
      public void render(
          Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {
        leftValue.accept(renderer);
        final Label end = renderer.methodGen().newLabel();
        renderer.methodGen().dup();
        renderer.methodGen().ifZCmp(condition, end);
        renderer.methodGen().pop();
        rightValue.accept(renderer);
        renderer.methodGen().mark(end);
      }
    };
  }

  public static ArithmeticOperation staticMethod(
      Imyhat leftType, Imyhat rightType, Imyhat returnType, Type owner, String methodName) {
    return new ArithmeticOperation(leftType, rightType, returnType) {

      @Override
      public void render(
          Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

        leftValue.accept(renderer);
        rightValue.accept(renderer);
        renderer
            .methodGen()
            .invokeStatic(
                owner,
                new Method(
                    methodName,
                    returnType.apply(TypeUtils.TO_ASM),
                    new Type[] {
                      leftType.apply(TypeUtils.TO_ASM), rightType.apply(TypeUtils.TO_ASM)
                    }));
      }
    };
  }

  public static ArithmeticOperation virtualMethod(
      Imyhat leftType, Imyhat rightType, Imyhat returnType, String methodName) {
    final Method method =
        new Method(
            methodName,
            returnType.apply(TypeUtils.TO_ASM),
            new Type[] {rightType.apply(TypeUtils.TO_ASM)});
    return new ArithmeticOperation(leftType, rightType, returnType) {

      @Override
      public void render(
          Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

        leftValue.accept(renderer);
        rightValue.accept(renderer);
        if (leftType.javaType().isInterface()) {
          renderer.methodGen().invokeInterface(leftType.apply(TypeUtils.TO_ASM), method);
        } else {
          renderer.methodGen().invokeVirtual(leftType.apply(TypeUtils.TO_ASM), method);
        }
      }
    };
  }

  public static final ArithmeticOperation BAD =
      new ArithmeticOperation(Imyhat.BAD, Imyhat.BAD, Imyhat.BAD) {

        @Override
        public void render(
            Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {
          throw new UnsupportedOperationException();
        }
      };
  private final Imyhat leftType;

  private final Imyhat returnType;

  private final Imyhat rightType;

  public ArithmeticOperation(Imyhat leftType, Imyhat rightType, Imyhat returnType) {
    super();
    this.leftType = leftType;
    this.rightType = rightType;
    this.returnType = returnType;
  }

  public Imyhat leftType() {
    return leftType;
  }

  public abstract void render(
      Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue);

  public Imyhat returnType() {
    return returnType;
  }

  public Imyhat rightType() {
    return rightType;
  }
}
