package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class DestructuredArgumentNodeVariable extends DestructuredArgumentNode {
  private Target.Flavour flavour;
  private final int line;
  private final int column;
  private final String name;
  private boolean read;
  private Imyhat type = Imyhat.BAD;
  private final Target target =
      new Target() {
        @Override
        public Flavour flavour() {
          return flavour;
        }

        @Override
        public String name() {
          return name;
        }

        @Override
        public void read() {
          read = true;
        }

        @Override
        public Imyhat type() {
          return type;
        }
      };

  public DestructuredArgumentNodeVariable(int line, int column, String name) {
    this.line = line;
    this.column = column;
    this.name = name;
  }

  @Override
  public boolean checkUnusedDeclarations(Consumer<String> errorHandler) {
    if (read) {
      return true;
    } else {
      errorHandler.accept(String.format("%d:%d: Variable “%s” is never used.", line, column, name));
      return false;
    }
  }

  @Override
  public WildcardCheck checkWildcard(Consumer<String> errorHandler) {
    return WildcardCheck.NONE;
  }

  @Override
  public boolean isBlank() {
    return false;
  }

  @Override
  public Stream<LoadableValue> render(Consumer<Renderer> loader) {
    return Stream.of(
        new LoadableValue() {
          @Override
          public void accept(Renderer renderer) {
            loader.accept(renderer);
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public Type type() {
            return type.apply(TO_ASM);
          }
        });
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
  public Stream<Target> targets() {
    return Stream.of(target);
  }

  @Override
  public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
    this.type = type;
    return true;
  }
}
