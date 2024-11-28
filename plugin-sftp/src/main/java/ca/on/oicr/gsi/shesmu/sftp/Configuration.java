package ca.on.oicr.gsi.shesmu.sftp;

import java.util.List;
import java.util.Map;

public class Configuration {
  private List<String> fileRoots = List.of();
  private Integer fileRootsTtl;
  private Map<String, FunctionConfig> functions = Map.of();
  private String host;
  private List<JsonDataSource> jsonSources = List.of();
  private String listCommand;
  private int port;
  private Integer refillerTimeout;
  private Map<String, RefillerConfig> refillers = Map.of();
  private String user;

  public List<String> getFileRoots() {
    return fileRoots;
  }

  public Integer getFileRootsTtl() {
    return fileRootsTtl;
  }

  public Map<String, FunctionConfig> getFunctions() {
    return functions;
  }

  public String getHost() {
    return host;
  }

  public List<JsonDataSource> getJsonSources() {
    return jsonSources;
  }

  public String getListCommand() {
    return listCommand;
  }

  public int getPort() {
    return port;
  }

  public Integer getRefillerTimeout() {
    return refillerTimeout;
  }

  public Map<String, RefillerConfig> getRefillers() {
    return refillers;
  }

  public String getUser() {
    return user;
  }

  public void setFileRoots(List<String> fileRoots) {
    this.fileRoots = fileRoots;
  }

  public void setFileRootsTtl(Integer fileRootsTtl) {
    this.fileRootsTtl = fileRootsTtl;
  }

  public void setFunctions(Map<String, FunctionConfig> functions) {
    this.functions = functions;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setJsonSources(List<JsonDataSource> jsonSources) {
    this.jsonSources = jsonSources;
  }

  public void setListCommand(String listCommand) {
    this.listCommand = listCommand;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setRefillerTimeout(Integer refillerTimeout) {
    this.refillerTimeout = refillerTimeout;
  }

  public void setRefillers(Map<String, RefillerConfig> refillers) {
    this.refillers = refillers;
  }

  public void setUser(String user) {
    this.user = user;
  }
}
