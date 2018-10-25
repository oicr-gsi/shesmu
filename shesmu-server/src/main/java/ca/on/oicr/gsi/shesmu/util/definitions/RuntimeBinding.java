package ca.on.oicr.gsi.shesmu.util.definitions;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.FileBound;

/**
 * Create a way to call functions on an instance of a class
 *
 * @param <T>
 *            the class that will be injected into the bytecode
 */
public class RuntimeBinding<T extends FileBound> {

	private interface Binder<T> {
		T bind(Consumer<GeneratorAdapter> loadInstance, Object... formattingParameters);
	}

	public final class CustomBinding {
		private final String fileName;
		private final String instanceName;
		private final Consumer<GeneratorAdapter> loader;

		private CustomBinding(Consumer<GeneratorAdapter> loader, Object... args) {
			this.loader = loader;
			instanceName = args[0].toString();
			fileName = args[1].toString();
		}

		public String fileName() {
			return fileName;
		}

		public String instanceName() {
			return instanceName;
		}

		public void push(GeneratorAdapter methodGen) {
			loader.accept(methodGen);
		}

		public Type type() {
			return type;
		}
	}

	private class Finisher<S> implements Function<Binder<S>, S>, Consumer<GeneratorAdapter> {
		private final String fileName;
		private final String instanceName;

		public Finisher(T instance) {
			instanceName = RuntimeSupport.removeExtension(instance.fileName(), extension);
			this.fileName = instance.fileName().toString();
			final Pair<String, Class<?>> key = new Pair<>(instanceName, clazz);
			final MethodHandle handle = MethodHandles.constant(clazz, instance);
			REGISTRY.computeIfAbsent(key, k -> new MutableCallSite(handle)).setTarget(handle);

		}

		@Override
		public void accept(GeneratorAdapter methodGen) {
			methodGen.invokeDynamic("get", Type.getMethodDescriptor(type, new Type[0]),
					new Handle(Opcodes.H_INVOKESTATIC, SELF_NAME, "bootstrap", BSM_DESCRIPTOR, false), instanceName);
		}

		@Override
		public S apply(Binder<S> finisher) {
			return finisher.bind(this, instanceName, fileName);
		}

	}

	private static final String BSM_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(CallSite.class),
			Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class),
			Type.getType(String.class));
	private static final Map<Pair<String, Class<?>>, MutableCallSite> REGISTRY = new ConcurrentHashMap<>();

	private static final String SELF_NAME = Type.getType(RuntimeBinding.class).getInternalName();

	public static CallSite bootstrap(Lookup lookup, String methodName, MethodType methodType, String fileName) {
		return REGISTRY.get(new Pair<>(fileName, methodType.returnType()));
	}

	public static Stream<Pair<Pair<String, Class<?>>, MethodType>> dump() {
		return REGISTRY.entrySet().stream()//
				.map(e -> new Pair<>(e.getKey(), e.getValue().type()));
	}

	private final List<Binder<ActionDefinition>> actions = new ArrayList<>();
	private final Class<?> clazz;
	private final List<Binder<ConstantDefinition>> constants = new ArrayList<>();
	private final String extension;

	private final List<Binder<FunctionDefinition>> functions = new ArrayList<>();

	private final Type type;

	/**
	 * Create a new binding instance
	 *
	 * The resulting instance should be assigned to a static variable since it will
	 * be available for all time.
	 *
	 * @param clazz
	 *            the target class; this should be a unique non-generic class to
	 *            avoid collisions with other users of ths interface
	 * @param extension
	 *            the file name extension of input file
	 */
	public RuntimeBinding(Class<T> clazz, String extension) {
		this.clazz = clazz;
		this.extension = extension;
		type = Type.getType(clazz);
	}

	/**
	 * Define a new action attached to an instance
	 *
	 * @param name
	 *            the Shesmu identifier for this action where <tt>%1$s</tt> will be
	 *            the instance name
	 * @param actionClass
	 *            the action class to be instantiated
	 * @param description
	 *            the Shesmu documentation for this action where <tt>%1$s</tt> will
	 *            be the instance name and <tt>%2$s</tt> is the path
	 * @param parameters
	 *            the action's parameter definitions
	 */
	public RuntimeBinding<T> action(String name, Class<? extends Action> actionClass, String description,
			ActionParameterDefinition... parameters) {
		final Type actionType = Type.getType(actionClass);
		actions.add((loader, args) -> new ActionDefinition(String.format(name, args), actionType,
				String.format(description, args), Stream.of(parameters)) {

			@Override
			public void initialize(GeneratorAdapter methodGen) {
				methodGen.newInstance(actionType);
				methodGen.dup();
				loader.accept(methodGen);
				methodGen.invokeConstructor(actionType, new Method("<init>", Type.VOID_TYPE, new Type[] { type }));
			}
		});
		return this;
	}

	/**
	 * Create actions definitions for an instance
	 *
	 * @param instance
	 *            the instance to bind to
	 */
	public List<ActionDefinition> bindActions(T instance) {
		return actions.stream()//
				.map(new Finisher<>(instance))//
				.collect(Collectors.toList());
	}

	/**
	 * Create constant definitions for an instance
	 *
	 * @param instance
	 *            the instance to bind to
	 */
	public List<ConstantDefinition> bindConstants(T instance) {
		return constants.stream()//
				.map(new Finisher<>(instance))//
				.collect(Collectors.toList());
	}

	/**
	 * Create a binding for situations that are too complicated for the other
	 * situations
	 *
	 * @param instance
	 *            the instance to bind to
	 */
	public CustomBinding bindCustom(T instance) {
		return new Finisher<CustomBinding>(instance).apply(CustomBinding::new);
	}

	/**
	 * Create function definitions for an instance
	 *
	 * @param instance
	 *            the instance to bind to
	 */
	public List<FunctionDefinition> bindFunctions(T instance) {
		return functions.stream()//
				.map(new Finisher<>(instance))//
				.collect(Collectors.toList());
	}

	/**
	 * Define a new constant produced by an instance
	 *
	 * @param name
	 *            the Shesmu identifier for this constant where <tt>%1$s</tt> will
	 *            be the instance name
	 * @param methodName
	 *            the Java name of the method producing this constant; this method
	 *            must take no arguments
	 * @param constantType
	 *            the type of the constant; the Java method must return a compatible
	 *            type
	 * @param description
	 *            the Shesmu documentation for this constant where <tt>%1$s</tt>
	 *            will be the instance name and <tt>%2$s</tt> is the path
	 */
	public RuntimeBinding<T> constant(String name, String methodName, Imyhat constantType, String description) {
		constants.add((loader, args) -> new ConstantDefinition(String.format(name, args), constantType,
				String.format(description, args)) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				loader.accept(methodGen);
				methodGen.invokeVirtual(type, new Method(methodName, constantType.asmType(), new Type[0]));

			}
		});
		return this;
	}

	/**
	 * Define a new function attached to an instance
	 *
	 * The method provided must be a public instance method of this class
	 *
	 * @param name
	 *            the Shesmu identifier for this function where <tt>%1$s</tt> will
	 *            be the instance name
	 * @param methodName
	 *            the Java name of the method to be called; this method's arguments
	 *            must match the provided arguments
	 * @param returnType
	 *            the return type of the constant; the Java method must return a
	 *            compatible type
	 * @param description
	 *            the Shesmu documentation for this function where <tt>%1$s</tt>
	 *            will be the instance name and <tt>%2$s</tt> is the path
	 * @param parameters
	 *            the function parameters in Shesmu which must match the Java method
	 */
	public RuntimeBinding<T> function(String name, String methodName, Imyhat returnType, String description,
			FunctionParameter... parameters) {
		final Method method = new Method(methodName, returnType.asmType(), Stream.of(parameters)//
				.map(p -> p.type().asmType())//
				.toArray(Type[]::new));
		functions.add((loader, args) -> new FunctionDefinition() {
			private final String formattedDescription = String.format(description, args);
			private final String formattedName = String.format(name, args);

			@Override
			public String description() {
				return formattedDescription;
			}

			@Override
			public String name() {
				return formattedName;
			}

			@Override
			public Stream<FunctionParameter> parameters() {
				return Stream.of(parameters);
			}

			@Override
			public void render(GeneratorAdapter methodGen) {
				if (clazz.isInterface()) {
					methodGen.invokeInterface(type, method);
				} else {
					methodGen.invokeVirtual(type, method);
				}
			}

			@Override
			public void renderStart(GeneratorAdapter methodGen) {
				loader.accept(methodGen);
			}

			@Override
			public Imyhat returnType() {
				return returnType;
			}
		});
		return this;
	}

	/**
	 * Define a new constant produced by a static method that takes an instance
	 *
	 * @param name
	 *            the Shesmu identifier for this constant where <tt>%1$s</tt> will
	 *            be the instance name
	 * @param methodName
	 *            the Java name of the method producing this constant; this method
	 *            must take no arguments
	 * @param constantType
	 *            the type of the constant; the Java method must return a compatible
	 *            type
	 * @param description
	 *            the Shesmu documentation for this constant where <tt>%1$s</tt>
	 *            will be the instance name and <tt>%2$s</tt> is the path
	 */
	public RuntimeBinding<T> staticConstant(String name, Class<?> owner, String methodName, Imyhat constantType,
			String description) {
		constants.add((loader, args) -> new ConstantDefinition(String.format(name, args), constantType,
				String.format(description, args)) {

			@Override
			protected void load(GeneratorAdapter methodGen) {
				loader.accept(methodGen);
				methodGen.invokeStatic(Type.getType(owner),
						new Method(methodName, constantType.asmType(), new Type[] { type }));

			}
		});
		return this;
	}

	/**
	 * Define a new function attached to an instance
	 *
	 * The method provided must be a public static method in any class that takes an
	 * instance as the first argument
	 *
	 * @param name
	 *            the Shesmu identifier for this function where <tt>%1$s</tt> will
	 *            be the instance name
	 * @param owner
	 *            the class in which the method exists
	 * @param methodName
	 *            the Java name of the method to be called; this method's arguments
	 *            must be <T> followed by types matching the provided arguments
	 * @param returnType
	 *            the return type of the constant; the Java method must return a
	 *            compatible type
	 * @param description
	 *            the Shesmu documentation for this function where <tt>%1$s</tt>
	 *            will be the instance name and <tt>%2$s</tt> is the path
	 * @param parameters
	 *            the function parameters in Shesmu which must match the Java method
	 */

	public RuntimeBinding<T> staticFunction(String name, Class<?> owner, String methodName, Imyhat returnType,
			String description, FunctionParameter... parameters) {
		final Type ownerType = Type.getType(owner);
		final Method method = new Method(methodName, returnType.asmType(), //
				Stream.concat(//
						Stream.of(type), //
						Stream.of(parameters)//
								.map(p -> p.type().asmType()))//
						.toArray(Type[]::new));
		functions.add((loader, args) -> new FunctionDefinition() {
			private final String formattedDescription = String.format(description, args);
			private final String formattedName = String.format(name, args);

			@Override
			public String description() {
				return formattedDescription;
			}

			@Override
			public String name() {
				return formattedName;
			}

			@Override
			public Stream<FunctionParameter> parameters() {
				return Stream.of(parameters);
			}

			@Override
			public void render(GeneratorAdapter methodGen) {
				methodGen.invokeStatic(ownerType, method);
			}

			@Override
			public void renderStart(GeneratorAdapter methodGen) {
				loader.accept(methodGen);
			}

			@Override
			public Imyhat returnType() {
				return returnType;
			}
		});
		return this;
	}

}
