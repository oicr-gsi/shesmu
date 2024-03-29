package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import java.io.PrintStream;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;

public class SftpPluginType extends PluginFileType<SftpServer> {

  private static final String EXTENSION = ".sftp";

  public SftpPluginType() {
    super(MethodHandles.lookup(), SftpServer.class, EXTENSION, "ssh");
  }

  @Override
  public SftpServer create(Path filePath, String instanceName, Definer<SftpServer> definer) {
    return new SftpServer(filePath, instanceName, definer);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    writer.println(
        "actionRender.set('sftp-symlink', a => [title(a, `Create Symlink on ${a.instance}`), text(`${a.link} → ${a.target}`)])");
    writer.println(
        "actionRender.set('sftp-rm', a => [title(a, `Delete File on ${a.instance}`), text(a.target)])");
  }
}
