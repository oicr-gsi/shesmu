package ca.on.oicr.gsi.shesmu.function;

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
import ca.on.oicr.gsi.shesmu.Imyhat;

public class FunctionForInstance implements FunctionDefinition {

	private static final String BSM_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(CallSite.class),
			Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class));
	private static Map<String, CallSite> callsites = new WeakHashMap<>();

	private static final String SELF_NAME = Type.getType(FunctionForInstance.class).getInternalName();
	private static final AtomicInteger TOKEN_SOURCE = new AtomicInteger();

	public static <T> FunctionForInstance bind(Class<?> owner, Function<MethodType, MethodHandle> find, String name,
			String description, Imyhat returnType, Imyhat... parameterTypes) {
		return new FunctionForInstance(new ConstantCallSite(find.apply(methodTypeByImyhat(returnType, parameterTypes))),
				name, description, returnType, parameterTypes);

	}

	public static <T> FunctionForInstance bind(Lookup lookup, Class<T> owner, T instance, String methodName,
			String name, String description, Imyhat returnType, Imyhat... parameterTypes)
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
			Imyhat... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
		return lookup.findVirtual(clazz, methodName, methodTypeByImyhat(returnType, parameterTypes));
	}

	public static MethodType methodTypeByImyhat(Imyhat returnType, Imyhat... parameterTypes) {
		return MethodType.methodType(returnType.javaType(),
				Stream.of(parameterTypes).map(Imyhat::javaType).toArray(Class[]::new));
	}

	private final String name;

	private final Imyhat[] parameterTypes;

	private final Imyhat returnType;

	private final String token;
	private final String description;

	public FunctionForInstance(CallSite callsite, String name, String description, Imyhat returnType,
			Imyhat... parameterTypes) {
		super();
		this.name = name;
		this.description = description;
		this.returnType = returnType;
		this.parameterTypes = parameterTypes;
		token = String.format("i%d", TOKEN_SOURCE.getAndIncrement());
		callsites.put(token, callsite);
	}

	public FunctionForInstance(Lookup lookup, String methodName, String name, String description, Imyhat returnType,
			Imyhat... parameterTypes) throws NoSuchMethodException, IllegalAccessException {
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
	public final String name() {
		return name;
	}

	@Override
	public void render(GeneratorAdapter methodGen) {
		methodGen.invokeDynamic(token,
				Type.getMethodDescriptor(returnType.asmType(),
						Stream.of(parameterTypes).map(Imyhat::asmType).toArray(Type[]::new)),
				new Handle(Opcodes.H_INVOKESTATIC, SELF_NAME, "bootstrap", BSM_DESCRIPTOR, false));

	}

	@Override
	public final Imyhat returnType() {
		return returnType;
	}

	@Override
	public final Stream<Imyhat> types() {
		return Stream.of(parameterTypes);
	}

	@Override
	public String description() {
		return description;
	}
}
