package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class ExpressionNodeList extends ExpressionNode {

	private static final Type A_HASH_SET_TYPE = Type.getType(HashSet.class);

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Method CTOR_DEFAULT = new Method("<init>", Type.VOID_TYPE, new Type[] {});

	private static final Method METHOD_HASH_SET__ADD = new Method("add", Type.BOOLEAN_TYPE, new Type[] { A_OBJECT_TYPE });

	private final List<ExpressionNode> items;

	private Imyhat type = Imyhat.BAD;

	public ExpressionNodeList(int line, int column, List<ExpressionNode> items) {
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
		renderer.methodGen().newInstance(A_HASH_SET_TYPE);
		renderer.methodGen().dup();
		renderer.methodGen().invokeConstructor(A_HASH_SET_TYPE, CTOR_DEFAULT);
		items.forEach(item -> {
			renderer.methodGen().dup();
			item.render(renderer);
			renderer.methodGen().box(item.type().asmType());
			renderer.methodGen().invokeVirtual(A_HASH_SET_TYPE, METHOD_HASH_SET__ADD);
			renderer.methodGen().pop();
		});
		renderer.mark(line());
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return items.stream().filter(item -> item.resolve(defs, errorHandler)).count() == items.size();
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return items.stream().filter(item -> item.resolveLookups(definedLookups, errorHandler)).count() == items.size();
	}

	@Override
	public Imyhat type() {
		return type;
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		if (items.size() == 0) {
			errorHandler.accept(String.format("%d:%d: Cannot define empty list.", line(), column()));
		}
		boolean ok = items.stream().filter(item -> item.typeCheck(errorHandler)).count() == items.size();
		if (ok) {
			final Imyhat firstType = items.get(0).type();
			ok = items.stream().filter(item -> {
				final boolean isSame = item.type().isSame(firstType);
				if (isSame) {
					return true;
				}
				item.typeError(firstType.name(), item.type(), errorHandler);
				return false;
			}).count() == items.size();
			type = firstType.asList();
		}
		return ok;
	}

}
