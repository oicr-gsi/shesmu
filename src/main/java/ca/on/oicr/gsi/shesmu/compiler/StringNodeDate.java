package ca.on.oicr.gsi.shesmu.compiler;

import java.time.Instant;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;

public class StringNodeDate extends StringNode {

	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
	private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Type A_STRINGBUILDER_TYPE = Type.getType(StringBuilder.class);

	private static final Method METHOD_FORMAT_DATE = new Method("appendFormatted", A_STRINGBUILDER_TYPE,
			new Type[] { A_STRINGBUILDER_TYPE, A_INSTANT_TYPE, A_STRING_TYPE });
	private final int column;

	private final ExpressionNode expression;
	private final String format;
	private final int line;

	public StringNodeDate(int line, int column, ExpressionNode expression, String format) {
		this.line = line;
		this.column = column;
		this.expression = expression;
		this.format = format;
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
		renderer.methodGen().push(format);
		renderer.methodGen().invokeStatic(A_RUNTIME_SUPPORT_TYPE, METHOD_FORMAT_DATE);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return expression.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return expression.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public String text() {
		return null;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		if (expression.typeCheck(errorHandler)) {
			final boolean ok = expression.type().isSame(Imyhat.DATE);
			if (!ok) {
				errorHandler.accept(String.format("%d:%d: Date expected in date-formatted interpolation, but got %s.",
						line, column, expression.type().name()));
			}
			return ok;
		} else {
			return false;
		}
	}

}
