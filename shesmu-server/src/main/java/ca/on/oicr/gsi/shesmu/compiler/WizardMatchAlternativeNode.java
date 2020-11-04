package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.ParseDispatch;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public abstract class WizardMatchAlternativeNode {

  private static final ParseDispatch<WizardMatchAlternativeNode> DISPATCH = new ParseDispatch<>();

  static {
    DISPATCH.addKeyword(
        "Else",
        (p, o) -> {
          final AtomicReference<WizardNode> step = new AtomicReference<>();
          final Parser result = p.whitespace().then(WizardNode::parse, step::set).whitespace();
          if (result.isGood()) {
            o.accept(new WizardMatchAlternativeNodeElse(step.get()));
          }
          return result;
        });
    DISPATCH.addKeyword(
        "Remainder",
        (p, o) -> {
          final AtomicReference<String> name = new AtomicReference<>();
          final AtomicReference<WizardNode> expression = new AtomicReference<>();
          final Parser result =
              p.whitespace()
                  .symbol("(")
                  .whitespace()
                  .identifier(name::set)
                  .whitespace()
                  .symbol(")")
                  .whitespace()
                  .then(WizardNode::parse, expression::set)
                  .whitespace();
          if (result.isGood()) {
            o.accept(
                new WizardMatchAlternativeNodeRemainder(
                    p.line(), p.column(), name.get(), expression.get()));
          }
          return result;
        });
    DISPATCH.addRaw(
        "nothing",
        (p, o) -> {
          o.accept(new WizardMatchAlternativeNodeEmpty());
          return p;
        });
  }

  public static Parser parse(Parser input, Consumer<WizardMatchAlternativeNode> output) {
    return input.whitespace().dispatch(DISPATCH, output);
  }

  public abstract String render(
      EcmaScriptRenderer renderer,
      EcmaLoadableConstructor name,
      EcmaScriptRenderer localRenderer,
      String original);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveCrossReferences(
      Map<String, WizardDefineNode> references, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices,
      DefinitionRepository nativeDefininitions,
      Consumer<String> errorHandler);

  public abstract boolean typeCheck(
      int line, int column, Map<String, Imyhat> remainingBranches, Consumer<String> errorHandler);
}
