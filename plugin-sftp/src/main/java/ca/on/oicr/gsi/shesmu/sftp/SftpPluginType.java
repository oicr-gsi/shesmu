package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class SftpPluginType extends PluginFileType<SftpServer> {

  private static final String EXTENSION = ".sftp";

  public SftpPluginType() {
    super(MethodHandles.lookup(), SftpServer.class, EXTENSION);
  }

  @Override
  public SftpServer create(Path filePath, String instanceName, Definer definer) {
    return new SftpServer(filePath, instanceName);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    // No actions.
  }
}
