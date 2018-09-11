package ca.on.oicr.gsi.shesmu.jira;

import java.io.PrintStream;
import java.nio.file.Path;
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

		public TicketActionDefinition(JiraConfig config, String prefix, Type type, String description,
				Stream<ParameterDefinition> parameters) {
			super(String.format("%s_%s", prefix, config.instance()), type, description, Stream.concat(parameters, Stream
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
	protected Stream<ActionDefinition> create(JiraConfig config, Path filename) {
		return Stream.of(//
				new TicketActionDefinition(config, "ticket", A_FILE_TICKET_TYPE, //
						String.format("Opens (or re-opens) a JIRA ticket in %s. Defined in %s.", config.projectKey(),
								filename),
						Stream.of(//
								ParameterDefinition.forField(A_FILE_TICKET_TYPE, "description", Imyhat.STRING, true), //
								ParameterDefinition.forField(A_FILE_TICKET_TYPE, "type", Imyhat.STRING, false))),
				new TicketActionDefinition(config, "resolve_ticket", A_RESOLVE_TICKET_TYPE, //
						String.format("Closes any JIRA tickets in %s with a matching summary. Defined in %s.",
								config.projectKey(), filename), //
						Stream.of(//
								ParameterDefinition.forField(A_RESOLVE_TICKET_TYPE, "comment", Imyhat.STRING, false))));
	}

	@Override
	protected String purpose() {
		return "action";
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return stream();
	}

	@Override
	public void writeJavaScriptRenderer(PrintStream writer) {
		writer.println("const jiraTicket = (a, verb) => [");
		writer.println("  title(a, `${verb} Ticket in ${a.projectKey}`),");
		writer.println("  link(`${a.instanceUrl}/projects/${a.projectKey}`, 'Go to Server'),");
		writer.println("  text(`Summary: ${a.summary}`)].concat(");
		writer.println("    a.issues.map(i =>");
		writer.println("      link(`${a.instanceUrl}/browse/${i}`, `Issue ${i}`))");
		writer.println("  );");
		writer.println("actionRender.set('jira-open-ticket', a => jiraTicket(a,'Open'));");
		writer.println("actionRender.set('jira-close-ticket', a => jiraTicket(a, 'Close'));");
	}

}