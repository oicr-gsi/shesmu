package ca.on.oicr.gsi.shesmu.util.definitions;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.WatchedFileListener;
import ca.on.oicr.gsi.status.ConfigurationSection;

/**
 * A source of actions, constants, and functions based on configuration files
 * where every configuration file can create arbitrarily many definitions.
 *
 * @param <T>
 *            the configuration file representation
 */
public abstract class FileBackedArbitraryDefinitionRepository<T extends FileBackedConfiguration>
		implements DefinitionRepository {
	private class Wrapper implements WatchedFileListener, UserDefiner {
		private final Map<String, ActionDefinition> actions = new ConcurrentHashMap<>();
		private final Map<String, ConstantDefinition> constants = new ConcurrentHashMap<>();
		private boolean dead;
		private final Map<String, FunctionDefinition> functions = new ConcurrentHashMap<>();
		private final T instance;

		public Wrapper(Path path) {
			instance = ctor.apply(path, this);
		}

		public Stream<ActionDefinition> actions() {
			return actions.values().stream();
		}

		@Override
		public void clearActions() {
			actions.clear();
		}

		@Override
		public void clearConstants() {
			constants.clear();
		}

		@Override
		public void clearFunctions() {
			functions.clear();
		}

		public ConfigurationSection configuration() {
			return instance.configuration();
		}

		public Stream<ConstantDefinition> constants() {
			return constants.values().stream();
		}

		@Override
		public <A extends Action> void defineAction(String name, String description, Class<A> clazz,
				Supplier<A> supplier, Stream<ActionParameterDefinition> parameters) {
			final MethodHandle handle = MH_SUPPLIER_GET//
					.bindTo(supplier)//
					.asType(MethodType.methodType(clazz));
			final String fixedName = name + " " + clazz.getCanonicalName().replace('.', '·');
			register(fixedName, handle);
			actions.put(name, new ActionDefinition(name, Type.getType(clazz), description, //
					Stream.concat(parameters, AnnotationUtils.findActionDefinitionsByAnnotation(clazz))) {

				@Override
				public void initialize(GeneratorAdapter methodGen) {
					methodGen.invokeDynamic(fixedName, Type.getMethodDescriptor(Type.getType(clazz)), HANDLE_BSM);
				}
			});

		}

		@Override
		public void defineConstant(String name, String description, Imyhat type, Object value) {
			final MethodHandle handle = MethodHandles.constant(type.javaType(), value);

			final String fixedName = name + " " + type.descriptor();
			register(fixedName, handle);
			constants.put(name, new ConstantDefinition(name, type, description) {

				@Override
				protected void load(GeneratorAdapter methodGen) {
					methodGen.invokeDynamic(fixedName, Type.getMethodDescriptor(type.asmType()), HANDLE_BSM);
				}
			});
		}

		@Override
		public <R> void defineConstant(String name, String description, TypeGlue<R> returnType, Supplier<R> constant) {
			final MethodHandle handle = MH_SUPPLIER_GET//
					.bindTo(constant)//
					.asType(MethodType.methodType(returnType.type().javaType()));

			final String fixedName = name + " " + returnType.type().descriptor();
			register(fixedName, handle);
			constants.put(name, new ConstantDefinition(name, returnType.type(), description) {

				@Override
				protected void load(GeneratorAdapter methodGen) {
					methodGen.invokeDynamic(fixedName, Type.getMethodDescriptor(returnType.type().asmType()),
							HANDLE_BSM);
				}
			});
		}

		@Override
		public void defineFunction(String name, String description, Imyhat returnType, VariadicFunction function,
				FunctionParameter... parameters) {
			final MethodHandle handle = MH_VARIADICFUNCTION_APPLY//
					.bindTo(function)//
					.asCollector(Object[].class, parameters.length)//
					.asType(MethodType.methodType(returnType.javaType(), //
							Stream.of(parameters)//
									.map(p -> p.type().javaType())//
									.toArray(Class[]::new)));
			final String fixedName = Stream.of(parameters)//
					.map(p -> p.type().descriptor())//
					.collect(Collectors.joining(" ", name + " ", " " + returnType.descriptor()));
			final String descriptor = Type.getMethodDescriptor(returnType.asmType(), //
					Stream.of(parameters)//
							.map(p -> p.type().asmType())//
							.toArray(Type[]::new));
			register(fixedName, handle);
			functions.put(name, new FunctionDefinition() {

				@Override
				public String description() {
					return description;
				}

				@Override
				public String name() {
					return name;
				}

				@Override
				public Stream<FunctionParameter> parameters() {
					return Stream.of(parameters);
				}

				@Override
				public void render(GeneratorAdapter methodGen) {
					methodGen.invokeDynamic(fixedName, descriptor, HANDLE_BSM);
				}

				@Override
				public void renderStart(GeneratorAdapter methodGen) {
					// Nothing to do.
				}

				@Override
				public Imyhat returnType() {
					return returnType;
				}
			});
		}

		@Override
		public <A, R> void defineFunction(String name, String description, TypeGlue<R> returnType,
				String parameterDescription, TypeGlue<A> parameterType, Function<A, R> function) {
			final MethodHandle handle = MH_FUNCTION_APPLY//
					.bindTo(function)//
					.asType(MethodType.methodType(returnType.type().javaType(), //
							parameterType.type().javaType()));
			final String fixedName = name + " " + parameterType.type().descriptor() + " "
					+ returnType.type().descriptor();
			register(fixedName, handle);
			functions.put(name, new FunctionDefinition() {

				@Override
				public String description() {
					return description;
				}

				@Override
				public String name() {
					return name;
				}

				@Override
				public Stream<FunctionParameter> parameters() {
					return Stream.of(new FunctionParameter(parameterDescription, parameterType.type()));
				}

				@Override
				public void render(GeneratorAdapter methodGen) {
					methodGen.invokeDynamic(fixedName,
							Type.getMethodDescriptor(returnType.type().asmType(), parameterType.type().asmType()),
							HANDLE_BSM);
				}

				@Override
				public void renderStart(GeneratorAdapter methodGen) {
					// Nothing to do.
				}

				@Override
				public Imyhat returnType() {
					return returnType.type();
				}
			});
		}

		@Override
		public <A, B, R> void defineFunction(String name, String description, TypeGlue<R> returnType,
				String parameter1Description, TypeGlue<A> parameter1Type, String parameter2Description,
				TypeGlue<B> parameter2Type, BiFunction<A, B, R> function) {
			final MethodHandle handle = MH_BIFUNCTION_APPLY//
					.bindTo(function)//
					.asType(MethodType.methodType(returnType.type().javaType(), //
							parameter1Type.type().javaType(), //
							parameter2Type.type().javaType()));
			final String fixedName = name + " " + parameter1Type.type().descriptor() + " "
					+ parameter2Type.type().descriptor() + " " + returnType.type().descriptor();
			register(fixedName, handle);
			functions.put(name, new FunctionDefinition() {

				@Override
				public String description() {
					return description;
				}

				@Override
				public String name() {
					return name;
				}

				@Override
				public Stream<FunctionParameter> parameters() {
					return Stream.of(new FunctionParameter(parameter1Description, parameter1Type.type()),
							new FunctionParameter(parameter2Description, parameter2Type.type()));
				}

				@Override
				public void render(GeneratorAdapter methodGen) {
					methodGen.invokeDynamic(fixedName, Type.getMethodDescriptor(returnType.type().asmType(),
							parameter1Type.type().asmType(), parameter2Type.type().asmType()), HANDLE_BSM);
				}

				@Override
				public void renderStart(GeneratorAdapter methodGen) {
					// Nothing to do.
				}

				@Override
				public Imyhat returnType() {
					return returnType.type();
				}
			});
		}

		public Stream<FunctionDefinition> functions() {
			return functions.values().stream();
		}

		private void register(String fixedName, MethodHandle handle) {
			if (!dead) {
				REGISTRY.computeIfAbsent(fixedName, s -> new MutableCallSite(handle)).setTarget(handle);
			}
		}

		@Override
		public void start() {
			instance.start();
		}

		@Override
		public void stop() {
			dead = true;
			instance.stop();
		}

		@Override
		public Optional<Integer> update() {
			return instance.update();
		}
	}

	private static final String BSM_DESCRIPTOR = Type.getMethodDescriptor(Type.getType(CallSite.class),
			Type.getType(MethodHandles.Lookup.class), Type.getType(String.class), Type.getType(MethodType.class));

	private static final Handle HANDLE_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(FileBackedArbitraryDefinitionRepository.class).getInternalName(), "bootstrap", BSM_DESCRIPTOR,
			false);

	private static final MethodHandle MH_BIFUNCTION_APPLY;

	private static final MethodHandle MH_FUNCTION_APPLY;

	private static final MethodHandle MH_SUPPLIER_GET;

	private static final MethodHandle MH_VARIADICFUNCTION_APPLY;

	private static final Map<String, MutableCallSite> REGISTRY = new ConcurrentHashMap<>();
	public static final TypeGlue<String> STRING = new TypeGlue<String>() {

		@Override
		public Imyhat type() {
			return Imyhat.STRING;
		}

	};
	static {
		try {
			MH_SUPPLIER_GET = MethodHandles.publicLookup().findVirtual(Supplier.class, "get",
					MethodType.methodType(Object.class));
			MH_FUNCTION_APPLY = MethodHandles.publicLookup().findVirtual(Function.class, "apply",
					MethodType.methodType(Object.class, Object.class));
			MH_BIFUNCTION_APPLY = MethodHandles.publicLookup().findVirtual(BiFunction.class, "apply",
					MethodType.methodType(Object.class, Object.class, Object.class));
			MH_VARIADICFUNCTION_APPLY = MethodHandles.publicLookup().findVirtual(VariadicFunction.class, "apply",
					MethodType.methodType(Object.class, Object[].class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	public static CallSite bootstrap(Lookup lookup, String methodName, MethodType methodType) {
		final CallSite callsite = REGISTRY.get(methodName);
		return callsite;
	}

	public static Stream<Pair<String, MethodType>> dump() {
		return REGISTRY.entrySet().stream()//
				.map(e -> new Pair<>(e.getKey(), e.getValue().type()));
	}

	private final AutoUpdatingDirectory<Wrapper> configuration;

	BiFunction<Path, UserDefiner, T> ctor;

	public FileBackedArbitraryDefinitionRepository(Class<T> clazz, String extension,
			BiFunction<Path, UserDefiner, T> ctor) {
		this.ctor = ctor;
		configuration = new AutoUpdatingDirectory<>(extension, Wrapper::new);
	}

	@Override
	public final Stream<ActionDefinition> actions() {
		return configuration.stream().flatMap(Wrapper::actions);
	}

	@Override
	public final Stream<ConstantDefinition> constants() {
		return configuration.stream().flatMap(Wrapper::constants);
	}

	@Override
	public final Stream<FunctionDefinition> functions() {
		return configuration.stream().flatMap(Wrapper::functions);
	}

	@Override
	public final Stream<ConfigurationSection> listConfiguration() {
		return configuration.stream().map(Wrapper::configuration);
	}

}
