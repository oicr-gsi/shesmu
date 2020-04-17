package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Build a new class for holding the new variables defined by a <tt>Join</tt> clause */
public class JoinBuilder {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});

  private final ClassVisitor classVisitor;

  private final Renderer innerKeyMethod;

  private final Type innerType;

  private final Type joinType;

  private final Renderer outerKeyMethod;

  private final Type outerType;

  public JoinBuilder(
      RootBuilder owner,
      Type joinType,
      Type outerType,
      Type innerType,
      Renderer outerKeyMethod,
      Renderer innerKeyMethod) {
    this.joinType = joinType;
    this.outerType = outerType;
    this.innerType = innerType;
    this.outerKeyMethod = outerKeyMethod;
    this.innerKeyMethod = innerKeyMethod;
    classVisitor = owner.createClassVisitor();
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        joinType.getInternalName(),
        null,
        A_OBJECT_TYPE.getInternalName(),
        null);
    final Method ctorType = new Method("<init>", Type.VOID_TYPE, new Type[] {outerType, innerType});
    final GeneratorAdapter ctor =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, ctorType, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);

    ctor.loadThis();
    ctor.loadArg(0);
    ctor.putField(joinType, "outer", outerType);
    classVisitor
        .visitField(Opcodes.ACC_PRIVATE, "outer", outerType.getDescriptor(), null, null)
        .visitEnd();

    ctor.loadThis();
    ctor.loadArg(1);
    ctor.putField(joinType, "inner", innerType);
    classVisitor
        .visitField(Opcodes.ACC_PRIVATE, "inner", innerType.getDescriptor(), null, null)
        .visitEnd();

    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();
  }

  public void add(Target target, boolean outer) {
    add(target, target.name(), outer);
  }

  public void add(Target target, String alias, boolean outer) {
    final Type targetType = outer ? outerType : innerType;
    final Method getMethod =
        new Method(alias, target.type().apply(TypeUtils.TO_ASM), new Type[] {});
    final GeneratorAdapter getter =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, getMethod, null, null, classVisitor);
    getter.visitCode();
    getter.loadThis();
    getter.getField(joinType, outer ? "outer" : "inner", targetType);
    if (target instanceof InputVariable) {
      ((InputVariable) target).extract(getter);
    } else {
      getter.invokeVirtual(targetType, getMethod);
    }
    getter.returnValue();
    getter.visitMaxs(0, 0);
    getter.visitEnd();
  }

  public void finish() {
    classVisitor.visitEnd();
  }

  public Renderer innerKey() {
    return innerKeyMethod;
  }

  public Renderer outerKey() {
    return outerKeyMethod;
  }
}
