package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Fixed;
import ca.on.oicr.gsi.shesmu.runtime.subsample.Subsampler;

public class SampleNodeFixed extends SampleNode {

	private static final Type A_FIXED_TYPE = Type.getType(Fixed.class);

	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE,
			new Type[] { Type.getType(Subsampler.class), Type.LONG_TYPE });

	private final ExpressionNode expression;

	public SampleNodeFixed(ExpressionNode expression) {
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		expression.collectFreeVariables(names, predicate);

	}

	@Override
	public Consumption consumptionCheck(Consumption previous, Consumer<String> errorHandler) {
		switch (previous) {
		case LIMITED:
			return Consumption.LIMITED;
		case GREEDY:
			errorHandler.accept(String.format("%d:%d: No items will be left to subsample.", expression.line(),
					expression.column()));
			return Consumption.BAD;
		case BAD:
		default:
			return Consumption.BAD;

		}
	}

	@Override
	public void render(Renderer renderer, int previousLocal, Type streamType) {
		renderer.methodGen().newInstance(A_FIXED_TYPE);
		renderer.methodGen().dup();
		renderer.methodGen().loadLocal(previousLocal);
		expression.render(renderer);
		renderer.methodGen().invokeConstructor(A_FIXED_TYPE, CTOR);
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
