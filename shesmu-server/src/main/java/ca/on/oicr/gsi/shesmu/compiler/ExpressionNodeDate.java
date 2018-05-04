package ca.on.oicr.gsi.shesmu.compiler;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class ExpressionNodeDate extends ExpressionNode {

	private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);

	private static final Method METHOD_OF_EPOCH_MILLI = new Method("ofEpochMilli", A_INSTANT_TYPE,
			new Type[] { Type.LONG_TYPE });

	private final ZonedDateTime value;

	public ExpressionNodeDate(int line, int column, ZonedDateTime value) {
		super(line, column);
		this.value = value;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		// Do nothing.
	}

	@Override
	public void render(Renderer renderer) {
		renderer.mark(line());

		renderer.methodGen().push(value.toInstant().toEpochMilli());
		renderer.methodGen().invokeStatic(A_INSTANT_TYPE, METHOD_OF_EPOCH_MILLI);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return true;
	}

	@Override
	public Imyhat type() {
		return Imyhat.DATE;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		return true;
	}

}
