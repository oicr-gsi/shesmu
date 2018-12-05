package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.SignatureVariable;

/**
 * Utility class to make generating Java-style lambda using
 * {@link LambdaMetafactory} more easily.
 */
public final class LambdaBuilder {

	public enum AccessMode {
		BOXED, ERASED, REAL
	}

	public interface LambdaType {
		Type interfaceType();

		String methodName();

		Stream<Type> parameterTypes(AccessMode accessMode);

		Type returnType(AccessMode accessMode);
	}

	private static final Type A_BICONSUMER_TYPE = Type.getType(BiConsumer.class);
	private static final Type A_BIFUNCTION_TYPE = Type.getType(BiFunction.class);
	private static final Type A_BIPREDICATE_TYPE = Type.getType(BiPredicate.class);
	private static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);

	private static final Type A_FUNCTION_TYPE = Type.getType(Function.class);

	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Type A_PREDICATE_TYPE = Type.getType(Predicate.class);

	private static final Type A_TO_INT_FUNCTION_TYPE = Type.getType(ToIntFunction.class);
	private static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);

	private static void assertNonPrimitive(Type type) {
		if (type.getSort() != Type.OBJECT && type.getSort() != Type.ARRAY) {
			throw new IllegalArgumentException(
					String.format("The type %s must be an reference type to be used in a lambda.", type));
		}
	}

	public static LambdaType biconsumer(Imyhat parameter1Type, Imyhat parameter2Type) {
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_BICONSUMER_TYPE;
			}

			@Override
			public String methodName() {
				return "accept";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
					return Stream.of(parameter1Type.boxedAsmType(), parameter2Type.boxedAsmType());
				case ERASED:
					return Stream.of(A_OBJECT_TYPE, A_OBJECT_TYPE);
				case REAL:
					return Stream.of(parameter1Type.asmType(), parameter2Type.asmType());
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				return VOID_TYPE;
			}

		};
	}

	public static LambdaType biconsumer(Type parameter1Type, Type parameter2Type) {
		assertNonPrimitive(parameter1Type);
		assertNonPrimitive(parameter2Type);
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_BICONSUMER_TYPE;
			}

			@Override
			public String methodName() {
				return "accept";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameter1Type, parameter2Type);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE, A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				return VOID_TYPE;
			}

		};
	}

	public static LambdaType bifunction(Type returnType, Type parameter1Type, Type parameter2Type) {
		assertNonPrimitive(parameter1Type);
		assertNonPrimitive(parameter2Type);
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_BIFUNCTION_TYPE;
			}

			@Override
			public String methodName() {
				return "apply";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameter1Type, parameter2Type);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE, A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return returnType;
				case ERASED:
					return A_OBJECT_TYPE;
				default:
					throw new UnsupportedOperationException();

				}
			}

		};
	}

	public static LambdaType bipredicate(Type parameter1Type, Type parameter2Type) {
		assertNonPrimitive(parameter1Type);
		assertNonPrimitive(parameter2Type);
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_BIPREDICATE_TYPE;
			}

			@Override
			public String methodName() {
				return "test";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameter1Type, parameter2Type);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE, A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				return BOOLEAN_TYPE;
			}

		};
	}

	public static LambdaType consumer(Type parameterType) {
		assertNonPrimitive(parameterType);
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_CONSUMER_TYPE;
			}

			@Override
			public String methodName() {
				return "accept";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameterType);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				return VOID_TYPE;
			}
		};
	}

	public static LambdaType function(Imyhat returnType, Imyhat parameterType) {
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_FUNCTION_TYPE;
			}

			@Override
			public String methodName() {
				return "apply";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
					return Stream.of(parameterType.boxedAsmType());
				case ERASED:
					return Stream.of(A_OBJECT_TYPE);
				case REAL:
					return Stream.of(parameterType.asmType());
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
					return returnType.boxedAsmType();
				case ERASED:
					return A_OBJECT_TYPE;
				case REAL:
					return returnType.asmType();
				default:
					throw new UnsupportedOperationException();

				}
			}

		};
	}

	public static LambdaType function(Imyhat returnType, Type parameterType) {
		assertNonPrimitive(parameterType);
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_FUNCTION_TYPE;
			}

			@Override
			public String methodName() {
				return "apply";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameterType);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
					return returnType.boxedAsmType();
				case ERASED:
					return A_OBJECT_TYPE;
				case REAL:
					return returnType.asmType();
				default:
					throw new UnsupportedOperationException();

				}
			}

		};
	}

	public static LambdaType function(Type returnType, Type parameterType) {
		assertNonPrimitive(returnType);
		assertNonPrimitive(parameterType);
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_FUNCTION_TYPE;
			}

			@Override
			public String methodName() {
				return "apply";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameterType);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return returnType;
				case ERASED:
					return A_OBJECT_TYPE;
				default:
					throw new UnsupportedOperationException();

				}
			}

		};
	}

	public static LambdaType predicate(Imyhat parameterType) {
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_PREDICATE_TYPE;
			}

			@Override
			public String methodName() {
				return "test";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
					return Stream.of(parameterType.boxedAsmType());
				case ERASED:
					return Stream.of(A_OBJECT_TYPE);
				case REAL:
					return Stream.of(parameterType.asmType());
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				return BOOLEAN_TYPE;
			}

		};
	}

	public static LambdaType predicate(Type parameterType) {
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_PREDICATE_TYPE;
			}

			@Override
			public String methodName() {
				return "test";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameterType);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();
				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				return BOOLEAN_TYPE;
			}

		};
	}

	/**
	 * Creates a lambda that calls a constructor. The return type of the lambda must
	 * be the type being constructed.
	 */
	public static void pushNew(Renderer renderer, LambdaType lambda) {
		assertNonPrimitive(lambda.returnType(AccessMode.REAL));
		renderer.methodGen().invokeDynamic(lambda.methodName(), Type.getMethodDescriptor(lambda.interfaceType()),
				LAMBDA_METAFACTORY_BSM,
				Type.getMethodType(lambda.returnType(AccessMode.ERASED),
						lambda.parameterTypes(AccessMode.ERASED).toArray(Type[]::new)),
				new Handle(Opcodes.H_NEWINVOKESPECIAL, lambda.returnType(AccessMode.REAL).getInternalName(), "<init>",
						Type.getMethodDescriptor(VOID_TYPE,
								lambda.parameterTypes(AccessMode.REAL).toArray(Type[]::new)),
						false),
				Type.getMethodType(lambda.returnType(AccessMode.BOXED),
						lambda.parameterTypes(AccessMode.BOXED).toArray(Type[]::new)));
	}

	/**
	 * Create a lambda that calls a virtual method. The first type of the first
	 * parameter must be the class in which the method is defined.
	 */
	public static void pushVirtual(Renderer renderer, String methodName, LambdaType lambda) {
		Type selfType = lambda.parameterTypes(AccessMode.REAL).findFirst().orElseThrow(IllegalArgumentException::new);
		assertNonPrimitive(selfType);
		renderer.methodGen().invokeDynamic(lambda.methodName(), Type.getMethodDescriptor(lambda.interfaceType()),
				LAMBDA_METAFACTORY_BSM,
				Type.getMethodType(lambda.returnType(AccessMode.ERASED),
						lambda.parameterTypes(AccessMode.ERASED).toArray(Type[]::new)),
				new Handle(Opcodes.H_INVOKEVIRTUAL, selfType.getInternalName(), methodName,
						Type.getMethodDescriptor(lambda.returnType(AccessMode.REAL),
								lambda.parameterTypes(AccessMode.REAL).skip(1).toArray(Type[]::new)),
						false),
				Type.getMethodType(lambda.returnType(AccessMode.BOXED),
						lambda.parameterTypes(AccessMode.BOXED).toArray(Type[]::new)));
	}

	public static LambdaType toIntFunction(Type parameterType) {
		assertNonPrimitive(parameterType);
		return new LambdaType() {

			@Override
			public Type interfaceType() {
				return A_TO_INT_FUNCTION_TYPE;
			}

			@Override
			public String methodName() {
				return "applyAsInt";
			}

			@Override
			public Stream<Type> parameterTypes(AccessMode accessMode) {
				switch (accessMode) {
				case BOXED:
				case REAL:
					return Stream.of(parameterType);
				case ERASED:
					return Stream.of(A_OBJECT_TYPE);
				default:
					throw new UnsupportedOperationException();

				}
			}

			@Override
			public Type returnType(AccessMode accessMode) {
				return INT_TYPE;
			}

		};
	}

	private final LoadableValue[] capturedVariables;

	private final Type[] captureTypes;

	private final LambdaType lambda;

	private final Method method;

	private final RootBuilder owner;

	/**
	 * Create a new lambda for a function
	 * 
	 * @param owner
	 *            the class builder owning the lambda
	 * @param methodName
	 *            the name of the method to be created
	 * @param lambda
	 *            the type of the lambda that must be generated
	 * @param capturedVariables
	 *            any variables present in the caller that must be captured for use
	 *            inside the lambda
	 */
	public LambdaBuilder(RootBuilder owner, String methodName, LambdaType lambda, LoadableValue... capturedVariables) {
		this.owner = owner;
		this.lambda = lambda;
		this.capturedVariables = capturedVariables;
		method = new Method(methodName, lambda.returnType(AccessMode.REAL),
				Stream.concat(Arrays.stream(capturedVariables).map(LoadableValue::type),
						lambda.parameterTypes(AccessMode.REAL)).toArray(Type[]::new));
		captureTypes = Stream
				.concat(Stream.of(owner.selfType()), Arrays.stream(capturedVariables).map(LoadableValue::type))
				.toArray(Type[]::new);
	}

	/**
	 * Get the method that was generated
	 */
	public Method method() {
		return method;
	}

	/**
	 * Create a new method generator to write the bytecode for the body of the
	 * lambda.
	 */
	public GeneratorAdapter methodGen() {
		return new GeneratorAdapter(Opcodes.ACC_PRIVATE, method, null, null, owner.classVisitor);
	}

	/**
	 * Push a copy of the lambda onto the stack. The captured values passed in the
	 * constructor must exist in this context.
	 */
	public void push(Renderer renderer) {
		renderer.methodGen().loadThis();
		Stream.of(capturedVariables).forEach(var -> var.accept(renderer));
		renderer.methodGen().invokeDynamic(lambda.methodName(),
				Type.getMethodDescriptor(lambda.interfaceType(), captureTypes), LAMBDA_METAFACTORY_BSM,
				Type.getMethodType(lambda.returnType(AccessMode.ERASED),
						lambda.parameterTypes(AccessMode.ERASED).toArray(Type[]::new)),
				new Handle(Opcodes.H_INVOKEVIRTUAL, owner.selfType().getInternalName(), method.getName(),
						method.getDescriptor(), false),
				Type.getMethodType(lambda.returnType(AccessMode.BOXED),
						lambda.parameterTypes(AccessMode.BOXED).toArray(Type[]::new)));
	}

	/**
	 * Create a new renderer (method generator) for the body of the lambda.
	 * 
	 * @param streamType
	 *            the type of the stream value
	 * @param signerEmitter
	 *            the signature loader/generator
	 */
	public Renderer renderer(Type streamType, BiConsumer<SignatureVariable, Renderer> signerEmitter) {
		return renderer(streamType, 0, signerEmitter);
	}

	/**
	 * Create a new renderer (method generator) for the body of the lambda.
	 * 
	 * @param streamType
	 *            the type of the stream value
	 * @param streamOffset
	 *            the number of items after the last capture for the parameter that
	 *            contains the stream value. Normally 0.
	 * @param signerEmitter
	 *            the signature loader/generator
	 */
	public Renderer renderer(Type streamType, int streamOffset, BiConsumer<SignatureVariable, Renderer> signerEmitter) {
		return new Renderer(owner, methodGen(), capturedVariables.length + streamOffset, streamType,
				RootBuilder.proxyCaptured(0, capturedVariables), signerEmitter);
	}

}
