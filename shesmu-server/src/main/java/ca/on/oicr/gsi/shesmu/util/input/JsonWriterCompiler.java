package ca.on.oicr.gsi.shesmu.util.input;

import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.util.server.BaseHotloadingCompiler;
import com.fasterxml.jackson.core.JsonGenerator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

public final class JsonWriterCompiler extends BaseHotloadingCompiler {
  private static final Type A_CONSUMER_TYPE = Type.getType(Consumer.class);
  private static final Type A_JSON_GENERATOR_TYPE = Type.getType(JsonGenerator.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_JSON_GENERATOR_TYPE});
  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_ACCEPT =
      new Method("accept", Type.VOID_TYPE, new Type[] {A_OBJECT_TYPE});
  private static final Method METHOD_WRITE_FIELD =
      new Method("writeFieldName", Type.VOID_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method METHOD_WRITE_OBJ_END =
      new Method("writeEndObject", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_WRITE_OBJ_START =
      new Method("writeStartObject", Type.VOID_TYPE, new Type[] {});

  public <T> Class<Consumer<T>> create(Stream<Target> fields, Class<T> itemClass) {
    final Type self = Type.getObjectType("dyn/shesmu/Writer");
    final Type itemType = Type.getType(itemClass);
    final ClassVisitor classVisitor = createClassVisitor();
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        self.getInternalName(),
        null,
        A_OBJECT_TYPE.getInternalName(),
        new String[] {A_CONSUMER_TYPE.getInternalName()});

    classVisitor
        .visitField(Opcodes.ACC_PRIVATE, "json", A_JSON_GENERATOR_TYPE.getDescriptor(), null, null)
        .visitEnd();

    final GeneratorAdapter ctor =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, CTOR, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
    ctor.loadThis();
    ctor.loadArg(0);
    ctor.putField(self, "json", A_JSON_GENERATOR_TYPE);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    final GeneratorAdapter accept =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, METHOD_ACCEPT, null, null, classVisitor);
    accept.visitCode();
    final int local = accept.newLocal(itemType);
    accept.loadArg(0);
    accept.checkCast(itemType);
    accept.storeLocal(local);

    accept.loadThis();
    accept.getField(self, "json", A_JSON_GENERATOR_TYPE);
    accept.dup();
    accept.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_WRITE_OBJ_START);

    fields.forEach(
        field -> {
          accept.dup();
          accept.push(field.name());
          accept.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_WRITE_FIELD);
          accept.dup();
          accept.loadLocal(local);
          accept.invokeVirtual(
              itemType, new Method(field.name(), field.type().asmType(), new Type[] {}));
          field.type().streamJson(accept);
        });
    accept.invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_WRITE_OBJ_END);
    accept.visitInsn(Opcodes.RETURN);
    accept.visitMaxs(0, 0);
    accept.visitEnd();
    classVisitor.visitEnd();

    try {
      @SuppressWarnings("unchecked")
      final Class<Consumer<T>> clazz =
          (Class<Consumer<T>>) loadClass(Consumer.class, self.getClassName());
      return clazz;
    } catch (final ClassNotFoundException e) {
      e.printStackTrace();
      return null;
    }
  }
}
