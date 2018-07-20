package ca.on.oicr.gsi.shesmu.compiler;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;

/**
 * Helper class to hold state and context for bytecode generation.
 */
public class Renderer {
	private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Handle HANDLER_IMYHAT = new Handle(Opcodes.H_INVOKESTATIC, A_IMYHAT_TYPE.getInternalName(),
			"bootstrap", Type.getMethodDescriptor(Type.getType(CallSite.class),
					Type.getType(MethodHandles.Lookup.class), A_STRING_TYPE, Type.getType(MethodType.class)),
			false);

	private static final String METHOD_IMYHAT_DESC = Type.getMethodDescriptor(A_IMYHAT_TYPE);

	private final Map<String, LoadableValue> loadables;

	private final GeneratorAdapter methodGen;

	private final RootBuilder rootBuilder;

	private final int streamArg;

	private final Type streamType;

	public Renderer(RootBuilder rootBuilder, GeneratorAdapter methodGen, int streamArg, Type streamType,
			Stream<LoadableValue> loadables) {
		this.rootBuilder = rootBuilder;
		this.methodGen = methodGen;
		this.streamArg = streamArg;
		this.streamType = streamType;
		this.loadables = loadables.collect(Collectors.toMap(LoadableValue::name, Function.identity()));

	}

	public Stream<LoadableValue> allValues() {
		return loadables.values().stream();
	}

	public JavaStreamBuilder buildStream(Imyhat initialType) {
		return new JavaStreamBuilder(rootBuilder, this, streamType, rootBuilder.nextStreamId(), initialType);
	}

	/**
	 * Find a known variable by name and load it on the stack.
	 *
	 * @param name
	 */
	public void emitNamed(String name) {
		loadables.get(name).accept(this);
	}

	public void invokeInterfaceStatic(Type interfaceType, Method method) {
		methodGen.visitMethodInsn(Opcodes.INVOKESTATIC, interfaceType.getInternalName(), method.getName(),
				method.getDescriptor(), true);
	}

	public void loadImyhat(String signature) {
		methodGen.invokeDynamic(signature, METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
	}

	/**
	 * Load the current stream value on the stack
	 *
	 * This cannot be used in the contexts where the stream hasn't started.
	 */
	public void loadStream() {
		if (streamType == null) {
			throw new UnsupportedOperationException();
		}
		methodGen.loadArg(streamArg);
	}

	/**
	 * Write the line number into the debugger for future reference.
	 */
	public void mark(int line) {
		methodGen.visitLineNumber(line, methodGen.mark());
	}

	/**
	 * Get the method currently being written.
	 */
	public GeneratorAdapter methodGen() {
		return methodGen;
	}

	/**
	 * The the owner of this method
	 */
	public RootBuilder root() {
		return rootBuilder;
	}

	/**
	 * Get the type of the current stream variable
	 *
	 * This will vary if the stream has been grouped.
	 */
	public Type streamType() {
		return streamType;
	}
}
