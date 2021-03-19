package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.A_SIGNATURE_ACCESSOR_TYPE;
import static ca.on.oicr.gsi.shesmu.compiler.BaseOliveBuilder.SIGNER_ACCESSOR_NAME;

import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Build a new class for holding the new variables defined by a <tt>Join</tt> clause */
public class JoinBuilder {
  private class JoinHalfRenderer extends Renderer {
    private final GeneratorAdapter getter;
    private final boolean outer;
    private final Type targetType;

    public JoinHalfRenderer(GeneratorAdapter getter, boolean outer, LoadableValue signerAccessor) {
      super(
          JoinBuilder.this.innerKey().root(),
          getter,
          Stream.of(signerAccessor),
          JoinBuilder.this.innerKey().signerEmitter());
      this.getter = getter;
      this.outer = outer;
      targetType = outer ? outerType : innerType;
    }

    @Override
    public Renderer duplicate() {
      return new JoinHalfRenderer(getter, outer, this.getNamed(SIGNER_ACCESSOR_NAME));
    }

    @Override
    public void loadStream() {
      this.methodGen().loadThis();
      getter.getField(joinType, outer ? "outer" : "inner", targetType);
    }

    @Override
    public Type streamType() {
      return targetType;
    }
  }

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
    final var ctorType =
        new Method(
            "<init>", Type.VOID_TYPE, new Type[] {A_SIGNATURE_ACCESSOR_TYPE, outerType, innerType});
    final var ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, ctorType, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);

    ctor.loadThis();
    ctor.loadArg(0);
    ctor.putField(joinType, SIGNER_ACCESSOR_NAME, A_SIGNATURE_ACCESSOR_TYPE);
    classVisitor
        .visitField(
            Opcodes.ACC_PRIVATE,
            SIGNER_ACCESSOR_NAME,
            A_SIGNATURE_ACCESSOR_TYPE.getDescriptor(),
            null,
            null)
        .visitEnd();

    ctor.loadThis();
    ctor.loadArg(1);
    ctor.putField(joinType, "outer", outerType);
    classVisitor
        .visitField(Opcodes.ACC_PRIVATE, "outer", outerType.getDescriptor(), null, null)
        .visitEnd();

    ctor.loadThis();
    ctor.loadArg(2);
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
    final var getMethod = new Method(alias, target.type().apply(TypeUtils.TO_ASM), new Type[] {});
    final Renderer renderer =
        new JoinHalfRenderer(
            new GeneratorAdapter(Opcodes.ACC_PUBLIC, getMethod, null, null, classVisitor),
            outer,
            new LoadableValue() {
              @Override
              public String name() {
                return SIGNER_ACCESSOR_NAME;
              }

              @Override
              public Type type() {
                return A_SIGNATURE_ACCESSOR_TYPE;
              }

              @Override
              public void accept(Renderer renderer) {
                renderer.methodGen().loadThis();
                renderer
                    .methodGen()
                    .getField(joinType, SIGNER_ACCESSOR_NAME, A_SIGNATURE_ACCESSOR_TYPE);
              }
            });
    renderer.methodGen().visitCode();
    renderer.loadTarget(target);
    renderer.methodGen().returnValue();
    renderer.methodGen().visitMaxs(0, 0);
    renderer.methodGen().visitEnd();
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
