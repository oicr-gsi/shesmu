package ca.on.oicr.gsi.shesmu.jira;

import java.io.PrintStream;

import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedMatchedDefinitionRepository;

@MetaInfServices(DefinitionRepository.class)
public final class JiraDefinitionRepository extends FileBackedMatchedDefinitionRepository<JiraConnection> {

	public JiraDefinitionRepository() {
		super(JiraConnection.class, ".jira", JiraConnection::new);
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
