package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;

public class RefillerConfig {
  private String command;
  private Map<String, Imyhat> parameters;

  public String getCommand() {
    return command;
  }

  public Map<String, Imyhat> getParameters() {
    return parameters;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setParameters(Map<String, Imyhat> parameters) {
    this.parameters = parameters;
  }
}
