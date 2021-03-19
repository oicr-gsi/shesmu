package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.Parser.RuleWithLiteral;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import java.time.Instant;
import java.util.function.Consumer;

public abstract class InformationParameterNode<T> {
  private static final class SubRule<T> implements RuleWithLiteral<InformationParameterNode<T>, T> {
    private final boolean canFlatten;
    private final TypeGuarantee<T> guarantee;
    private final RuleWithLiteral<T, T> real;
    private final String suffix;

    private SubRule(
        RuleWithLiteral<T, T> real, TypeGuarantee<T> guarantee, boolean canFlatten, String suffix) {
      this.real = real;
      this.guarantee = guarantee;
      this.canFlatten = canFlatten;
      this.suffix = suffix;
    }

    @Override
    public InformationParameterNode<T> literal(T value) {
      return new InformationParameterNodeLiteral<>(value, canFlatten);
    }

    @Override
    public Parser parse(Parser parser, Consumer<InformationParameterNode<T>> output) {
      final var expressionResult = parser.whitespace().symbol("{");
      if (expressionResult.isGood()) {
        return expressionResult
            .then(
                ExpressionNode::parse,
                e ->
                    output.accept(
                        new InformationParameterNodeExpression<>(e, guarantee, canFlatten, suffix)))
            .symbol("}")
            .whitespace();
      }
      return real.parse(
          parser, v -> output.accept(new InformationParameterNodeLiteral<>(v, canFlatten)));
    }
  }

  public static final RuleWithLiteral<InformationParameterNode<ActionState>, ActionState>
      ACTION_STATE =
          new SubRule<>(
              ActionFilter.PARSE_ACTION_STATE,
              TypeGuarantee.algebraicForEnum(ActionState.class),
              true,
              ".map(x => x.type)");
  public static final RuleWithLiteral<InformationParameterNode<Instant>, Instant> INSTANT =
      new SubRule<>(ActionFilter.PARSE_TIME, TypeGuarantee.DATE, false, "");
  public static final RuleWithLiteral<InformationParameterNode<Long>, Long> OFFSET =
      new SubRule<>(ActionFilter.PARSE_OFFSET, TypeGuarantee.LONG, false, "");
  public static final RuleWithLiteral<InformationParameterNode<String>, String> STRING =
      new SubRule<>(ActionFilter.PARSE_STRING, TypeGuarantee.STRING, false, "");
  public static final RuleWithLiteral<InformationParameterNode<String>, String> STRINGS =
      new SubRule<>(ActionFilter.PARSE_STRING, TypeGuarantee.STRING, true, "");

  public abstract String renderEcma(EcmaScriptRenderer renderer);

  public abstract boolean resolve(NameDefinitions defs, Consumer<String> errorHandler);

  public abstract boolean resolveDefinitions(
      ExpressionCompilerServices expressionCompilerServices, Consumer<String> errorHandler);

  public abstract boolean typeCheck(Consumer<String> errorHandler);
}
