package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.SampleNode.Consumption;
import ca.on.oicr.gsi.shesmu.subsample.Squish;
import ca.on.oicr.gsi.shesmu.subsample.Subsampler;

public class SampleNodeSquish extends SampleNode {

	private static final Type A_SQUISH_TYPE = Type.getType(Squish.class);

	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE,
			new Type[] { Type.getType(Subsampler.class), Type.LONG_TYPE });

	private final ExpressionNode expression;

	public SampleNodeSquish(ExpressionNode expression) {
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		expression.collectFreeVariables(names);

	}

	@Override
	public Consumption consumptionCheck(Consumption previous, Consumer<String> errorHandler) {
		switch (previous) {
		case LIMITED:
			return Consumption.GREEDY;
		case GREEDY:
			errorHandler.accept(
					String.format("%d:%d: No items will be left to subsample.", expression.line(), expression.column()));
			return Consumption.BAD;
		case BAD:
		default:
			return Consumption.BAD;

		}
	}

	@Override
	public void render(Renderer renderer, int previousLocal, String prefix, int index, Type streamType) {
		renderer.methodGen().newInstance(A_SQUISH_TYPE);
		renderer.methodGen().dup();
		renderer.methodGen().loadLocal(previousLocal);
		expression.render(renderer);
		renderer.methodGen().invokeConstructor(A_SQUISH_TYPE, CTOR);
		renderer.methodGen().storeLocal(previousLocal);
	}

	@Override
	public boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
		final boolean ok = expression.typeCheck(errorHandler);
		if (ok && !expression.type().isSame(Imyhat.INTEGER)) {
			expression.typeError(Imyhat.INTEGER.name(), expression.type(), errorHandler);
			return false;
		}
		return true;
	}
}
