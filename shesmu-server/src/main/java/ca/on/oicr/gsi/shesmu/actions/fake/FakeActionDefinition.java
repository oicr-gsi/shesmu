package ca.on.oicr.gsi.shesmu.actions.fake;

import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;

public class FakeActionDefinition extends ActionDefinition {
	private static final Type A_FAKE_ACTION_TYPE = Type.getType(FakeAction.class);
	private static final Method CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] { Type.getType(String.class) });

	public FakeActionDefinition(String name, String description, Stream<ParameterDefinition> parameters) {
		super(name, A_FAKE_ACTION_TYPE, "Fake version of: " + description, parameters);
	}

	@Override
	public void initialize(GeneratorAdapter methodGen) {
		methodGen.newInstance(A_FAKE_ACTION_TYPE);
		methodGen.dup();
		methodGen.push(name());
		methodGen.invokeConstructor(A_FAKE_ACTION_TYPE, CTOR);
	}

}
