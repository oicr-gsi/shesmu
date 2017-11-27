package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class ExpressionNodeLookup extends ExpressionNode {
	private static final Type A_LOOKUP_TYPE = Type.getType(Lookup.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Lookup BROKEN_LOOKUP = new Lookup() {

		@Override
		public Object lookup(Object... parameters) {
			return null;
		}

		@Override
		public String name() {
			return "💔";
		}

		@Override
		public Imyhat returnType() {
			return Imyhat.BAD;
		}

		@Override
		public Stream<Imyhat> types() {
			return Stream.empty();
		}
	};
	private static final Method METHOD_LOOKUP__GET = new Method("get", A_OBJECT_TYPE,
			new Type[] { Type.getType(Object[].class) });

	private final List<ExpressionNode> arguments;

	private Lookup lookup;

	private final String name;

	public ExpressionNodeLookup(int line, int column, String name, List<ExpressionNode> arguments) {
		super(line, column);
		this.name = name;
		this.arguments = arguments;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		arguments.forEach(item -> item.collectFreeVariables(names));
	}

	@Override
	public void render(Renderer renderer) {
		renderer.loadLookup(name, renderer.methodGen());
		renderer.methodGen().push(arguments.size());
		renderer.methodGen().newArray(A_OBJECT_TYPE);
		for (int index = 0; index < arguments.size(); index++) {
			renderer.methodGen().dup();
			renderer.methodGen().push(index);
			arguments.get(index).render(renderer);
			renderer.methodGen().box(arguments.get(index).type().asmType());
			renderer.methodGen().arrayStore(A_OBJECT_TYPE);
		}

		renderer.methodGen().invokeInterface(A_LOOKUP_TYPE, METHOD_LOOKUP__GET);
		renderer.methodGen().unbox(lookup.returnType().asmType());
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return arguments.stream().filter(argument -> argument.resolve(defs, errorHandler)).count() == arguments.size();
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		boolean ok = true;
		lookup = definedLookups.apply(name);
		if (lookup == null) {
			lookup = BROKEN_LOOKUP;
			errorHandler.accept(String.format("%d:%d: Undefined lookup “%s”.", line(), column(), name));
			ok = false;
		}
		return ok & arguments.stream().filter(argument -> argument.resolveLookups(definedLookups, errorHandler))
				.count() == arguments.size();
	}

	@Override
	public Imyhat type() {
		return lookup.returnType();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		boolean ok = arguments.stream().filter(argument -> argument.typeCheck(errorHandler)).count() == arguments
				.size();
		if (ok) {
			final List<Imyhat> argumentTypes = lookup.types().collect(Collectors.toList());
			if (arguments.size() != argumentTypes.size()) {
				errorHandler.accept(String.format("%d:%d: Wrong number of arguments to lookup. Expected %d, got %d.",
						line(), column(), arguments.size(), argumentTypes.size()));
			}
			ok = IntStream.range(0, argumentTypes.size()).filter(index -> {
				final boolean isSame = argumentTypes.get(index).isSame(arguments.get(index).type());
				if (!isSame) {
					arguments.get(index).typeError(argumentTypes.get(index).name(), arguments.get(index).type(),
							errorHandler);
				}
				return isSame;
			}).count() == argumentTypes.size();
		}
		return ok;
	}

}
