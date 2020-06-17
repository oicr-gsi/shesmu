package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class JiraPluginType extends PluginFileType<JiraConnection> {

  public JiraPluginType() {
    super(MethodHandles.lookup(), JiraConnection.class, ".jira", "jira");
  }

  @Override
  public JiraConnection create(
      Path filePath, String instanceName, Definer<JiraConnection> definer) {
    return new JiraConnection(filePath, instanceName, definer);
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
