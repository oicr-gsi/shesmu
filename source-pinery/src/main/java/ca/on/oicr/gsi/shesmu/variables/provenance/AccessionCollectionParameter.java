package ca.on.oicr.gsi.shesmu.variables.provenance;

import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;

public class AccessionCollectionParameter implements ParameterDefinition {
	private static final Type A_ACP_TYPE = Type.getType(AccessionCollectionParameter.class);
	private static final Type A_SET_TYPE = Type.getType(Set.class);
	private static final Type A_SQWACTION_TYPE = Type.getType(SeqWareWorkflowAction.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Method ACP__PARSE_LONGS = new Method("parseLongs", A_STRING_TYPE, new Type[] { A_SET_TYPE });

	public static String parseLongs(Set<String> ids) {
		return ids.stream().map(Long::parseUnsignedLong).sorted().map(Object::toString)
				.collect(Collectors.joining(","));
	}

	private final String name;

	private final String realName;

	public AccessionCollectionParameter(String name, String realName) {
		super();
		this.name = name;
		this.realName = realName;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public boolean required() {
		return true;
	}

	@Override
	public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
		renderer.methodGen().loadLocal(actionLocal);
		loadParameter.accept(renderer);
		renderer.methodGen().invokeStatic(A_ACP_TYPE, ACP__PARSE_LONGS);
		renderer.methodGen().putField(A_SQWACTION_TYPE, realName, A_STRING_TYPE);

	}

	@Override
	public Imyhat type() {
		return Imyhat.STRING.asList();
	}
}
