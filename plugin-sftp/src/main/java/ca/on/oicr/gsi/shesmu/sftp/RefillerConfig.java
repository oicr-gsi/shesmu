package ca.on.oicr.gsi.shesmu.sftp;

import java.util.Map;

public class RefillerConfig {
  private String command;
  private Map<String, String> parameters;

  public String getCommand() {
    return command;
  }

  public Map<String, String> getParameters() {
    return parameters;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setParameters(Map<String, String> parameters) {
    this.parameters = parameters;
  }
}
