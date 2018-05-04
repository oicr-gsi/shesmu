package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.LONG_TYPE;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class StringNodeExpression extends StringNode {

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);

	private static final Method METHOD_OBJECT__TO_STRING = new Method("toString", A_STRING_TYPE, new Type[] {});
	private static final Method METHOD_STRINGBUILDER__APPEND__LONG = new Method("append", A_STRINGBUILDER_TYPE,
			new Type[] { LONG_TYPE });
	private static final Method METHOD_STRINGBUILDER__APPEND__STR = new Method("append", A_STRINGBUILDER_TYPE,
			new Type[] { A_STRING_TYPE });
	private final ExpressionNode expression;

	public StringNodeExpression(ExpressionNode expression) {
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		expression.collectFreeVariables(names);
	}

	@Override
	public boolean isPassive() {
		return false;
	}

	@Override
	public void render(Renderer renderer) {
		expression.render(renderer);
		if (expression.type().isSame(Imyhat.STRING)) {
			renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__STR);
		} else if (expression.type().isSame(Imyhat.INTEGER)) {
			renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__LONG);
		} else if (expression.type().isSame(Imyhat.DATE)) {
			renderer.methodGen().invokeVirtual(A_OBJECT_TYPE, METHOD_OBJECT__TO_STRING);
			renderer.methodGen().invokeVirtual(A_STRINGBUILDER_TYPE, METHOD_STRINGBUILDER__APPEND__STR);
		} else {
			throw new UnsupportedOperationException();
		}
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public String text() {
		return null;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = expression.typeCheck(errorHandler);
		if (ok) {
			final Imyhat innerType = expression.type();
			if (!innerType.isSame(Imyhat.INTEGER) && !innerType.isSame(Imyhat.DATE)
					&& !innerType.isSame(Imyhat.STRING)) {
				errorHandler.accept(
						String.format("%d:%d: Cannot convert type %s to string in interpolation.", innerType.name()));
			}
		}
		return ok;
	}

}
