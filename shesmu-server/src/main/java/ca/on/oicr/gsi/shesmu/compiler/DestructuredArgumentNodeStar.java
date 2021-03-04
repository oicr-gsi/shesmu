package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public class DestructuredArgumentNodeStar extends DestructuredArgumentNode {
  private class StarTarget implements DefinedTarget {
    private final String name;
    private Imyhat type = Imyhat.BAD;

    private StarTarget(String name) {
      this.name = name;
    }

    @Override
    public int column() {
      return column;
    }

    @Override
    public int line() {
      return line;
    }

    public LoadableValue prepare(Consumer<Renderer> loader) {
      return new LoadableValue() {
        private final Type asmType = type.apply(TO_ASM);
        private final int index = objectType.index(name);

        @Override
        public String name() {
          return name;
        }

        @Override
        public Type type() {
          return asmType;
        }

        @Override
        public void accept(Renderer renderer) {
          loader.accept(renderer);
          renderer.methodGen().push(index);
          renderer.methodGen().invokeVirtual(A_TUPLE_TYPE, METHOD_TUPLE__GET);
          renderer.methodGen().unbox(asmType);
        }
      };
    }

    @Override
    public Flavour flavour() {
      return flavour;
    }

    @Override
    public String name() {
      return name;
    }

    public EcmaLoadableValue prepareEcma(String loader) {
      return new EcmaLoadableValue() {
        @Override
        public String name() {
          return name;
        }

        @Override
        public String get() {
          return loader + "." + name;
        }
      };
    }

    @Override
    public void read() {
      read = true;
    }

    @Override
    public Imyhat type() {
      return type;
    }
  }

  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final Method METHOD_TUPLE__GET =
      new Method("get", Type.getType(Object.class), new Type[] {Type.INT_TYPE});
  private final int column;
  private final Map<String, StarTarget> fields = new TreeMap<>();
  private final int line;
  private boolean read;
  private Imyhat.ObjectImyhat objectType;
  private Target.Flavour flavour;

  public DestructuredArgumentNodeStar(int line, int column) {
    this.line = line;
    this.column = column;
  }

  @Override
  public boolean isBlank() {
    return false;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    if (read) {
      return true;
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: No variables map back to this wildcard. It is unused", line, column));
      return false;
    }
  }

  @Override
  public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    return WildcardCheck.HAS_WILDCARD;
  }

  @Override
  public Optional<Target> handleUndefinedVariable(String name) {
    return Optional.of(fields.computeIfAbsent(name, StarTarget::new));
  }

  @Override
  public Stream<LoadableValue> render(Consumer<Renderer> loader) {
    return fields.values().stream().map(t -> t.prepare(loader));
  }

  @Override
  public Stream<EcmaLoadableValue> renderEcma(String loader) {
    return fields.values().stream().map(f -> f.prepareEcma(loader));
  }

  @Override
  public boolean resolve(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return true;
  }

  @Override
  public void setFlavour(Target.Flavour flavour) {
    this.flavour = flavour;
  }

  @Override
  public Stream<DefinedTarget> targets() {
    return fields.values().stream().map(x -> x);
  }

  @Override
  public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
    if (type instanceof Imyhat.ObjectImyhat) {
      objectType = (Imyhat.ObjectImyhat) type;
      return fields.values().stream()
              .filter(
                  target -> {
                    target.type = objectType.get(target.name);
                    if (target.type.isBad()) {
                      errorHandler.accept(
                          String.format(
                              "%d:%d: Field %s inferred in * does not exist in object %s.",
                              line, column, target.name(), objectType.name()));
                      return false;
                    } else {
                      return true;
                    }
                  })
              .count()
          == fields.size();
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Object expected for destructuring, but got %s.", line, column, type.name()));
      return false;
    }
  }
}
