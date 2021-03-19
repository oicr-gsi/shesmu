package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;

public class MatchAlternativeNodeRemainder extends MatchAlternativeNode {

  private static final Type A_ALGEBRAIC_VALUE_TYPE = Type.getType(AlgebraicValue.class);
  private final ExpressionNode expression;
  private final String name;
  private boolean read;
  private Imyhat type = Imyhat.BAD;
  private final Target target =
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
          read = true;
        }

        @Override
        public Imyhat type() {
          return type;
        }
      };

  public MatchAlternativeNodeRemainder(String name, ExpressionNode expression) {
    this.name = name;
    this.expression = expression;
  }

  @Override
  public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
    final var remove = !names.contains(name);
    expression.collectFreeVariables(names, predicate);
    if (remove) {
      names.remove(name);
    }
  }

  @Override
  public void collectPlugins(Set<Path> pluginFileNames) {
    expression.collectPlugins(pluginFileNames);
  }

  @Override
  public void render(Renderer renderer, Label end, int local) {
    final var result = renderer.duplicate();
    result.define(
        name,
        new LoadableValue() {
          @Override
          public void accept(Renderer renderer) {
            renderer.methodGen().loadLocal(local);
          }

          @Override
          public String name() {
            return name;
          }

          @Override
          public Type type() {
            return A_ALGEBRAIC_VALUE_TYPE;
          }
        });
    expression.render(result);
    renderer.methodGen().goTo(end);
  }

  @Override
  public String render(EcmaScriptRenderer renderer, String original) {
    renderer.define(
        new EcmaLoadableValue() {
          @Override
          public String name() {
            return name;
          }

          @Override
          public String get() {
            return original;
          }
        });
    return expression.renderEcma(renderer);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    var ok = expression.resolve(defs.bind(target), errorHandler);
    if (ok && !read) {
      errorHandler.accept(
          String.format(
              "%d:%d: Variable “%s” is never used.", expression.line(), expression.column(), name));
      return false;
    }
    return ok;
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler) {
    return expression.resolveDefinitions(expressionCompilerServices, errorHandler);
  }

  @Override
  public Imyhat typeCheck(
      int line,
      int column,
      Imyhat resultType,
      Map<String, Imyhat> remainingBranches,
      Consumer<String> errorHandler) {
    if (remainingBranches.isEmpty()) {
      errorHandler.accept(
          String.format(
              "%d:%d: No unmatched branches for type. Remainder will never run.", line, column));
      return Imyhat.BAD;
    }
    type =
        remainingBranches.entrySet().stream()
            .map(
                e ->
                    e.getValue()
                        .apply(
                            new ImyhatTransformer<Imyhat>() {
                              @Override
                              public Imyhat algebraic(Stream<AlgebraicTransformer> contents) {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat bool() {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat date() {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat floating() {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat integer() {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat json() {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat list(Imyhat inner) {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat map(Imyhat key, Imyhat value) {
                                return null;
                              }

                              @Override
                              public Imyhat object(Stream<Pair<String, Imyhat>> contents) {
                                return Imyhat.algebraicObject(e.getKey(), contents);
                              }

                              @Override
                              public Imyhat optional(Imyhat inner) {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat path() {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat string() {
                                throw new IllegalStateException();
                              }

                              @Override
                              public Imyhat tuple(Stream<Imyhat> contents) {
                                return Imyhat.algebraicTuple(
                                    e.getKey(), contents.toArray(Imyhat[]::new));
                              }
                            }))
            .reduce(Imyhat::unify)
            .get();
    var ok = expression.typeCheck(errorHandler);
    if (ok) {
      if (expression.type().isSame(resultType)) {
        return expression.type().unify(resultType);
      } else {
        expression.typeError(resultType, expression.type(), errorHandler);
      }
    }
    return Imyhat.BAD;
  }
}
