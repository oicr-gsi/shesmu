package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.List;

public final class FunctionConfig {
  private String command;
  private List<Imyhat> parameters;
  private Imyhat returns;
  private int ttl = 60;

  public String getCommand() {
    return command;
  }

  public List<Imyhat> getParameters() {
    return parameters;
  }

  public Imyhat getReturns() {
    return returns;
  }

  public int getTtl() {
    return ttl;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setParameters(List<Imyhat> parameters) {
    this.parameters = parameters;
  }

  public void setReturns(Imyhat returns) {
    this.returns = returns;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
  }
}
