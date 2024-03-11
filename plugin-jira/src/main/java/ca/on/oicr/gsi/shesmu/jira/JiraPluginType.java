package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;
import java.util.Optional;

@MetaInfServices
public final class JiraPluginType extends PluginFileType<JiraConnection> {

  private static final Tuple FORMATTING =
      new Tuple("|", "|", "|", "||", "||", "||", Optional.empty());

  @ShesmuMethod(
      type =
          "o7data_end$sdata_separator$sdata_start$sheader_end$sheader_separator$sheader_start$sheader_underline$qs",
      description =
          "The formatting information used for generating JIRA-compatible tables using For...Table")
  public static Tuple table_formatting() {
    return FORMATTING;
  }

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
    writer.println("actionRender.set('jira-issue', a => [");
    writer.println("  title(a, `${a.verb} Ticket in ${a.projectKey}`),");
    writer.println("  link(`${a.instanceUrl}/projects/${a.projectKey}`, 'Go to Server'),");
    writer.println("  text(`Summary: ${a.summary}`)].concat(");
    writer.println("    a.issues.map(i =>");
    writer.println("      link(`${a.instanceUrl}/browse/${i}`, `Issue ${i}`))");
    writer.println("  ));");
  }
}
