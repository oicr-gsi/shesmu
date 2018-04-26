package ca.on.oicr.gsi.shesmu.actions.nothing;

import java.util.Map;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;

@MetaInfServices
public class NothingActionRepository implements ActionRepository {
	private static final Type A_NOTHING_ACTION_TYPE = Type.getType(NothingAction.class);

	private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});

	private static final ActionDefinition NOTHING_ACTION = new ActionDefinition("nothing", A_NOTHING_ACTION_TYPE,
			Stream.of(ParameterDefinition.forField(A_NOTHING_ACTION_TYPE, "value", Imyhat.STRING, true))) {

		@Override
		public void initialize(GeneratorAdapter methodGen) {
			methodGen.newInstance(A_NOTHING_ACTION_TYPE);
			methodGen.dup();
			methodGen.invokeConstructor(A_NOTHING_ACTION_TYPE, DEFAULT_CTOR);
		}

	};

	@Override
	public Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return Stream.empty();
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return Stream.of(NOTHING_ACTION);
	}

}
