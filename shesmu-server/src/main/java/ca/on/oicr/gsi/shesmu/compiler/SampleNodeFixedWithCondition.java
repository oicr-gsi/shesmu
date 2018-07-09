package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.subsample.FixedWithConditions;
import ca.on.oicr.gsi.shesmu.subsample.Subsampler;

public class SampleNodeFixedWithCondition extends SampleNode {

	private static final Type A_FIXEDWITHCONDITION_TYPE = Type.getType(FixedWithConditions.class);

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);
	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE,
			new Type[] { Type.getType(Subsampler.class), Type.LONG_TYPE, A_PREDICATE_TYPE });
	private static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);
	private final ExpressionNode conditionExpression;
	private final ExpressionNode limitExpression;

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
			return type;
		}
	};

	private Imyhat type;

	public SampleNodeFixedWithCondition(ExpressionNode limitExpression, ExpressionNode conditionExpression) {
		this.limitExpression = limitExpression;
		this.conditionExpression = conditionExpression;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		limitExpression.collectFreeVariables(names);
		final boolean remove = !names.contains(name);
		conditionExpression.collectFreeVariables(names);
		if (remove) {
			names.remove(name);
		}
	}

	@Override
	public Consumption consumptionCheck(Consumption previous, Consumer<String> errorHandler) {
		switch (previous) {
		case LIMITED:
			return Consumption.LIMITED;
		case GREEDY:
			errorHandler.accept(String.format("%d:%d: No items will be left to subsample.", limitExpression.line(),
					limitExpression.column()));
			return Consumption.BAD;
		case BAD:
		default:
			return Consumption.BAD;

		}
	}

	@Override
	public void render(Renderer renderer, int previousLocal, String prefix, int index, Type streamType) {
		final Set<String> freeVariables = new HashSet<>();
		conditionExpression.collectFreeVariables(freeVariables);
		final LoadableValue[] capturedVariables = renderer.allValues()
				.filter(v -> freeVariables.contains(v.name()) && !name.equals(v.name())).toArray(LoadableValue[]::new);
		final Method method = new Method(String.format("%s_%d_condition", prefix, index), BOOLEAN_TYPE,
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type),
						Stream.of(streamType, type.asmType())).toArray(Type[]::new));
		final Renderer conditionRenderer = new Renderer(renderer.root(),
				new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, renderer.root().classVisitor),
				capturedVariables.length, streamType,
				JavaStreamBuilder.parameters(capturedVariables, name, type.asmType()));
		;
		conditionRenderer.methodGen().visitCode();
		conditionExpression.render(conditionRenderer);
		conditionRenderer.methodGen().returnValue();
		conditionRenderer.methodGen().visitMaxs(0, 0);
		conditionRenderer.methodGen().visitEnd();
		renderer.methodGen().newInstance(A_FIXEDWITHCONDITION_TYPE);
		renderer.methodGen().dup();
		renderer.methodGen().loadLocal(previousLocal);
		limitExpression.render(renderer);
		renderer.methodGen().loadThis();
		Arrays.stream(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.loadStream();
		final Handle handle = new Handle(Opcodes.H_INVOKEVIRTUAL, renderer.root().selfType().getInternalName(),
				method.getName(), method.getDescriptor(), false);
		renderer.methodGen().invokeDynamic("test",
				Type.getMethodDescriptor(A_PREDICATE_TYPE, Stream
						.concat(Stream.concat(Stream.of(renderer.root().selfType()),
								Arrays.stream(capturedVariables).map(LoadableValue::type)), Stream.of(streamType))
						.toArray(Type[]::new)),
				LAMBDA_METAFACTORY_BSM, Type.getMethodType(BOOLEAN_TYPE, A_OBJECT_TYPE), handle,
				Type.getMethodType(BOOLEAN_TYPE, type.asmType()));
		renderer.methodGen().invokeConstructor(A_FIXEDWITHCONDITION_TYPE, CTOR);
		renderer.methodGen().storeLocal(previousLocal);
	}

	@Override
	public boolean resolve(String name, NameDefinitions defs, Consumer<String> errorHandler) {
		this.name = name;
		return limitExpression.resolve(defs, errorHandler)
				& conditionExpression.resolve(defs.bind(parameter), errorHandler);
	}

	@Override
	public boolean resolveFunctions(Function<String, FunctionDefinition> definedFunctions,
			Consumer<String> errorHandler) {
		return limitExpression.resolveFunctions(definedFunctions, errorHandler)
				& conditionExpression.resolveFunctions(definedFunctions, errorHandler);
	}

	@Override
	public boolean typeCheck(Imyhat type, Consumer<String> errorHandler) {
		this.type = type;
		boolean limitok = limitExpression.typeCheck(errorHandler);
		boolean conditionok = conditionExpression.typeCheck(errorHandler);
		if (limitok && !limitExpression.type().isSame(Imyhat.INTEGER)) {
			limitExpression.typeError(Imyhat.INTEGER.name(), limitExpression.type(), errorHandler);
			limitok = false;
		}
		if (conditionok && !conditionExpression.type().isSame(Imyhat.BOOLEAN)) {
			conditionExpression.typeError(Imyhat.BOOLEAN.name(), conditionExpression.type(), errorHandler);
			conditionok = false;
		}
		return limitok & conditionok;
	}
}
