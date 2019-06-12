package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.json.UnpackJson;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Compile a {@link FunctionDefinition} to a {@link FunctionRunner} so that it can be used via the
 * REST interface
 */
public final class FunctionRunnerCompiler extends BaseHotloadingCompiler {

  private static final Type A_FUNCTION_RUNNER_TYPE = Type.getType(FunctionRunner.class);

  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);

  private static final Type A_JSON_ARRAY_TYPE = Type.getType(ArrayNode.class);

  private static final Type A_JSON_NODE_TYPE = Type.getType(JsonNode.class);
  private static final Type A_JSON_OBJECT_TYPE = Type.getType(ObjectNode.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);

  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
  private static final Handle HANDLER_IMYHAT =
      new Handle(
          Opcodes.H_INVOKESTATIC,
          A_IMYHAT_TYPE.getInternalName(),
          "bootstrap",
          Type.getMethodDescriptor(
              Type.getType(CallSite.class),
              Type.getType(MethodHandles.Lookup.class),
              A_STRING_TYPE,
              Type.getType(MethodType.class)),
          false);
  private static final Method JSON_ARRAY__GET =
      new Method("get", A_JSON_NODE_TYPE, new Type[] {Type.INT_TYPE});
  private static final Method LOAD_METHOD =
      new Method("run", Type.VOID_TYPE, new Type[] {A_JSON_ARRAY_TYPE, A_JSON_OBJECT_TYPE});

  private static final String METHOD_IMYHAT_DESC = Type.getMethodDescriptor(A_IMYHAT_TYPE);
  private static final Type A_JSON_UNPACK_TYPE = Type.getType(UnpackJson.class);
  private static final Method METHOD_JSON_UNPACK__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_JSON_NODE_TYPE});
  private static final Method METHOD_IMYHAT__APPLY =
      new Method("apply", A_OBJECT_TYPE, new Type[] {Type.getType(ImyhatTransformer.class)});
  private static final Method METHOD_IMYHAT__ACCEPT_OBJ =
      new Method(
          "accept", Type.VOID_TYPE, new Type[] {Type.getType(ImyhatConsumer.class), A_OBJECT_TYPE});
  private static final Type A_PACK_JSON_OBJECT_TYPE = Type.getType(PackJsonObject.class);
  private static final Type A_OBJECT_NODE_TYPE = Type.getType(ObjectNode.class);
  private static final Method PACK_JSON_OBJECT_CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_OBJECT_NODE_TYPE, A_STRING_TYPE});

  public static FunctionRunner compile(FunctionDefinition function) {
    return new FunctionRunnerCompiler(function).compile();
  }

  private final FunctionDefinition function;

  private FunctionRunnerCompiler(FunctionDefinition function) {
    this.function = function;
  }

  public FunctionRunner compile() {
    final ClassVisitor classVisitor = createClassVisitor();
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        "dyn/shesmu/Function",
        null,
        A_OBJECT_TYPE.getInternalName(),
        new String[] {A_FUNCTION_RUNNER_TYPE.getInternalName()});

    final GeneratorAdapter ctor =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, DEFAULT_CTOR, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    final GeneratorAdapter handle =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, LOAD_METHOD, null, null, classVisitor);
    handle.visitCode();
    handle.invokeDynamic(function.returnType().descriptor(), METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
    handle.newInstance(A_PACK_JSON_OBJECT_TYPE);
    handle.dup();
    handle.loadArg(1);
    handle.push("value");
    handle.invokeConstructor(A_PACK_JSON_OBJECT_TYPE, PACK_JSON_OBJECT_CTOR);
    function.renderStart(handle);
    function
        .parameters()
        .map(FunctionParameter::type)
        .map(Pair.number())
        .forEach(
            type -> {
              handle.invokeDynamic(type.second().descriptor(), METHOD_IMYHAT_DESC, HANDLER_IMYHAT);
              handle.newInstance(A_JSON_UNPACK_TYPE);
              handle.dup();
              handle.loadArg(0);
              handle.push(type.first());
              handle.invokeVirtual(A_JSON_ARRAY_TYPE, JSON_ARRAY__GET);
              handle.invokeConstructor(A_JSON_UNPACK_TYPE, METHOD_JSON_UNPACK__CTOR);
              handle.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__APPLY);
              handle.unbox(type.second().apply(TypeUtils.TO_ASM));
            });
    function.render(handle);
    handle.valueOf(function.returnType().apply(TypeUtils.TO_ASM));
    handle.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__ACCEPT_OBJ);
    handle.visitInsn(Opcodes.RETURN);
    handle.visitMaxs(0, 0);
    handle.visitEnd();

    classVisitor.visitEnd();

    try {
      return load(FunctionRunner.class, "dyn.shesmu.Function");
    } catch (InstantiationException
        | IllegalAccessException
        | ClassNotFoundException
        | NoSuchMethodException
        | InvocationTargetException e) {
      e.printStackTrace();
      return (a, o) -> o.put("error", e.getMessage());
    }
  }
}
