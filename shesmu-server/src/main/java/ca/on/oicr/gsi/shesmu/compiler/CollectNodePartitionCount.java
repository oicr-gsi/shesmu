package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.runtime.PartitionCount;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;

public class CollectNodePartitionCount extends CollectNode {

	private static final Type A_COLLECTOR_TYPE = Type.getType(Collector.class);
	private static final Type A_PARTITION_COUNT_TYPE = Type.getType(PartitionCount.class);
	private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
	private final ExpressionNode expression;
	private Imyhat incomingType;
	private String name;
	private final Target parameter = new Target() {

		@Override
		public Flavour flavour() {
			return Flavour.LAMBDA;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public Imyhat type() {
			return incomingType;
		}
	};

	public CollectNodePartitionCount(int line, int column, ExpressionNode expression) {
		super(line, column);
		this.expression = expression;
	}

	@Override
	public void collectFreeVariables(Set<String> names, Predicate<Flavour> predicate) {
		final boolean remove = !names.contains(name);
		expression.collectFreeVariables(names, predicate);
		if (remove) {
			names.remove(name);
		}
	}

	@Override
	public void render(JavaStreamBuilder builder) {
		final Set<String> freeVariables = new HashSet<>();
		expression.collectFreeVariables(freeVariables, Flavour::needsCapture);
		final Renderer renderer = builder.map(name, Imyhat.BOOLEAN, builder.renderer().allValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));
		renderer.methodGen().visitCode();
		expression.render(renderer);
		renderer.methodGen().returnValue();
		renderer.methodGen().visitMaxs(0, 0);
		renderer.methodGen().visitEnd();
		builder.collector(A_TUPLE_TYPE, r -> {
			r.methodGen().getStatic(A_PARTITION_COUNT_TYPE, "COLLECTOR", A_COLLECTOR_TYPE);
		});
	}

	@Override
	public boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return expression.resolve(defs.bind(parameter), errorHandler);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return expression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public Imyhat type() {
		return PartitionCount.TYPE;
	}

	@Override
	public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		incomingType = incoming;
		boolean ok = expression.typeCheck(errorHandler);
		if (ok) {
			if (!expression.type().isSame(Imyhat.BOOLEAN)) {
				expression.typeError(Imyhat.BOOLEAN.name(), expression.type(), errorHandler);
				ok = false;
			}
		}
		return ok;
	}

}
