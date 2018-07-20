package ca.on.oicr.gsi.shesmu.variables.provenance;

import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.ParameterDefinition;

public interface SeqWareParameterDefinition {
	public ParameterDefinition generate(Type type);
}
