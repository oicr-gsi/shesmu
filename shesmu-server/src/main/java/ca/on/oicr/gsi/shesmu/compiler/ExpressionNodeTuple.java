package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;
import ca.on.oicr.gsi.shesmu.Tuple;

public class ExpressionNodeTuple extends ExpressionNode {

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);

	private static final Method CTOR_TUPLE = new Method("<init>", Type.VOID_TYPE,
			new Type[] { Type.getType(Object[].class) });

	private final List<ExpressionNode> items;

	private Imyhat type = Imyhat.BAD;

	public ExpressionNodeTuple(int line, int column, List<ExpressionNode> items) {
		super(line, column);
		this.items = items;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		items.forEach(item -> item.collectFreeVariables(names));
	}

	@Override
	public void render(Renderer renderer) {
		renderer.mark(line());

		renderer.methodGen().newInstance(A_TUPLE_TYPE);
		renderer.methodGen().dup();
		renderer.methodGen().push(items.size());
		renderer.methodGen().newArray(A_OBJECT_TYPE);
		for (int index = 0; index < items.size(); index++) {
			renderer.methodGen().dup();
			renderer.methodGen().push(index);
			items.get(index).render(renderer);
			renderer.methodGen().box(items.get(index).type().asmType());
			renderer.methodGen().arrayStore(A_OBJECT_TYPE);
		}
		renderer.mark(line());

		renderer.methodGen().invokeConstructor(A_TUPLE_TYPE, CTOR_TUPLE);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return items.stream().filter(item -> item.resolve(defs, errorHandler)).count() == items.size();
	}

	@Override
	public boolean resolveLookups(Function<String, LookupDefinition> definedLookups, Consumer<String> errorHandler) {
		return items.stream().filter(item -> item.resolveLookups(definedLookups, errorHandler)).count() == items.size();
	}

	@Override
	public Imyhat type() {
		return type;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		final boolean ok = items.stream().filter(item -> item.typeCheck(errorHandler)).count() == items.size();
		if (ok) {
			type = Imyhat.tuple(items.stream().map(ExpressionNode::type).toArray(Imyhat[]::new));
		}
		return ok;
	}

}
