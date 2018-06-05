package ca.on.oicr.gsi.shesmu;

import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.compiler.Renderer;

/**
 * Compile a {@link ActionDefinition} to a {@link ActionRunner} so that it can
 * be used via the static JSON file interface
 */
public final class ActionRunnerCompiler extends BaseHotloadingCompiler {

	private static final Type A_ACTION_RUNNER_TYPE = Type.getType(ActionRunner.class);

	private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);

	private static final Type A_JSON_NODE_TYPE = Type.getType(JsonNode.class);
	private static final Type A_JSON_OBJECT_TYPE = Type.getType(ObjectNode.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
	private static final Method JSON_OBJECT__GET = new Method("get", A_JSON_NODE_TYPE, new Type[] { A_STRING_TYPE });
	private static final Method LOAD_METHOD = new Method("run", Type.VOID_TYPE, new Type[] { A_JSON_OBJECT_TYPE });

	private static final Method METHOD_IMYHAT__UNPACK_JSON = new Method("unpackJson", A_OBJECT_TYPE,
			new Type[] { A_JSON_NODE_TYPE });
	public static ActionRunner compile(ActionDefinition function) {
		return new ActionRunnerCompiler(function).compile();
	}

	private final ActionDefinition action;

	public ActionRunnerCompiler(ActionDefinition action) {
		this.action = action;
	}

	public ActionRunner compile() {
		ClassVisitor classVisitor = createClassVisitor();
		classVisitor.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "dyn/shesmu/Action", null, A_OBJECT_TYPE.getInternalName(),
				new String[] { A_ACTION_RUNNER_TYPE.getInternalName() });

		GeneratorAdapter ctor = new GeneratorAdapter(Opcodes.ACC_PUBLIC, DEFAULT_CTOR, null, null, classVisitor);
		ctor.visitCode();
		ctor.loadThis();
		ctor.invokeConstructor(A_OBJECT_TYPE, DEFAULT_CTOR);
		ctor.visitInsn(Opcodes.RETURN);
		ctor.visitMaxs(0, 0);
		ctor.visitEnd();

		GeneratorAdapter handle = new GeneratorAdapter(Opcodes.ACC_PUBLIC, LOAD_METHOD, null, null, classVisitor);
		handle.visitCode();
		int actionLocal = handle.newLocal(action.type());
		action.initialize(handle);
		handle.storeLocal(actionLocal);
		action.parameters().forEach(parameter -> {
			parameter.store(new Renderer(null, handle, 0, null, Stream.empty()), actionLocal, r -> {
				r.loadImyhat(parameter.type().signature());
				r.methodGen().loadArg(0);
				r.methodGen().push(parameter.name());
				handle.invokeVirtual(A_JSON_OBJECT_TYPE, JSON_OBJECT__GET);
				handle.invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__UNPACK_JSON);
				handle.unbox(parameter.type().asmType());
			});
		});
		handle.loadLocal(actionLocal);
		handle.visitInsn(Opcodes.RETURN);
		handle.visitMaxs(0, 0);
		handle.visitEnd();

		classVisitor.visitEnd();

		try {
			return load(ActionRunner.class, "dyn.shesmu.Action");
		} catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
			e.printStackTrace();
			return p -> null;
		}
	}
}
