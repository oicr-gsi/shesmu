package ca.on.oicr.gsi.shesmu.actions.rest;

import java.util.Map;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.actions.util.JsonParameter;

public final class Definition {
	static final Type A_LAUNCH_REMOTE_TYPE = Type.getType(LaunchRemote.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Method CTOR_LAUNCH_REMOTE = new Method("<init>", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE, A_STRING_TYPE });

	private String description;
	private String name;

	private Map<String, ParameterInfo> parameters;

	public String getDescription() {
		return description;
	}

	public String getName() {
		return name;
	}

	public Map<String, ParameterInfo> getParameters() {
		return parameters;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setParameters(Map<String, ParameterInfo> parameters) {
		this.parameters = parameters;
	}

	public ActionDefinition toDefinition(String url) {
		return new ActionDefinition(name, A_LAUNCH_REMOTE_TYPE,
				getDescription() + String.format(" Defined on %s.", url), parameters.entrySet().stream().map(p -> {
					final Imyhat type = Imyhat.parse(p.getValue().getType());
					return new JsonParameter(p.getKey(), type, p.getValue().isRequired());
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