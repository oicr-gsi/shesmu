package ca.on.oicr.gsi.shesmu.util.function;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;

public class FunctionForInstance implements FunctionDefinition {
	public interface FinishBind<T> {
		FunctionForInstance bind(T instance, Object... args);
	}

	private static final String BSM_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(CallSite.class),
			Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class));
	private static Map<String, CallSite> callsites = new WeakHashMap<>();

	private static final String SELF_NAME = Type.getType(FunctionForInstance.class).getInternalName();
	private static final AtomicInteger TOKEN_SOURCE = new AtomicInteger();

	public static <T> FunctionForInstance bind(Class<?> owner, Function<MethodType, MethodHandle> find, String name,
			String description, Imyhat returnType, FunctionParameter... parameterTypes) {
		return new FunctionForInstance(new ConstantCallSite(find.apply(methodTypeByImyhat(returnType, parameterTypes))),
				name, description, returnType, parameterTypes);

	}

	public static <T> FunctionForInstance bind(Lookup lookup, Class<T> owner, T instance, String methodName,
			String name, String description, Imyhat returnType, FunctionParameter... parameterTypes)
			throws NoSuchMethodException, IllegalAccessException {
		return new FunctionForInstance(
				new ConstantCallSite(
						findVirtualFor(lookup, owner, methodName, returnType, parameterTypes).bindTo(instance)),
				name, description, returnType, parameterTypes);
	}

	public static CallSite bootstrap(Lookup lookup, String methodName, MethodType methodType) {
		return callsites.get(methodName);
	}

	public static MethodHandle findVirtualFor(Lookup lookup, Class<?> clazz, String methodName, Imyhat returnType,
			FunctionParameter... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
		return lookup.findVirtual(clazz, methodName, methodTypeByImyhat(returnType, parameterTypes));
	}

	public static MethodType methodTypeByImyhat(Imyhat returnType, FunctionParameter... parameterTypes) {
		return MethodType.methodType(returnType.javaType(),
				Stream.of(parameterTypes).map(p -> p.type().javaType()).toArray(Class[]::new));
	}

	public static <T> FinishBind<T> startBind(Lookup lookup, Class<T> owner, String methodName, String name,
			String description, Imyhat returnType, FunctionParameter... parameterTypes)
			throws NoSuchMethodException, IllegalAccessException {
		final MethodHandle method = findVirtualFor(lookup, owner, methodName, returnType, parameterTypes);
		return (instance, args) -> new FunctionForInstance(new ConstantCallSite(method.bindTo(instance)),
				String.format(name, args), String.format(description, args), returnType, parameterTypes);

	}

	private final String description;

	private final String name;

	private final FunctionParameter[] parameterTypes;

	private final Imyhat returnType;
	private final String token;

	public FunctionForInstance(CallSite callsite, String name, String description, Imyhat returnType,
			FunctionParameter... parameterTypes) {
		super();
		this.name = name;
		this.description = description;
		this.returnType = returnType;
		this.parameterTypes = parameterTypes;
		token = String.format("i%d", TOKEN_SOURCE.getAndIncrement());
		callsites.put(token, callsite);
	}

	public FunctionForInstance(Lookup lookup, String methodName, String name, String description, Imyhat returnType,
			FunctionParameter... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
		super();
		this.name = name;
		this.description = description;
		this.returnType = returnType;
		this.parameterTypes = parameterTypes;
		token = String.format("i%d", TOKEN_SOURCE.getAndIncrement());
		callsites.put(token, new ConstantCallSite(
				findVirtualFor(lookup, getClass(), methodName, returnType, parameterTypes).bindTo(this)));
	}

	@Override
	public String description() {
		return description;
	}

	@Override
	public final String name() {
		return name;
	}

	@Override
	public final Stream<FunctionParameter> parameters() {
		return Stream.of(parameterTypes);
	}

	@Override
	public void render(GeneratorAdapter methodGen) {
		methodGen.invokeDynamic(token,
				Type.getMethodDescriptor(returnType.asmType(),
						Stream.of(parameterTypes).map(p -> p.type().asmType()).toArray(Type[]::new)),
				new Handle(Opcodes.H_INVOKESTATIC, SELF_NAME, "bootstrap", BSM_DESCRIPTOR, false));

	}

	@Override
	public final void renderStart(GeneratorAdapter methodGen) {
		// None required.
	}

	@Override
	public final Imyhat returnType() {
		return returnType;
	}
}
