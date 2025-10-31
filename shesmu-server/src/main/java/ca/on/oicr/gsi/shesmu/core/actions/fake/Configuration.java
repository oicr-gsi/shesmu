package ca.on.oicr.gsi.shesmu.core.actions.fake;

public class Configuration {
  private String allow;
  private String prefix = "";
  private String url;
  private int timeout;

  public String getAllow() {
    return allow;
  }

  public String getPrefix() {
    return prefix;
  }

  public String getUrl() {
    return url;
  }

  public void setAllow(String allow) {
    this.allow = allow;
  }

  public void setPrefix(String prefix) {
    this.prefix = prefix;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getTimeout() {
    return timeout;
  }

  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }
}
