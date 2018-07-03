package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public abstract class SampleNode {

	public enum Consumption {
		BAD, LIMITED, GREEDY
	}

	private static final Parser.ParseDispatch<SampleNode> DISPATCH = new Parser.ParseDispatch<>();

	static {
		DISPATCH.addKeyword("Squish", (p, o) -> {
			final AtomicReference<ExpressionNode> expression = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.then(ExpressionNode::parse, expression::set)//
					.whitespace();
			if (result.isGood()) {
				o.accept(new SampleNodeSquish(expression.get()));
			}
			return result;
		});
		DISPATCH.addKeyword("Fixed", (p, o) -> {
			final AtomicReference<ExpressionNode> limit = new AtomicReference<>();
			final AtomicReference<ExpressionNode> condition = new AtomicReference<>();
			final Parser result = p//
					.whitespace()//
					.then(ExpressionNode::parse, limit::set)//
					.whitespace();
			if (result.isGood()) {
				final Parser conditionResult = result//
						.keyword("While")//
						.whitespace()//
						.then(ExpressionNode::parse, condition::set)//
						.whitespace();
				if (conditionResult.isGood()) {
					o.accept(new SampleNodeFixedWithCondition(limit.get(), condition.get()));
					return conditionResult;
				}
				o.accept(new SampleNodeFixed(limit.get()));
			}
			return result;
		});
	}

	public static Parser parse(Parser parser, Consumer<SampleNode> output) {
		return parser.whitespace().dispatch(DISPATCH, output).whitespace();
	}

	public SampleNode() {
	}

	public abstract void collectFreeVariables(Set<String> names);

	public abstract Consumption consumptionCheck(Consumption previous, Consumer<String> errorHandler);

	public abstract void render(Renderer renderer, int previousLocal, String prefix, int index, Type streamType);

	public abstract boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler);

	public abstract boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler);

	public abstract boolean typeCheck(Imyhat type, Consumer<String> errorHandler);

}
