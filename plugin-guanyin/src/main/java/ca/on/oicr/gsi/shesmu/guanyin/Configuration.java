package ca.on.oicr.gsi.shesmu.guanyin;

/** Bean for on-disk Guanyin service configuration files */
public class Configuration {
  private String cromwell;
  private String drmaa;
  private String drmaaPsk;
  private String guanyin;
  private int memory = 1;
  private String modules = "";
  private String script;

  public String getCromwell() {
    return cromwell;
  }

  public String getDrmaa() {
    return drmaa;
  }

  public String getDrmaaPsk() {
    return drmaaPsk;
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

  public void setCromwell(String cromwell) {
    this.cromwell = cromwell;
  }

  public void setDrmaa(String drmaa) {
    this.drmaa = drmaa;
  }

  public void setDrmaaPsk(String drmaaPsk) {
    this.drmaaPsk = drmaaPsk;
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
}
