package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;

public class ExpressionNodeLookup extends ExpressionNode {
	private static final LookupDefinition BROKEN_LOOKUP = new LookupDefinition() {

		@Override
		public String name() {
			return "💔";
		}

		@Override
		public void render(GeneratorAdapter methodGen) {
			throw new UnsupportedOperationException();
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

	private final List<ExpressionNode> arguments;

	private LookupDefinition lookup;

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
		arguments.forEach(argument -> argument.render(renderer));
		lookup.render(renderer.methodGen());
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return arguments.stream().filter(argument -> argument.resolve(defs, errorHandler)).count() == arguments.size();
	}

	@Override
	public boolean resolveLookups(Function<String, LookupDefinition> definedLookups, Consumer<String> errorHandler) {
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
