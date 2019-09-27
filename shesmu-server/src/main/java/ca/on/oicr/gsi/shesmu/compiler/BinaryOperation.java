package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.description.Renderable;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
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

  private static class ConcatenatedObjectField {
    private final boolean isLeft;
    private final String name;
    private final int originalIndex;
    private final Imyhat type;

    private ConcatenatedObjectField(String name, Imyhat type, int originalIndex, boolean isLeft) {
      this.name = name;
      this.type = type;
      this.originalIndex = originalIndex;
      this.isLeft = isLeft;
    }

    public void copy(
        GeneratorAdapter methodGen, int targetIndex, int leftVariable, int rightVariable) {
      methodGen.dup();
      methodGen.push(targetIndex);
      methodGen.loadLocal(isLeft ? leftVariable : rightVariable);
      methodGen.push(originalIndex);
      methodGen.invokeVirtual(A_TUPLE_TYPE, TUPLE__GET);
      methodGen.arrayStore(A_OBJECT_TYPE);
    }

    public String name() {
      return name;
    }

    public Pair<String, Imyhat> pair() {
      return new Pair<>(name, type);
    }
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
                      int line,
                      int column,
                      Renderer renderer,
                      Renderable leftValue,
                      Renderable rightValue) {
                    final Imyhat inner = ((Imyhat.ListImyhat) left).inner();
                    renderer.loadImyhat(inner.descriptor());
                    leftValue.render(renderer);
                    rightValue.render(renderer);
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
                    int line,
                    int column,
                    Renderer renderer,
                    Renderable leftValue,
                    Renderable rightValue) {
                  renderer.loadImyhat(inner.descriptor());
                  leftValue.render(renderer);
                  rightValue.render(renderer);
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

  public static Optional<BinaryOperation> objectConcat(Imyhat left, Imyhat right) {
    if (left instanceof Imyhat.ObjectImyhat && right instanceof Imyhat.ObjectImyhat) {
      final Imyhat.ObjectImyhat leftType = (Imyhat.ObjectImyhat) left;
      final Imyhat.ObjectImyhat rightType = (Imyhat.ObjectImyhat) right;
      Map<String, ConcatenatedObjectField> newFields =
          Stream.of(new Pair<>(true, leftType), new Pair<>(false, rightType))
              .flatMap(
                  pair ->
                      pair.second()
                          .fields()
                          .map(
                              field ->
                                  new ConcatenatedObjectField(
                                      field.getKey(),
                                      field.getValue().first(),
                                      field.getValue().second(),
                                      pair.first())))
              .collect(
                  Collectors.toMap(
                      ConcatenatedObjectField::name,
                      Function.identity(),
                      (a, b) -> null,
                      TreeMap::new));
      if (newFields.size() == leftType.fields().count() + rightType.fields().count()) {
        return Optional.of(
            new BinaryOperation(
                new Imyhat.ObjectImyhat(
                    newFields.values().stream().map(ConcatenatedObjectField::pair))) {
              @Override
              public void render(
                  int line,
                  int column,
                  Renderer renderer,
                  Renderable leftValue,
                  Renderable rightValue) {
                int leftVariable = renderer.methodGen().newLocal(A_TUPLE_TYPE);
                int rightVariable = renderer.methodGen().newLocal(A_TUPLE_TYPE);
                leftValue.render(renderer);
                renderer.methodGen().storeLocal(leftVariable);
                rightValue.render(renderer);
                renderer.methodGen().storeLocal(rightVariable);

                renderer.methodGen().newInstance(A_TUPLE_TYPE);
                renderer.methodGen().dup();
                renderer.methodGen().push(newFields.size());
                renderer.methodGen().newArray(A_OBJECT_TYPE);
                int targetIndex = 0;
                for (ConcatenatedObjectField field : newFields.values()) {
                  field.copy(renderer.methodGen(), targetIndex++, leftVariable, rightVariable);
                }
                renderer.methodGen().invokeConstructor(A_TUPLE_TYPE, TUPLE__CTOR);
              }
            });
      }
    }
    return Optional.empty();
  }

  public static Optional<BinaryOperation> optionalCoalesce(Imyhat left, Imyhat right) {
    final Imyhat resultType;
    if (left instanceof Imyhat.OptionalImyhat
        && right.isSame(((Imyhat.OptionalImyhat) left).inner())) {
      resultType = right.unify(((Imyhat.OptionalImyhat) left).inner());
    } else if (left == Imyhat.EMPTY) {
      resultType = right;
    } else {
      return Optional.empty();
    }

    return Optional.of(
        new BinaryOperation(resultType) {
          @Override
          public void render(
              int line,
              int column,
              Renderer renderer,
              Renderable leftValue,
              Renderable rightValue) {
            final Set<String> captures = new HashSet<>();
            rightValue.collectFreeVariables(captures, Target.Flavour::needsCapture);
            final LambdaBuilder supplier =
                new LambdaBuilder(
                    renderer.root(),
                    String.format("Coalesce %d:%d", line, column),
                    LambdaBuilder.supplier(resultType),
                    renderer
                        .allValues()
                        .filter(v -> captures.contains(v.name()))
                        .toArray(LoadableValue[]::new));
            final Renderer orElseMethod =
                supplier.renderer(renderer.streamType(), renderer.signerEmitter());
            orElseMethod.methodGen().visitCode();
            rightValue.render(orElseMethod);
            orElseMethod.methodGen().returnValue();
            orElseMethod.methodGen().endMethod();
            leftValue.render(renderer);
            supplier.push(renderer);
            renderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, OPTIONAL__OR_ELSE_GET);
            renderer.methodGen().unbox(resultType.apply(TO_ASM));
          }
        });
  }

  public static Optional<BinaryOperation> optionalMerge(Imyhat left, Imyhat right) {
    if (left instanceof Imyhat.OptionalImyhat && right instanceof Imyhat.OptionalImyhat) {
      final Imyhat leftInner = ((Imyhat.OptionalImyhat) left).inner();
      final Imyhat rightInner = ((Imyhat.OptionalImyhat) right).inner();
      if (leftInner.isSame(rightInner)) {
        return Optional.of(
            new BinaryOperation(left.unify(right)) {
              @Override
              public void render(
                  int line,
                  int column,
                  Renderer renderer,
                  Renderable leftValue,
                  Renderable rightValue) {
                final Set<String> captures = new HashSet<>();
                rightValue.collectFreeVariables(captures, Target.Flavour::needsCapture);
                final LambdaBuilder supplier =
                    new LambdaBuilder(
                        renderer.root(),
                        String.format("Merge %d:%d", line, column),
                        LambdaBuilder.supplier(A_OPTIONAL_TYPE),
                        renderer
                            .allValues()
                            .filter(v -> captures.contains(v.name()))
                            .toArray(LoadableValue[]::new));
                final Renderer orElseMethod =
                    supplier.renderer(renderer.streamType(), renderer.signerEmitter());
                orElseMethod.methodGen().visitCode();
                rightValue.render(orElseMethod);
                orElseMethod.methodGen().returnValue();
                orElseMethod.methodGen().endMethod();
                leftValue.render(renderer);
                supplier.push(renderer);
                renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, RUNTIME_SUPPORT__MERGE);
              }
            });
      }
    }
    return Optional.empty();
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
              int line,
              int column,
              Renderer renderer,
              Renderable leftValue,
              Renderable rightValue) {

            leftValue.render(renderer);
            rightValue.render(renderer);
            renderer.methodGen().math(opcode, type.apply(TO_ASM));
          }
        });
  }

  /**
   * Perform a primitive math operation on two operands which must be floating point or integral;
   * returning the same type if integral, floating point otherwise
   *
   * @param opcode the integer version of the bytecode
   */
  public static Definition primitiveMathUpgrading(int opcode) {
    return new Definition() {
      @Override
      public Optional<BinaryOperation> match(Imyhat left, Imyhat right) {
        final boolean leftIsInt = left.isSame(Imyhat.INTEGER);
        final boolean rightIsInt = right.isSame(Imyhat.INTEGER);
        if ((leftIsInt || left.isSame(Imyhat.FLOAT))
            && (rightIsInt || right.isSame(Imyhat.FLOAT))) {
          return Optional.of(
              new BinaryOperation(leftIsInt && rightIsInt ? Imyhat.INTEGER : Imyhat.FLOAT) {
                @Override
                public void render(
                    int line,
                    int column,
                    Renderer renderer,
                    Renderable leftValue,
                    Renderable rightValue) {
                  leftValue.render(renderer);
                  if (leftIsInt && !rightIsInt) {
                    renderer.methodGen().cast(Type.LONG_TYPE, Type.DOUBLE_TYPE);
                  }
                  rightValue.render(renderer);
                  if (!leftIsInt && rightIsInt) {
                    renderer.methodGen().cast(Type.LONG_TYPE, Type.DOUBLE_TYPE);
                  }
                  renderer.methodGen().math(opcode, this.returnType().apply(TO_ASM));
                }
              });
        }
        return Optional.empty();
      }
    };
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
              int line,
              int column,
              Renderer renderer,
              Renderable leftValue,
              Renderable rightValue) {
            leftValue.render(renderer);
            final Label end = renderer.methodGen().newLabel();
            renderer.methodGen().dup();
            renderer.methodGen().ifZCmp(condition, end);
            renderer.methodGen().pop();
            rightValue.render(renderer);
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
              int line,
              int column,
              Renderer renderer,
              Renderable leftValue,
              Renderable rightValue) {

            leftValue.render(renderer);
            rightValue.render(renderer);
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

  public static Optional<BinaryOperation> tupleConcat(Imyhat left, Imyhat right) {
    if (left instanceof Imyhat.TupleImyhat && right instanceof Imyhat.TupleImyhat) {
      final Imyhat resultType =
          Imyhat.tuple(
              Stream.concat(
                      ((Imyhat.TupleImyhat) left).inner(), ((Imyhat.TupleImyhat) right).inner())
                  .toArray(Imyhat[]::new));
      return Optional.of(
          new BinaryOperation(resultType) {
            @Override
            public void render(
                int line,
                int column,
                Renderer renderer,
                Renderable leftValue,
                Renderable rightValue) {
              leftValue.render(renderer);
              rightValue.render(renderer);
              renderer.methodGen().invokeVirtual(A_TUPLE_TYPE, TUPLE__CONCAT);
            }
          });
    }
    return Optional.empty();
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
              int line,
              int column,
              Renderer renderer,
              Renderable leftValue,
              Renderable rightValue) {

            leftValue.render(renderer);
            rightValue.render(renderer);
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
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_SET_TYPE = Type.getType(Set.class);
  private static final Type A_SUPPLIER_TYPE = Type.getType(Supplier.class);
  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  public static final BinaryOperation BAD =
      new BinaryOperation(Imyhat.BAD) {

        @Override
        public void render(
            int line, int column, Renderer renderer, Renderable leftValue, Renderable rightValue) {
          throw new UnsupportedOperationException();
        }
      };
  private static final Method OPTIONAL__OR_ELSE_GET =
      new Method("orElseGet", A_OBJECT_TYPE, new Type[] {A_SUPPLIER_TYPE});
  private static final Method RUNTIME_SUPPORT__MERGE =
      new Method("merge", A_OPTIONAL_TYPE, new Type[] {A_OPTIONAL_TYPE, A_SUPPLIER_TYPE});
  public static final Method TUPLE__CONCAT =
      new Method("concat", A_TUPLE_TYPE, new Type[] {A_TUPLE_TYPE});
  public static final Method TUPLE__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {Type.getType(Object[].class)});
  public static final Method TUPLE__GET =
      new Method("get", A_OBJECT_TYPE, new Type[] {Type.INT_TYPE});
  private final Imyhat returnType;

  public BinaryOperation(Imyhat returnType) {
    super();
    this.returnType = returnType;
  }

  public abstract void render(
      int line, int column, Renderer renderer, Renderable leftValue, Renderable rightValue);

  public Imyhat returnType() {
    return returnType;
  }
}
