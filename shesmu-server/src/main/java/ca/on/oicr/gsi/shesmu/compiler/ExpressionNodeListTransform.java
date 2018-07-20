package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;

public class ExpressionNodeListTransform extends ExpressionNode {

	private final CollectNode collector;
	private Imyhat initialType;
	private final String name;

	private final ExpressionNode source;
	private final List<ListNode> transforms;

	public ExpressionNodeListTransform(int line, int column, String name, ExpressionNode source,
			List<ListNode> transforms, CollectNode collector) {
		super(line, column);
		this.name = name;
		this.source = source;
		this.transforms = transforms;
		this.collector = collector;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		source.collectFreeVariables(names);
		collector.collectFreeVariables(names);
		transforms.forEach(t -> t.collectFreeVariables(names));
	}

	@Override
	public void render(Renderer renderer) {
		source.render(renderer);
		final JavaStreamBuilder builder = renderer.buildStream(initialType);
		transforms.forEach(t -> t.render(builder));
		collector.render(builder);

	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		boolean ok = source.resolve(defs, errorHandler);

		final Optional<String> nextName = transforms.stream().reduce(Optional.of(name),
				(n, t) -> n.flatMap(name -> t.resolve(name, defs, errorHandler)), (a, b) -> {
					throw new UnsupportedOperationException();
				});

		ok &= nextName.map(name -> collector.resolve(name, defs, errorHandler)).orElse(false);
		return ok;
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return source.resolveFunctions(definedFunctions, errorHandler)
				& collector.resolveFunctions(definedFunctions, errorHandler) & transforms.stream()
						.filter(t -> t.resolveFunctions(definedFunctions, errorHandler)).count() == transforms.size();
	}

	@Override
	public Imyhat type() {
		return collector.type();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		if (!source.typeCheck(errorHandler)) {
			return false;
		}
		final Imyhat type = source.type();
		if (type instanceof Imyhat.ListImyhat) {
			Ordering ordering = Ordering.RANDOM;
			Imyhat incoming = ((Imyhat.ListImyhat) type).inner();
			initialType = incoming;
			for (final ListNode transform : transforms) {
				if (!transform.typeCheck(incoming, errorHandler)) {
					return false;
				}
				incoming = transform.nextType();
				ordering = transform.order(ordering, errorHandler);
			}
			if (collector.typeCheck(incoming, errorHandler) && ordering != Ordering.BAD) {
				return collector.orderingCheck(ordering, errorHandler);
			}
			return false;
		} else {
			typeError("list", type, errorHandler);
			return false;
		}
	}

}
