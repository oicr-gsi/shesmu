package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;

public class ExpressionNodeFor extends ExpressionNode {

	private final CollectNode collector;
	private final String name;

	private final SourceNode source;
	private final List<ListNode> transforms;

	public ExpressionNodeFor(int line, int column, String name, SourceNode source, List<ListNode> transforms,
			CollectNode collector) {
		super(line, column);
		this.name = name;
		this.source = source;
		this.transforms = transforms;
		this.collector = collector;
	}

	@Override
	public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		source.collectFreeVariables(names, predicate);
		collector.collectFreeVariables(names, predicate);
		transforms.forEach(t -> t.collectFreeVariables(names, predicate));
	}

	@Override
	public void render(Renderer renderer) {
		final JavaStreamBuilder builder = source.render(renderer);
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
		Ordering ordering = source.ordering();
		Imyhat incoming = source.streamType();
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
	}

}
