package ca.on.oicr.gsi.shesmu.sftp;

import java.util.Collections;
import java.util.Map;

public class Configuration {
  private String host;
  private int port;
  private Map<String, RefillerConfig> refillers = Collections.emptyMap();
  private String user;

  public String getHost() {
    return host;
  }

  public int getPort() {
    return port;
  }

  public Map<String, RefillerConfig> getRefillers() {
    return refillers;
  }

  public String getUser() {
    return user;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setRefillers(Map<String, RefillerConfig> refillers) {
    this.refillers = refillers;
  }

  public void setUser(String user) {
    this.user = user;
  }
}
