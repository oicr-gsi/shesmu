package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

/**
 * Describes how to perform an binary operation
 *
 * <p>The operation must be able to take exactly two arguments and know what to do based only on the
 * types of the operands.
 */
public abstract class BinaryOperation {
  /** Find a possible operation given the operand types */
  public interface Definition {
    Optional<BinaryOperation> match(Imyhat left, Imyhat right);
  }

  /**
   * Perform a static call if the operands are two lists of the same inner type; the return type is
   * the same
   *
   * @param owner the class containing the static method
   * @param method method to call taking the Imyhat of the inner type follow by the two operands
   */
  public static Definition binaryListStaticMethod(Type owner, String method) {
    return (left, right) ->
        (left.isSame(right) && (left instanceof Imyhat.ListImyhat))
            ? Optional.of(
                new BinaryOperation(left) {
                  @Override
                  public void render(
                      Renderer renderer,
                      Consumer<Renderer> leftValue,
                      Consumer<Renderer> rightValue) {
                    final Imyhat inner = ((Imyhat.ListImyhat) left).inner();
                    renderer.loadImyhat(inner.descriptor());
                    leftValue.accept(renderer);
                    rightValue.accept(renderer);
                    renderer
                        .methodGen()
                        .invokeStatic(
                            owner,
                            new Method(
                                method,
                                A_SET_TYPE,
                                new Type[] {A_IMYHAT_TYPE, A_SET_TYPE, A_SET_TYPE}));
                  }
                })
            : Optional.empty();
  }

  /**
   * Create a definition that returns the provided operation given the operand types are as provided
   *
   * @param left the type of the left operand
   * @param right the type of the right operand
   * @param operation the operation to be performed
   */
  public static Definition exact(Imyhat left, Imyhat right, BinaryOperation operation) {
    return (foundLeft, foundRight) ->
        foundLeft.isSame(left) && foundRight.isSame(right)
            ? Optional.of(operation)
            : Optional.empty();
  }

  /**
   * Perform a static call if the left operand is a list and the right operand is a value of the
   * same type as the inner type of the list; the return type is the same list type
   *
   * @param owner the class containing the static method
   * @param method method to call taking the Imyhat of the inner type follow by the two operands
   */
  public static Definition listAndItemStaticMethod(Type owner, String method) {
    return (left, right) -> {
      if (left instanceof Imyhat.ListImyhat) {
        final Imyhat inner = ((Imyhat.ListImyhat) left).inner();
        if (inner.isSame(right)) {
          return Optional.of(
              new BinaryOperation(left) {
                @Override
                public void render(
                    Renderer renderer,
                    Consumer<Renderer> leftValue,
                    Consumer<Renderer> rightValue) {
                  renderer.loadImyhat(inner.descriptor());
                  leftValue.accept(renderer);
                  rightValue.accept(renderer);
                  renderer.methodGen().valueOf(inner.apply(TO_ASM));
                  renderer
                      .methodGen()
                      .invokeStatic(
                          owner,
                          new Method(
                              method,
                              A_SET_TYPE,
                              new Type[] {A_IMYHAT_TYPE, A_SET_TYPE, A_OBJECT_TYPE}));
                }
              });
        }
      }
      return Optional.empty();
    };
  }

  /**
   * Perform a primitive math operation on two operands of the provided type; returning the same
   * type
   *
   * @param type the type of the operands
   * @param opcode the integer version of the bytecode
   */
  public static Definition primitiveMath(Imyhat type, int opcode) {
    return exact(
        type,
        type,
        new BinaryOperation(type) {

          @Override
          public void render(
              Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

            leftValue.accept(renderer);
            rightValue.accept(renderer);
            renderer.methodGen().math(opcode, type.apply(TO_ASM));
          }
        });
  }

  /**
   * Perform a short-circuiting logical operation
   *
   * @param condition the bytecode on which to short circuit
   */
  public static Definition shortCircuit(int condition) {
    return exact(
        Imyhat.BOOLEAN,
        Imyhat.BOOLEAN,
        new BinaryOperation(Imyhat.BOOLEAN) {

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
        });
  }

  /**
   * Call a static method to evaluate an operation
   *
   * @param leftType the type of the left operand (and the first argument to the static method)
   * @param rightType the type of the right operand (and the second argument to the static method)
   * @param returnType the type of the result (and the return type of the static method)
   * @param owner the class containing the method to call
   * @param methodName the name of the method to call
   */
  public static Definition staticMethod(
      Imyhat leftType, Imyhat rightType, Imyhat returnType, Type owner, String methodName) {
    return exact(
        leftType,
        rightType,
        new BinaryOperation(returnType) {

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
                        returnType.apply(TO_ASM),
                        new Type[] {leftType.apply(TO_ASM), rightType.apply(TO_ASM)}));
          }
        });
  }

  /**
   * Call a virtual method on the left operand
   *
   * @param leftType the type of the left operand; this must be a non-primitive type
   * @param rightType the type of the right operand (and the type of the only argument to the
   *     virtual method)
   * @param returnType the result type (and the return type of the virtual method)
   * @param methodName the name of the virtual method
   */
  public static Definition virtualMethod(
      Imyhat leftType, Imyhat rightType, Imyhat returnType, String methodName) {
    final Method method =
        new Method(methodName, returnType.apply(TO_ASM), new Type[] {rightType.apply(TO_ASM)});
    return exact(
        leftType,
        rightType,
        new BinaryOperation(returnType) {

          @Override
          public void render(
              Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

            leftValue.accept(renderer);
            rightValue.accept(renderer);
            if (leftType.javaType().isInterface()) {
              renderer.methodGen().invokeInterface(leftType.apply(TO_ASM), method);
            } else {
              renderer.methodGen().invokeVirtual(leftType.apply(TO_ASM), method);
            }
          }
        });
  }

  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  public static final BinaryOperation BAD =
      new BinaryOperation(Imyhat.BAD) {

        @Override
        public void render(
            Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {
          throw new UnsupportedOperationException();
        }
      };

  private final Imyhat returnType;

  public BinaryOperation(Imyhat returnType) {
    super();
    this.returnType = returnType;
  }

  public abstract void render(
      Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue);

  public Imyhat returnType() {
    return returnType;
  }
}
