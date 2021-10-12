package ca.on.oicr.gsi.shesmu.guanyin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Bean for on-disk Guanyin service configuration files */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Configuration {
  private String cromwell;
  private String guanyin;
  private int memory = 1;
  private String modules = "";
  private String script;
  private int timeout = 1;

  public String getCromwell() {
    return cromwell;
  }

  public String getGuanyin() {
    return guanyin;
  }

  public int getMemory() {
    return memory;
  }

  public String getModules() {
    return modules;
  }

  public String getScript() {
    return script;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setCromwell(String cromwell) {
    this.cromwell = cromwell;
  }

  public void setGuanyin(String guanyin) {
    this.guanyin = guanyin;
  }

  public void setMemory(int memory) {
    this.memory = memory;
  }

  public void setModules(String modules) {
    this.modules = modules;
  }

  public void setScript(String script) {
    this.script = script;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }
}
