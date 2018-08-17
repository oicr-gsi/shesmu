package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.LONG_TYPE;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public class StringNodeInteger extends StringNode {

	private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
	private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);
	private static final Method METHOD_FORMAT_NUMBER = new Method("appendFormatted", A_STRINGBUILDER_TYPE,
			new Type[] { A_STRINGBUILDER_TYPE, LONG_TYPE, INT_TYPE });
	private final int column;

	private final ExpressionNode expression;

	private final int line;

	private final int width;

	public StringNodeInteger(int line, int column, ExpressionNode expression, int width) {
		this.line = line;
		this.column = column;
		this.expression = expression;
		this.width = width;
	}

	@Override
	public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		expression.collectFreeVariables(names, predicate);
	}

	@Override
	public boolean isPassive() {
		return false;
	}

	@Override
	public void render(Renderer renderer) {
		expression.render(renderer);
		renderer.methodGen().push(width);
		renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_FORMAT_NUMBER);

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
		boolean ok = expression.typeCheck(errorHandler);
		if (ok) {
			ok = expression.type().isSame(Imyhat.INTEGER);
			if (!ok) {
				errorHandler.accept(String.format("%d:%d: Expected integer in padded interpolation, but got %s.", line,
						column, expression.type()));
			}
		}
		return ok;
	}

}
