package ca.on.oicr.gsi.shesmu.sftp;

import java.util.List;

public final class FunctionConfig {
  private String command;
  private List<String> parameters;
  private String returns;
  private int ttl = 60;

  public String getCommand() {
    return command;
  }

  public List<String> getParameters() {
    return parameters;
  }

  public String getReturns() {
    return returns;
  }

  public int getTtl() {
    return ttl;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setParameters(List<String> parameters) {
    this.parameters = parameters;
  }

  public void setReturns(String returns) {
    this.returns = returns;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
  }
}
