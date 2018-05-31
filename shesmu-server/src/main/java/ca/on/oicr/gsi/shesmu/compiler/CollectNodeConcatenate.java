package ca.on.oicr.gsi.shesmu.compiler;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.ListNode.Ordering;

public class CollectNodeConcatenate extends CollectNode {

	public enum ConcatentationType {
		LEXICOGRAPHICAL("LexicalConcat"), PROVIDED("FixedConcat");
		private final String syntax;

		private ConcatentationType(String syntax) {
			this.syntax = syntax;
		}

		public String syntax() {
			return syntax;
		}
	}

	private static final Type A_CHAR_SEQUENCE_TYPE = Type.getType(CharSequence.class);
	private static final Type A_COLLECTOR_TYPE = Type.getType(Collector.class);
	private static final Type A_COLLECTORS_TYPE = Type.getType(Collectors.class);
	private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
	private static final Method METHOD_COLLECTORS__JOINING = new Method("joining", A_COLLECTOR_TYPE,
			new Type[] { A_CHAR_SEQUENCE_TYPE });

	private static final Method METHOD_STREAM__SORTED = new Method("sorted", A_STREAM_TYPE, new Type[] {});

	private final ExpressionNode delimiter;
	private final ExpressionNode getter;
	private final ConcatentationType concatentation;

	private String name;
	private boolean needsSort;
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
			return type;
		}

	};

	private Imyhat type;

	public CollectNodeConcatenate(int line, int column, ConcatentationType concatentation, ExpressionNode getter, ExpressionNode delimiter) {
		super(line, column);
		this.concatentation = concatentation;
		this.getter = getter;
		this.delimiter = delimiter;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		getter.collectFreeVariables(names);
		delimiter.collectFreeVariables(names);
	}

	@Override
	public final boolean orderingCheck(Ordering ordering, Consumer<String> errorHandler) {
		switch (concatentation) {
		case LEXICOGRAPHICAL:
			needsSort = true;
			return true;
		case PROVIDED:
			if (ordering == Ordering.RANDOM) {
				errorHandler.accept(String.format(
						"%d:%d: String concatenation is based on a random order. That is a bad idea.", line(), column()));
				return false;
			}
			return true;
		default:
			return false;
		}
	}

	@Override
	public void render(JavaStreamBuilder builder) {
		final Set<String> freeVariables = new HashSet<>();
		getter.collectFreeVariables(freeVariables);

		final Renderer mapMethod = builder.map(name, Imyhat.STRING.asmType(), builder.renderer().allValues()
				.filter(v -> freeVariables.contains(v.name())).toArray(LoadableValue[]::new));
		mapMethod.methodGen().visitCode();
		getter.render(mapMethod);
		mapMethod.methodGen().returnValue();
		mapMethod.methodGen().visitMaxs(0, 0);
		mapMethod.methodGen().visitEnd();

		builder.collector(Imyhat.STRING.asmType(), renderer -> {
			if (needsSort) {
				renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__SORTED);
			}
			delimiter.render(renderer);
			renderer.methodGen().invokeStatic(A_COLLECTORS_TYPE, METHOD_COLLECTORS__JOINING);
		});
	}

	@Override
	public boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return getter.resolve(defs.bind(parameter), errorHandler) & delimiter.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return getter.resolveFunctions(definedFunctions, errorHandler)
				& delimiter.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public Imyhat type() {
		return Imyhat.STRING;
	}

	@Override
	public boolean typeCheck(Imyhat incoming, Consumer<String> errorHandler) {
		type = incoming;
		boolean ok = true;
		if (getter.typeCheck(errorHandler)) {
			if (!getter.type().isSame(Imyhat.STRING)) {
				getter.typeError(Imyhat.STRING.name(), getter.type(), errorHandler);
				ok = false;
			}
		} else {
			ok = false;
		}
		if (delimiter.typeCheck(errorHandler)) {
			if (!delimiter.type().isSame(Imyhat.STRING)) {
				delimiter.typeError(Imyhat.STRING.name(), delimiter.type(), errorHandler);
				ok = false;
			}
		} else {
			ok = false;
		}
		return ok;
	}

}
