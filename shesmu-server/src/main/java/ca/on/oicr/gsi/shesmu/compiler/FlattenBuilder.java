package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.definitions.InputVariable;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** Build a new class for holding the new variables defined by a <tt>Flatten</tt> clause */
public class FlattenBuilder {
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);

  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});

  private final ClassVisitor classVisitor;
  private final Method ctorType;
  private final Renderer explodeMethod;
  private final Type flattenType;
  private final Type originalType;
  private final RootBuilder owner;
  private final Type unrollType;

  public FlattenBuilder(
      RootBuilder owner,
      Type flattenType,
      Type originalType,
      Type unrollType,
      boolean copySignatures,
      Renderer explodeMethod) {
    this.owner = owner;
    this.flattenType = flattenType;
    this.originalType = originalType;
    this.unrollType = unrollType;
    classVisitor = owner.createClassVisitor();
    this.explodeMethod = explodeMethod;
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        flattenType.getInternalName(),
        null,
        A_OBJECT_TYPE.getInternalName(),
        null);
    ctorType =
        new Method(
            "<init>",
            Type.VOID_TYPE,
            Stream.concat(
                    Stream.of(originalType, unrollType),
                    copySignatures
                        ? owner.signatureVariables().map(SignatureDefinition::storageType)
                        : Stream.empty())
                .toArray(Type[]::new));
    final GeneratorAdapter ctor =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, ctorType, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);

    ctor.loadThis();
    ctor.loadArg(0);
    ctor.putField(flattenType, "$original", originalType);
    classVisitor
        .visitField(Opcodes.ACC_PRIVATE, "$original", originalType.getDescriptor(), null, null)
        .visitEnd();

    ctor.loadThis();
    ctor.loadArg(1);
    ctor.putField(flattenType, "$unroll", unrollType);
    classVisitor
        .visitField(Opcodes.ACC_PRIVATE, "$unroll", unrollType.getDescriptor(), null, null)
        .visitEnd();
    if (copySignatures) {
      owner
          .signatureVariables()
          .forEach(
              new Consumer<SignatureDefinition>() {
                private int index = 2;

                @Override
                public void accept(SignatureDefinition signatureDefinition) {
                  ctor.loadThis();
                  ctor.loadArg(index++);
                  ctor.putField(
                      flattenType, signatureDefinition.name(), signatureDefinition.storageType());
                  classVisitor
                      .visitField(
                          Opcodes.ACC_PRIVATE,
                          signatureDefinition.name(),
                          signatureDefinition.storageType().getDescriptor(),
                          null,
                          null)
                      .visitEnd();
                }
              });
    }

    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();
  }

  public void add(Target target) {
    final Method getMethod =
        new Method(target.name(), target.type().apply(TypeUtils.TO_ASM), new Type[] {});
    final GeneratorAdapter getter =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, getMethod, null, null, classVisitor);
    getter.visitCode();
    getter.loadThis();
    if (target instanceof SignatureDefinition) {
      getter.getField(flattenType, target.name(), getMethod.getReturnType());
    } else {
      getter.getField(flattenType, "$original", originalType);
      if (target instanceof InputVariable) {
        ((InputVariable) target).extract(getter);
      } else {
        getter.invokeVirtual(originalType, getMethod);
      }
    }
    getter.returnValue();
    getter.visitMaxs(0, 0);
    getter.visitEnd();
  }

  public void add(LoadableConstructor constructor) {
    constructor
        .create(
            renderer -> {
              renderer.methodGen().loadThis();
              renderer.methodGen().getField(flattenType, "$unroll", unrollType);
            })
        .forEach(
            loader -> {
              final Method getMethod = new Method(loader.name(), loader.type(), new Type[] {});
              final GeneratorAdapter getter =
                  new GeneratorAdapter(Opcodes.ACC_PUBLIC, getMethod, null, null, classVisitor);
              getter.visitCode();
              loader.accept(
                  new RendererArgumentStream(
                      owner,
                      getter,
                      0,
                      null,
                      Stream.empty(),
                      (sv, r) -> {
                        throw new UnsupportedOperationException(
                            "Cannot have signature in function.");
                      }));
              getter.returnValue();
              getter.visitMaxs(0, 0);
              getter.visitEnd();
            });
  }

  public Method constructor() {
    return ctorType;
  }

  public Renderer explodeMethod() {
    return explodeMethod;
  }

  public void finish() {
    classVisitor.visitEnd();
  }

  public Type type() {
    return flattenType;
  }
}
