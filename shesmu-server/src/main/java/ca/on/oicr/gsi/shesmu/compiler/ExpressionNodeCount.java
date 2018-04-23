package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;

public class ExpressionNodeCount extends ExpressionNode {
	private static final Method A_SET__SIZE = new Method("size", Type.INT_TYPE, new Type[] {});

	private static final Type A_SET_TYPE = Type.getType(Set.class);

	private final ExpressionNode inner;

	public ExpressionNodeCount(int line, int column, ExpressionNode inner) {
		super(line, column);
		this.inner = inner;

	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		inner.collectFreeVariables(names);
	}

	@Override
	public void render(Renderer renderer) {
		inner.render(renderer);
		renderer.mark(line());

		renderer.methodGen().invokeInterface(A_SET_TYPE, A_SET__SIZE);
		renderer.methodGen().cast(Type.INT_TYPE, Type.LONG_TYPE);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return inner.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveLookups(Function<String, LookupDefinition> definedLookups, Consumer<String> errorHandler) {
		return inner.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return Imyhat.INTEGER;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		boolean ok = inner.typeCheck(errorHandler);
		if (ok) {
			ok = inner.type() instanceof Imyhat.ListImyhat;
			if (!ok) {
				typeError("list", inner.type(), errorHandler);
			}
		}
		return ok;
	}

}
