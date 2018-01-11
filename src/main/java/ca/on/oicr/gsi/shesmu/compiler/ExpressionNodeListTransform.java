package ca.on.oicr.gsi.shesmu.compiler;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class ExpressionNodeListTransform extends ExpressionNode {

	private final CollectNode collector;
	private Type initialType;
	private final ExpressionNode source;

	private final List<ListNode> transforms;

	public ExpressionNodeListTransform(int line, int column, ExpressionNode source, List<ListNode> transforms,
			CollectNode collector) {
		super(line, column);
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
		return source.resolve(defs, errorHandler) & collector.resolve(defs, errorHandler)
				& transforms.stream().filter(t -> t.resolve(defs, errorHandler)).count() == transforms.size();
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return source.resolveLookups(definedLookups, errorHandler)
				& collector.resolveLookups(definedLookups, errorHandler) & transforms.stream()
						.filter(t -> t.resolveLookups(definedLookups, errorHandler)).count() == transforms.size();
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
			Imyhat incoming = ((Imyhat.ListImyhat) type).inner();
			initialType = incoming.asmType();
			for (final ListNode transform : transforms) {
				if (!transform.typeCheck(incoming, errorHandler)) {
					return false;
				}
				incoming = transform.nextType();
			}
			return collector.typeCheck(incoming, errorHandler);
		} else {
			typeError("list", type, errorHandler);
			return false;
		}
	}

}
