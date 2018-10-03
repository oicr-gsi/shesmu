package ca.on.oicr.gsi.shesmu.util.input;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.core.JsonGenerator;

import ca.on.oicr.gsi.shesmu.compiler.NameDefinitions.DefaultStreamTarget;
import ca.on.oicr.gsi.shesmu.util.server.BaseHotloadingCompiler;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.InputRepository;
import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
import ca.on.oicr.gsi.shesmu.compiler.Target;

/**
 * Create a new {@link InputFormatDefinition} that uses Java service loaders to
 * find implementation sources of input data and annotations to generate Shesmu
 * variables
 *
 * @param <V>
 *            the type for each “row”, decorated with {@link Export} annotations
 * @param <R>
 *            the type of the interface implemented by sources of input data
 */
public abstract class BaseInputFormatDefinition<V, R extends InputRepository<V>> extends InputFormatDefinition {
	private class WriterCompiler extends BaseHotloadingCompiler {

		public Class<Consumer<V>> create(Stream<Target> fields) {
			final Type self = Type.getObjectType("dyn/shesmu/Writer");
			final Type itemType = Type.getType(itemClass);
			final ClassVisitor classVisitor = createClassVisitor();
			classVisitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, self.getInternalName(), null,
					A_OBJECT_TYPE.getInternalName(), new String[] { A_CONSUMER_TYPE.getInternalName() });

			classVisitor.visitField(Opcodes.ACC_PRIVATE, "json", A_JSON_GENERATOR_TYPE.getDescriptor(), null, null)
					.visitEnd();

			final GeneratorAdapter ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR, null, null, classVisitor);
			ctor.visitCode();
			ctor.loadThis();
			ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
			ctor.loadThis();
			ctor.loadArg(0);
			ctor.putField(self, "json", A_JSON_GENERATOR_TYPE);
			ctor.visitInsn(Opcodes.RETURN);
			ctor.visitMaxs(0, 0);
			ctor.visitEnd();

			final GeneratorAdapter accept = new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_ACCEPT, null, null,
					classVisitor);
			accept.visitCode();
			final int local = accept.newLocal(itemType);
			accept.loadArg(0);
			accept.checkCast(itemType);
			accept.storeLocal(local);

			accept.loadThis();
			accept.getField(self, "json", A_JSON_GENERATOR_TYPE);
			accept.dup();
			accept.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_WRITE_OBJ_START);

			fields.forEach(field -> {
				accept.dup();
				accept.push(field.name());
				accept.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_WRITE_FIELD);
				accept.dup();
				accept.loadLocal(local);
				accept.invokeVirtual(itemType, new Method(field.name(), field.type().asmType(), new Type[] {}));
				field.type().streamJson(accept);
			});
			accept.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_WRITE_OBJ_END);
			accept.visitInsn(Opcodes.RETURN);
			accept.visitMaxs(0, 0);
			accept.visitEnd();
			classVisitor.visitEnd();

			try {
				@SuppressWarnings("unchecked")
				final Class<Consumer<V>> clazz = (Class<Consumer<V>>) loadClass(Consumer.class, self.getClassName());
				return clazz;
			} catch (final ClassNotFoundException e) {
				e.printStackTrace();
				return null;
			}
		}
	}

	private static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);
	private static final Type A_JSON_GENERATOR_TYPE = Type.getType(JsonGenerator.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] { A_JSON_GENERATOR_TYPE });
	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
	private static final Method METHOD_ACCEPT = new Method("accept", Type.VOID_TYPE, new Type[] { A_OBJECT_TYPE });
	private static final Method METHOD_WRITE_FIELD = new Method("writeFieldName", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE });
	private static final Method METHOD_WRITE_OBJ_END = new Method("writeEndObject", Type.VOID_TYPE, new Type[] {});
	private static final Method METHOD_WRITE_OBJ_START = new Method("writeStartObject", Type.VOID_TYPE, new Type[] {});
	private final Class<V> itemClass;
	private final ServiceLoader<R> loader;
	private final Target[] variables;

	private final Class<Consumer<V>> writer;

	public BaseInputFormatDefinition(String name, Class<V> itemClass, Class<R> repositoryClass) {
		super(name);
		this.itemClass = itemClass;
		loader = ServiceLoader.load(repositoryClass);
		variables = Arrays.stream(itemClass.getMethods()).flatMap(method -> {
			final Export[] exports = method.getAnnotationsByType(Export.class);
			if (exports.length == 1) {
				return Stream.of(new DefaultStreamTarget(method.getName(), Imyhat.parse(exports[0].type()),
						exports[0].signable()));
			}
			return Stream.empty();
		}).toArray(Target[]::new);
		writer = new WriterCompiler().create(Stream.of(variables));
	}

	@Override
	public final Stream<Target> baseStreamVariables() {
		return Arrays.stream(variables);
	}

	@Override
	public final Stream<LoadedConfiguration> configuration() {
		return StreamSupport.stream(loader.spliterator(), false).map(x -> x);
	}

	@Override
	public final <T> Stream<T> input(Class<T> clazz) {
		if (clazz.isAssignableFrom(itemClass)) {
			return stream().map(clazz::cast);
		}
		return Stream.empty();
	}

	@Override
	public final Class<?> itemClass() {
		return itemClass;
	}

	private Stream<V> stream() {
		return StreamSupport.stream(loader.spliterator(), false).flatMap(InputRepository::stream);
	}

	@Override
	public void write(JsonGenerator generator) throws IOException {
		try {
			stream().forEach(writer.getConstructor(JsonGenerator.class).newInstance(generator));
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
		}
	}

}
