package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public class MatchBranchNodeWildcard extends MatchBranchNode {
  private class StarTarget implements DefinedTarget {
    private int index = -1;
    private final String name;
    private Imyhat type = Imyhat.BAD;

    private StarTarget(String name) {
      this.name = name;
      targets.add(this);
    }

    @Override
    public int column() {
      return MatchBranchNodeWildcard.this.column();
    }

    @Override
    public int line() {
      return MatchBranchNodeWildcard.this.line();
    }

    public LoadableValue prepare(BiConsumer<Renderer, Integer> loadElement) {
      return new LoadableValue() {
        private final Type asmType = type.apply(TO_ASM);

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
          loadElement.accept(renderer, index);
          renderer.methodGen().unbox(asmType);
        }
      };
    }

    @Override
    public Flavour flavour() {
      return Flavour.LAMBDA;
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
      // Always true.
    }

    @Override
    public Imyhat type() {
      return type;
    }
  }

  private final List<StarTarget> targets = new ArrayList<>();

  public MatchBranchNodeWildcard(int line, int column, String name, ExpressionNode value) {
    super(line, column, name, value);
  }

  @Override
  protected NameDefinitions bind(NameDefinitions definitions) {
    return definitions.withProvider(n -> Optional.of(new StarTarget(n)));
  }

  @Override
  protected Stream<Target> boundNames() {
    return targets.stream().map(x -> x);
  }

  @Override
  protected Stream<EcmaLoadableValue> loadBoundNames(String base) {
    return targets.stream().map(t -> t.prepareEcma(base));
  }

  @Override
  protected Renderer prepare(Renderer renderer, BiConsumer<Renderer, Integer> loadElement) {
    final var result = renderer.duplicate();
    for (final var target : targets) {
      result.define(target.name(), target.prepare(loadElement));
    }
    return result;
  }

  @Override
  protected boolean typeCheckBindings(Imyhat argumentType, Consumer<String> errorHandler) {
    if (argumentType instanceof Imyhat.ObjectImyhat) {
      final var objectType = (Imyhat.ObjectImyhat) argumentType;
      var ok = true;
      for (final var target : targets) {
        final var fieldType = objectType.get(target.name());
        if (fieldType.isBad()) {
          ok = false;
          errorHandler.accept(
              String.format(
                  "%d:%d: Wildcard variable %s does not exist in algebraic type %s.",
                  line(), column(), target.name(), objectType.name()));
        } else {
          target.type = fieldType;
          target.index = objectType.index(target.name());
        }
      }
      return ok;
    } else {
      errorHandler.accept(
          String.format(
              "%d:%d: Algebraic object expected for destructuring, but got %s.",
              line(), column(), argumentType.name()));
      return false;
    }
  }
}
