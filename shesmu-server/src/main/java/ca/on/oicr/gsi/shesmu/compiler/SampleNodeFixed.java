package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.subsample.Fixed;
import ca.on.oicr.gsi.shesmu.subsample.Subsampler;

public class SampleNodeFixed extends SampleNode {

	private final ExpressionNode expression;
	
	private static final Type A_FIXED_TYPE = Type.getType(Fixed.class);
	
	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE,
			new Type[] { Type.getType(Subsampler.class), Type.LONG_TYPE});

	public SampleNodeFixed(ExpressionNode expression) {
		this.expression = expression;
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
	public void collectFreeVariables(Set<String> names) {
		expression.collectFreeVariables(names);

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

	@Override
	public void render(Renderer renderer, int previousLocal, String prefix, int index, Type streamType) {
		renderer.methodGen().newInstance(A_FIXED_TYPE);
		renderer.methodGen().dup();
		renderer.methodGen().loadLocal(previousLocal);
		expression.render(renderer);
		renderer.methodGen().invokeConstructor(A_FIXED_TYPE, CTOR);
		renderer.methodGen().storeLocal(previousLocal);
	}

}
