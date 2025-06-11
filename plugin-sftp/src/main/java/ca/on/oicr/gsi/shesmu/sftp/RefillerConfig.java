package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;

public class RefillerConfig {
  private String command;
  private Map<String, Imyhat> parameters;
  private Integer timeout;

  public String getCommand() {
    return command;
  }

  public Map<String, Imyhat> getParameters() {
    return parameters;
  }

  public Integer getTimeout() {
    return timeout;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setParameters(Map<String, Imyhat> parameters) {
    this.parameters = parameters;
  }

  public void setTimeout(Integer timeout) {
    this.timeout = timeout;
  }
}
