package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.ParameterDefinition;

public interface WorkflowParameterDefinition {
	public ParameterDefinition generate(Type type);
}
