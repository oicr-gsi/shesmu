package ca.on.oicr.gsi.shesmu.sftp;

public class JsonDataSource {
  private String command;
  private String format;
  private int ttl = 60;

  public String getCommand() {
    return command;
  }

  public String getFormat() {
    return format;
  }

  public int getTtl() {
    return ttl;
  }

  public void setCommand(String command) {
    this.command = command;
  }

  public void setFormat(String format) {
    this.format = format;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
  }
}
