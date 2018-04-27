package ca.on.oicr.gsi.shesmu.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public class LetBuilder {
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});

	private final ClassVisitor classVisitor;
	private final Renderer createMethodGen;
	private final List<Type> ctorArgs = new ArrayList<>();

	private final List<Consumer<GeneratorAdapter>> ctorWrites = new ArrayList<>();

	private final Type letType;

	public LetBuilder(RootBuilder owner, Type letType, Renderer createMethodGen) {
		this.letType = letType;
		this.createMethodGen = createMethodGen;
		classVisitor = owner.createClassVisitor();
		classVisitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, letType.getInternalName(), null,
				A_OBJECT_TYPE.getInternalName(), null);
		createMethodGen.methodGen().visitCode();
		createMethodGen.methodGen().newInstance(letType);
		createMethodGen.methodGen().dup();

	}

	public void add(Type fieldType, String name, Consumer<Renderer> loadValue) {
		final int ctorIndex = ctorArgs.size();
		ctorArgs.add(fieldType);
		ctorWrites.add(ctorMethod -> {
			ctorMethod.loadThis();
			ctorMethod.loadArg(ctorIndex);
			ctorMethod.putField(letType, name, fieldType);
		});
		classVisitor.visitField(Opcodes.ACC_PRIVATE, name, fieldType.getDescriptor(), null, null).visitEnd();
		final GeneratorAdapter getter = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
				new Method(name, fieldType, new Type[] {}), null, null, classVisitor);
		getter.visitCode();
		getter.loadThis();
		getter.getField(letType, name, fieldType);
		getter.returnValue();
		getter.visitMaxs(0, 0);
		getter.visitEnd();
		loadValue.accept(createMethodGen);
	}

	public void finish() {
		final Method ctorType = new Method("<init>", Type.VOID_TYPE, ctorArgs.stream().toArray(Type[]::new));
		final GeneratorAdapter ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, ctorType, null, null, classVisitor);
		ctor.visitCode();
		ctor.loadThis();
		ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
		ctorWrites.forEach(action -> action.accept(ctor));
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(0, 0);
		ctor.visitEnd();

		createMethodGen.methodGen().invokeConstructor(letType, ctorType);
		createMethodGen.methodGen().returnValue();
		createMethodGen.methodGen().visitMaxs(0, 0);
		createMethodGen.methodGen().visitEnd();

		classVisitor.visitEnd();
	}

}
