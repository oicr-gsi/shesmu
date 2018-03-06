package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class ExpressionNodeContains extends ExpressionNode {
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_SET_TYPE = Type.getType(Set.class);

	private static final Method METHOD_SET__CONTAINS = new Method("contains", Type.BOOLEAN_TYPE,
			new Type[] { A_OBJECT_TYPE });

	private final ExpressionNode haystack;

	private final ExpressionNode needle;

	public ExpressionNodeContains(int line, int column, ExpressionNode needle, ExpressionNode haystack) {
		super(line, column);
		this.needle = needle;
		this.haystack = haystack;

	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		needle.collectFreeVariables(names);
		haystack.collectFreeVariables(names);
	}

	@Override
	public void render(Renderer renderer) {
		haystack.render(renderer);
		needle.render(renderer);
		renderer.mark(line());

		renderer.methodGen().box(needle.type().asmType());
		renderer.methodGen().invokeInterface(A_SET_TYPE, METHOD_SET__CONTAINS);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return needle.resolve(defs, errorHandler) & haystack.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return needle.resolveLookups(definedLookups, errorHandler)
				& haystack.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return Imyhat.BOOLEAN;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = needle.typeCheck(errorHandler) & haystack.typeCheck(errorHandler);
		if (ok) {
			if (needle.type().asList().isSame(haystack.type())) {
				return true;
			}
			typeError(needle.type().asList().name(), haystack.type(), errorHandler);
			return false;
		}
		return ok;
	}
}
