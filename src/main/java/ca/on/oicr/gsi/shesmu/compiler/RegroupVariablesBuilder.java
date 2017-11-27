package ca.on.oicr.gsi.shesmu.compiler;

import static org.objectweb.asm.Type.BOOLEAN_TYPE;
import static org.objectweb.asm.Type.INT_TYPE;
import static org.objectweb.asm.Type.VOID_TYPE;

import java.lang.invoke.LambdaMetafactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Helps to build a “Group” clause and the corresponding variable class
 */
public class RegroupVariablesBuilder {

	private class Collected extends Element {
		private final String fieldName;
		private final Consumer<Renderer> loader;
		private final Type valueType;

		public Collected(Type valueType, String fieldName, Consumer<Renderer> loader) {
			super();
			this.valueType = valueType;
			this.fieldName = fieldName;
			this.loader = loader;
		}

		@Override
		public void buildCollect() {
			collectRenderer.methodGen().loadArg(collectedSelfArgument);
			collectRenderer.methodGen().getField(self, fieldName, A_SET_TYPE);
			loader.accept(collectRenderer);
			collectRenderer.methodGen().box(valueType);
			collectRenderer.methodGen().invokeVirtual(A_SET_TYPE, METHOD_SET__ADD);
		}

		@Override
		public int buildConstructor(GeneratorAdapter ctor, int index) {
			ctor.loadThis();
			ctor.newInstance(A_HASHSET_TYPE);
			ctor.dup();
			ctor.invokeConstructor(A_HASHSET_TYPE, CTOR_DEFAULT);
			ctor.putField(self, fieldName, A_SET_TYPE);
			return index;
		}

		@Override
		public void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end) {
			// Collections are not included in equality.
		}

		@Override
		public void buildHashCode(GeneratorAdapter hashMethod) {
			// Collections are not included in the hash.
		}

		@Override
		public Type constructorType() {
			return null;
		}

		@Override
		public void loadConstructorArgument() {
			// No argument to constructor.

		}

	}

	private class Discriminator extends Element {
		private final String fieldName;
		private final Type fieldType;
		private final Consumer<Renderer> loader;

		public Discriminator(Type fieldType, String fieldName, Consumer<Renderer> loader) {
			super();
			this.fieldType = fieldType;
			this.fieldName = fieldName;
			this.loader = loader;
		}

		@Override
		public void buildCollect() {
			// No collection required
		}

		@Override
		public int buildConstructor(GeneratorAdapter ctor, int index) {
			ctor.loadThis();
			ctor.loadArg(index);
			ctor.putField(self, fieldName, fieldType);
			return index + 1;
		}

		@Override
		public void buildEquals(GeneratorAdapter method, int otherLocal, Label end) {
			method.loadThis();
			method.getField(self, fieldName, fieldType);
			method.loadLocal(otherLocal);
			method.getField(self, fieldName, fieldType);
			switch (fieldType.getSort()) {
			case Type.ARRAY:
			case Type.OBJECT:
				method.invokeVirtual(A_OBJECT_TYPE, METHOD_EQUALS);
				method.ifZCmp(GeneratorAdapter.EQ, end);
				break;
			default:
				method.ifCmp(fieldType, GeneratorAdapter.NE, end);
			}
		}

		@Override
		public void buildHashCode(GeneratorAdapter method) {
			method.push(31);
			method.math(GeneratorAdapter.MUL, INT_TYPE);
			method.loadThis();
			method.getField(self, fieldName, fieldType);
			switch (fieldType.getSort()) {
			case Type.ARRAY:
			case Type.OBJECT:
				method.invokeVirtual(A_OBJECT_TYPE, METHOD_HASH_CODE);
				break;
			default:
				method.cast(fieldType, INT_TYPE);
				break;
			}
			method.math(GeneratorAdapter.ADD, INT_TYPE);

		}

		@Override
		public Type constructorType() {
			return fieldType;
		}

		@Override
		public void loadConstructorArgument() {
			loader.accept(newRenderer);
		}

	}

	private abstract class Element {

		public abstract void buildCollect();

		public abstract int buildConstructor(GeneratorAdapter ctor, int index);

		public abstract void buildEquals(GeneratorAdapter methodGen, int otherLocal, Label end);

		public abstract void buildHashCode(GeneratorAdapter method);

		public abstract Type constructorType();

		public abstract void loadConstructorArgument();
	}

	private static final Type A_HASHSET_TYPE = Type.getType(HashSet.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_SET_TYPE = Type.getType(Set.class);
	private static final Method CTOR_DEFAULT = new Method("<init>", VOID_TYPE, new Type[] {});

	protected static final Handle LAMBDA_METAFACTORY_BSM = new Handle(Opcodes.H_INVOKESTATIC,
			Type.getType(LambdaMetafactory.class).getInternalName(), "metafactory",
			"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
			false);
	private static final Method METHOD_EQUALS = new Method("equals", BOOLEAN_TYPE, new Type[] { A_OBJECT_TYPE });
	private static final Method METHOD_HASH_CODE = new Method("hashCode", INT_TYPE, new Type[] {});
	private static final Method METHOD_SET__ADD = new Method("add", VOID_TYPE, new Type[] { A_OBJECT_TYPE });
	private final ClassVisitor classVisitor;
	public final int collectedSelfArgument;

	private final Renderer collectRenderer;

	private final List<Element> elements = new ArrayList<>();

	private final Renderer newRenderer;

	private final Type self;

	public RegroupVariablesBuilder(RootBuilder builder, String name, Renderer newMethodGen, Renderer collectedMethodGen,
			int collectedSelfArgument) {
		newRenderer = newMethodGen;
		collectRenderer = collectedMethodGen;
		this.collectedSelfArgument = collectedSelfArgument;
		self = Type.getObjectType(name);
		classVisitor = builder.createClassVisitor();
		classVisitor.visit(Opcodes.V1_8, 0, name, null, A_OBJECT_TYPE.getInternalName(), null);
		classVisitor.visitSource(builder.sourcePath(), null);
	}

	/**
	 * Add a new collection of values slurped during iteration
	 *
	 * @param valueType
	 *            the type of the values in the collection
	 * @param fieldName
	 *            the name of the variable for consumption by downstream uses
	 */
	public void addCollected(Type valueType, String fieldName, Consumer<Renderer> loader) {
		classVisitor.visitField(Opcodes.ACC_PRIVATE, fieldName, A_SET_TYPE.getDescriptor(), null, null).visitEnd();
		elements.add(new Collected(valueType, fieldName, loader));

		final GeneratorAdapter getMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
				new Method(fieldName, A_SET_TYPE, new Type[] {}), null, null, classVisitor);
		getMethod.visitCode();
		getMethod.loadThis();
		getMethod.getField(self, fieldName, A_SET_TYPE);
		getMethod.returnValue();
		getMethod.visitMaxs(0, 0);
		getMethod.visitEnd();
	}

	/**
	 * A single value to be added as part of the deduplication
	 *
	 * @param fieldType
	 *            the type of the value being added
	 * @param fieldName
	 *            the name of the variable for consumption by downstream uses
	 */
	public void addKey(Type fieldType, String fieldName, Consumer<Renderer> loader) {
		classVisitor.visitField(Opcodes.ACC_PRIVATE, fieldName, fieldType.getDescriptor(), null, null).visitEnd();
		elements.add(new Discriminator(fieldType, fieldName, loader));

		final GeneratorAdapter getMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC,
				new Method(fieldName, fieldType, new Type[] {}), null, null, classVisitor);
		getMethod.visitCode();
		getMethod.loadThis();
		getMethod.getField(self, fieldName, fieldType);
		getMethod.returnValue();
		getMethod.visitMaxs(0, 0);
		getMethod.visitEnd();
	}

	/**
	 * Generate the class completely
	 */
	public void finish() {
		final Method ctorType = new Method("<init>", VOID_TYPE,
				elements.stream().map(Element::constructorType).filter(Objects::nonNull).toArray(Type[]::new));
		final GeneratorAdapter ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, ctorType, null, null, classVisitor);
		ctor.visitCode();
		ctor.loadThis();
		ctor.invokeConstructor(A_OBJECT_TYPE, CTOR_DEFAULT);
		elements.stream().reduce(0, (index, element) -> element.buildConstructor(ctor, index), (a, b) -> {
			throw new UnsupportedOperationException();
		});
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(0, 0);
		ctor.visitEnd();

		final GeneratorAdapter hashMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_HASH_CODE, null, null,
				classVisitor);
		hashMethod.visitCode();
		hashMethod.push(0);
		elements.forEach(element -> element.buildHashCode(hashMethod));

		hashMethod.returnValue();
		hashMethod.visitMaxs(0, 0);
		hashMethod.visitEnd();

		final GeneratorAdapter equalsMethod = new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_EQUALS, null, null,
				classVisitor);
		equalsMethod.visitCode();
		equalsMethod.loadArg(0);
		equalsMethod.instanceOf(self);
		final Label equalsFalse = equalsMethod.newLabel();
		equalsMethod.ifZCmp(GeneratorAdapter.EQ, equalsFalse);
		final int equalsOtherLocal = equalsMethod.newLocal(self);
		equalsMethod.loadArg(0);
		equalsMethod.checkCast(self);
		equalsMethod.storeLocal(equalsOtherLocal);
		elements.forEach(element -> element.buildEquals(equalsMethod, equalsOtherLocal, equalsFalse));
		equalsMethod.push(true);
		equalsMethod.returnValue();
		equalsMethod.mark(equalsFalse);
		equalsMethod.push(false);
		equalsMethod.returnValue();
		equalsMethod.visitMaxs(0, 0);
		equalsMethod.visitEnd();

		newRenderer.methodGen().visitCode();
		newRenderer.methodGen().newInstance(self);
		newRenderer.methodGen().dup();
		elements.forEach(Element::loadConstructorArgument);
		newRenderer.methodGen().invokeConstructor(self, ctorType);
		newRenderer.methodGen().returnValue();
		newRenderer.methodGen().visitMaxs(0, 0);
		newRenderer.methodGen().visitEnd();

		collectRenderer.methodGen().visitCode();
		elements.forEach(Element::buildCollect);
		collectRenderer.methodGen().visitInsn(Opcodes.RETURN);
		collectRenderer.methodGen().visitMaxs(0, 0);
		collectRenderer.methodGen().visitEnd();

		classVisitor.visitEnd();
	}

}
