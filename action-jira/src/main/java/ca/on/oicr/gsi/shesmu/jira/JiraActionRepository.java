package ca.on.oicr.gsi.shesmu.jira;

import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;

@MetaInfServices(ActionRepository.class)
public final class JiraActionRepository extends BaseJiraRepository<ActionDefinition> implements ActionRepository {

	private static class TicketActionDefinition extends ActionDefinition {
		private final JiraConfig config;

		public TicketActionDefinition(JiraConfig config, String prefix, Type type,
				Stream<ParameterDefinition> parameters) {
			super(String.format("%s_%s", prefix, config.instance()), type, Stream.concat(parameters, Stream
					.of(ParameterDefinition.forField(A_BASE_TICKET_ACTION_TYPE, "summary", Imyhat.STRING, true))));
			this.config = config;
		}

		@Override
		public void initialize(GeneratorAdapter methodGen) {
			methodGen.newInstance(type());
			methodGen.dup();
			methodGen.push(config.id());
			methodGen.invokeConstructor(type(), CTOR_FILE_TICKET);
		}

	}

	private static final Type A_BASE_TICKET_ACTION_TYPE = Type.getType(BaseTicketAction.class);
	private static final Type A_FILE_TICKET_TYPE = Type.getType(FileTicket.class);
	private static final Type A_RESOLVE_TICKET_TYPE = Type.getType(ResolveTicket.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);

	private static final Method CTOR_FILE_TICKET = new Method("<init>", Type.VOID_TYPE, new Type[] { A_STRING_TYPE });

	public JiraActionRepository() {
		super("JIRA Action Repository");
	}

	@Override
	protected Stream<ActionDefinition> create(JiraConfig config) {
		return Stream.of(
				new TicketActionDefinition(config, "ticket", A_FILE_TICKET_TYPE,
						Stream.of(
								ParameterDefinition.forField(A_FILE_TICKET_TYPE, "description", Imyhat.STRING, true))),
				new TicketActionDefinition(config, "resolve_ticket", A_RESOLVE_TICKET_TYPE, Stream
						.of(ParameterDefinition.forField(A_RESOLVE_TICKET_TYPE, "comment", Imyhat.STRING, false))));
	}

	@Override
	protected String purpose() {
		return "action";
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return stream();
	}

}