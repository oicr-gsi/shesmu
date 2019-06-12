package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.TypeUtils;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.json.UnpackJson;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Stream;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/**
 * Compile a {@link ActionDefinition} to a {@link ActionRunner} so that it can be used via the
 * static JSON file interface
 */
public final class ActionRunnerCompiler extends BaseHotloadingCompiler {

  private static final Type A_ACTION_RUNNER_TYPE = Type.getType(ActionRunner.class);
  private static final Type A_ACTION_TYPE = Type.getType(Action.class);

  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);

  private static final Type A_JSON_NODE_TYPE = Type.getType(JsonNode.class);
  private static final Type A_JSON_OBJECT_TYPE = Type.getType(ObjectNode.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);

  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
  private static final Method JSON_OBJECT__GET =
      new Method("get", A_JSON_NODE_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method JSON_OBJECT__HAS =
      new Method("has", Type.BOOLEAN_TYPE, new Type[] {A_STRING_TYPE});

  private static final Method RUN_METHOD =
      new Method("run", A_ACTION_TYPE, new Type[] {A_JSON_OBJECT_TYPE});
  private static final Type A_JSON_UNPACK_TYPE = Type.getType(UnpackJson.class);
  private static final Method METHOD_JSON_UNPACK__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_JSON_NODE_TYPE});
  private static final Method METHOD_IMYHAT__APPLY =
      new Method("apply", A_OBJECT_TYPE, new Type[] {Type.getType(ImyhatTransformer.class)});

  public static ActionRunner compile(ActionDefinition function) {
    return new ActionRunnerCompiler(function).compile();
  }

  private final ActionDefinition action;

  public ActionRunnerCompiler(ActionDefinition action) {
    this.action = action;
  }

  public ActionRunner compile() {
    final ClassVisitor classVisitor = createClassVisitor();
    classVisitor.visit(
        Opcodes.V1_8,
        Opcodes.ACC_PUBLIC,
        "dyn/shesmu/ActionRunner",
        null,
        A_OBJECT_TYPE.getInternalName(),
        new String[] {A_ACTION_RUNNER_TYPE.getInternalName()});

    final GeneratorAdapter ctor =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, DEFAULT_CTOR, null, null, classVisitor);
    ctor.visitCode();
    ctor.loadThis();
    ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
    ctor.visitInsn(Opcodes.RETURN);
    ctor.visitMaxs(0, 0);
    ctor.visitEnd();

    final GeneratorAdapter handle =
        new GeneratorAdapter(Opcodes.ACC_PUBLIC, RUN_METHOD, null, null, classVisitor);
    handle.visitCode();
    final int actionLocal = handle.newLocal(A_ACTION_TYPE);
    action.initialize(handle);
    handle.storeLocal(actionLocal);
    action
        .parameters()
        .forEach(
            parameter -> {
              Label end = null;
              if (!parameter.required()) {
                end = handle.newLabel();
                handle.loadArg(0);
                handle.push(parameter.name());
                handle.invokeVirtual(A_JSON_OBJECT_TYPE, JSON_OBJECT__HAS);
                handle.ifZCmp(GeneratorAdapter.EQ, end);
              }
              parameter.store(
                  new Renderer(
                      null,
                      handle,
                      0,
                      null,
                      Stream.empty(),
                      (n, r) -> {
                        throw new UnsupportedOperationException(
                            "Trying to access signature variable from action runner.");
                      }),
                  actionLocal,
                  r -> {
                    r.loadImyhat(parameter.type().descriptor());
                    handle.newInstance(A_JSON_UNPACK_TYPE);
                    handle.dup();
                    r.methodGen().loadArg(0);
                    r.methodGen().push(parameter.name());
                    handle.invokeVirtual(A_JSON_OBJECT_TYPE, JSON_OBJECT__GET);
                    handle.invokeConstructor(A_JSON_UNPACK_TYPE, METHOD_JSON_UNPACK__CTOR);
                    handle.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__APPLY);
                    handle.unbox(parameter.type().apply(TypeUtils.TO_ASM));
                  });
              if (end != null) {
                handle.mark(end);
              }
            });
    handle.loadLocal(actionLocal);
    handle.returnValue();
    handle.visitMaxs(0, 0);
    handle.visitEnd();

    classVisitor.visitEnd();

    try {
      return load(ActionRunner.class, "dyn.shesmu.ActionRunner");
    } catch (InstantiationException
        | IllegalAccessException
        | ClassNotFoundException
        | NoSuchMethodException
        | InvocationTargetException e) {
      e.printStackTrace();
      return p -> null;
    }
  }
}
