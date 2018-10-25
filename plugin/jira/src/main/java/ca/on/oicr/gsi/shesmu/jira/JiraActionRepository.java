package ca.on.oicr.gsi.shesmu.jira;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;

import ca.on.oicr.gsi.shesmu.ActionDefinition;
import ca.on.oicr.gsi.shesmu.ActionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ParameterDefinition;
import ca.on.oicr.gsi.shesmu.util.RuntimeBinding;

@MetaInfServices(ActionRepository.class)
public final class JiraActionRepository extends BaseJiraRepository<ActionDefinition> implements ActionRepository {

	private static final Type A_BASE_TICKET_ACTION_TYPE = Type.getType(BaseTicketAction.class);
	private static final Type A_FILE_TICKET_TYPE = Type.getType(FileTicket.class);
	private static final Type A_RESOLVE_TICKET_TYPE = Type.getType(ResolveTicket.class);

	public JiraActionRepository() {
		super("JIRA Action Repository");
	}

	@Override
	protected Stream<ActionDefinition> create(JiraConfig config, Path filename) {
		return RUNTIME_BINDING.bindActions(config).stream();
	}

	@Override
	protected String purpose() {
		return "action";
	}

	@Override
	public Stream<ActionDefinition> queryActions() {
		return stream();
	}

	private RuntimeBinding<JiraConnection> RUNTIME_BINDING = new RuntimeBinding<>(JiraConnection.class, EXTENSION)//
			.action("ticket_%s", FileTicket.class, "Opens (or re-opens) a JIRA ticket. Defined in %2$s.", //
					ParameterDefinition.forField(A_BASE_TICKET_ACTION_TYPE, "summary", Imyhat.STRING, true), //
					ParameterDefinition.forField(A_FILE_TICKET_TYPE, "description", Imyhat.STRING, true), //
					ParameterDefinition.forField(A_FILE_TICKET_TYPE, "type", Imyhat.STRING, false))//
			.action("resolve_ticket_%s", ResolveTicket.class,
					"Closes any JIRA tickets with a matching summary. Defined in %2%s.", //
					ParameterDefinition.forField(A_BASE_TICKET_ACTION_TYPE, "summary", Imyhat.STRING, true), //
					ParameterDefinition.forField(A_RESOLVE_TICKET_TYPE, "comment", Imyhat.STRING, false));

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