package ca.on.oicr.gsi.shesmu.actions.rest;

import java.util.Map.Entry;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;

public final class Definition {
	private static final class JsonParameter implements ParameterDefinition {
		private final String name;
		private final Imyhat type;

		private JsonParameter(String name, Imyhat type) {
			this.name = name;
			this.type = type;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
			renderer.loadImyhat(type.signature());
			renderer.methodGen().loadLocal(actionLocal);
			renderer.methodGen().getField(A_LAUNCH_REMOTE_TYPE, "parameters", A_OBJECT_NODE_TYPE);
			renderer.methodGen().push(name);
			loadParameter.accept(renderer);
			renderer.methodGen().box(type.asmType());
			renderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__PACK_JSON);

		}

		@Override
		public Imyhat type() {
			return type;
		}
	}

	private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
	private static final Type A_LAUNCH_REMOTE_TYPE = Type.getType(LaunchRemote.class);

	private static final Type A_OBJECT_NODE_TYPE = Type.getType(ObjectNode.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Method CTOR_LAUNCH_REMOTE = new Method("<init>", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE });

	private static final Method METHOD_IMYHAT__PACK_JSON = new Method("packJson", Type.VOID_TYPE,
			new Type[] { A_OBJECT_NODE_TYPE, A_STRING_TYPE, A_OBJECT_TYPE });

	private String name;

	private ObjectNode parameters;

	public String getName() {
		return name;
	}

	public ObjectNode getParameters() {
		return parameters;
	}

	private Stream<Entry<String, JsonNode>> parametersStream() {
		return StreamSupport.stream(Spliterators.spliteratorUnknownSize(parameters.fields(), Spliterator.ORDERED),
				false);
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setParameters(ObjectNode parameters) {
		this.parameters = parameters;
	}

	public ActionDefinition toDefinition(String url) {
		return new ActionDefinition(name, A_LAUNCH_REMOTE_TYPE, parametersStream().map(p -> {
			final Imyhat type = Imyhat.parse(p.getValue().asText());
			return new JsonParameter(p.getKey(), type);
		})) {

			@Override
			public void initialize(GeneratorAdapter methodGen) {
				methodGen.newInstance(A_LAUNCH_REMOTE_TYPE);
				methodGen.dup();
				methodGen.push(url);
				methodGen.push(name);
				methodGen.invokeConstructor(A_LAUNCH_REMOTE_TYPE, CTOR_LAUNCH_REMOTE);
			}
		};
	}
}