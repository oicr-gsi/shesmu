package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class WizardMatchAlternativeNodeRemainder extends WizardMatchAlternativeNode {

  private final int column;
  private final int line;
  private final String name;
  private boolean read;
  private final WizardNode step;
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

  public WizardMatchAlternativeNodeRemainder(int line, int column, String name, WizardNode step) {
    this.line = line;
    this.column = column;
    this.name = name;
    this.step = step;
  }

  @Override
  public String render(EcmaScriptRenderer renderer, EcmaLoadableConstructor name, String original) {
    return String.format(
        "$state => %s({...$state, %s: %s})",
        step.renderEcma(
            renderer,
            base ->
                Stream.concat(
                    name.create(base),
                    Stream.of(
                        new EcmaLoadableValue() {
                          @Override
                          public String apply(EcmaScriptRenderer renderer) {
                            return base.apply(renderer) + "." + name();
                          }

                          @Override
                          public String name() {
                            return WizardMatchAlternativeNodeRemainder.this.name;
                          }
                        }))),
        this.name,
        original);
  }

  @Override
  public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
    boolean ok = step.resolve(defs.bind(target), errorHandler);
    if (ok && !read) {
      errorHandler.accept(String.format("%d:%d: Variable “%s” is never used.", line, column, name));
      return false;
    }
    return ok;
  }

  @Override
  public boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler) {
    return step.resolveCrossReferences(references, errorHandler);
  }

  @Override
  public boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefinitions,
      Consumer<String> errorHandler) {
    return step.resolveDefinitions(expressionCompilerServices, nativeDefinitions, errorHandler);
  }

  @Override
  public boolean typeCheck(
      int line, int column, Map<String, Imyhat> remainingBranches, Consumer<String> errorHandler) {
    if (remainingBranches.isEmpty()) {
      errorHandler.accept(
          String.format(
              "%d:%d: No unmatched branches for type. Remainder will never run.", line, column));
      return false;
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
    return step.typeCheck(errorHandler);
  }
}
