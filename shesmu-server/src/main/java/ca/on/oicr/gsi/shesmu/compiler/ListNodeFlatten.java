package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class ListNodeFlatten extends ListNodeWithExpression {

	private final String childName;
	private Imyhat initialType;

	private String nextName;

	private Ordering ordering = Ordering.RANDOM;

	private final List<ListNode> transforms;

	private Imyhat type;

	public ListNodeFlatten(int line, int column, String childName, ExpressionNode expression,
			List<ListNode> transforms) {
		super(line, column, expression);
		this.childName = childName;
		nextName = childName;
		this.transforms = transforms;
	}

	@Override
	protected void finishMethod(Renderer renderer) {
		final JavaStreamBuilder builder = renderer.buildStream(initialType);
		transforms.forEach(t -> t.render(builder));
		builder.finish();
	}

	@Override
	protected Renderer makeMethod(JavaStreamBuilder builder, LoadableValue[] loadables) {
		return builder.flatten(name(), type, loadables);
	}

	@Override
	public String nextName() {
		return nextName;
	}

	@Override
	public Imyhat nextType() {
		return type;
	}

	@Override
	public Ordering order(Ordering previous, Consumer<String> errorHandler) {
		if (previous == Ordering.BAD || ordering == Ordering.BAD) {
			return Ordering.BAD;
		}
		if (previous == Ordering.REQESTED && ordering == Ordering.REQESTED) {
			return Ordering.REQESTED;
		}
		return Ordering.RANDOM;
	}

	@Override
	protected boolean resolveExtra(NameDefinitions defs, Consumer<String> errorHandler) {

		final Optional<String> nextName = transforms.stream().reduce(Optional.of(childName),
				(n, t) -> n.flatMap(name -> t.resolve(name, defs, errorHandler)), (a, b) -> {
					throw new UnsupportedOperationException();
				});
		nextName.ifPresent(n -> this.nextName = n);
		return nextName.isPresent();
	}

	@Override
	protected boolean resolvefunctionsExtra(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return transforms.stream().filter(t -> t.resolveFunctions(definedFunctions, errorHandler)).count() == transforms
				.size();
	}

	@Override
	protected boolean typeCheckExtra(Imyhat incoming, Consumer<String> errorHandler) {
		if (incoming instanceof Imyhat.ListImyhat) {
			Imyhat innerIncoming = ((Imyhat.ListImyhat) incoming).inner();
			initialType = innerIncoming;
			for (final ListNode transform : transforms) {
				ordering = transform.order(ordering, errorHandler);
				if (!transform.typeCheck(innerIncoming, errorHandler)) {
					return false;
				}
				innerIncoming = transform.nextType();
			}
			type = innerIncoming;
			return true;
		}
		errorHandler.accept(
				String.format("%d:%d: Expected list for flattenting but got %s.", line(), column(), incoming.name()));
		return false;
	}
}
