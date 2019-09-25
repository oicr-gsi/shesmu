package ca.on.oicr.gsi.shesmu.mongo;

import java.util.Map;

public class Configuration {
  private Map<String, MongoFunction> functions;
  private String uri;

  public Map<String, MongoFunction> getFunctions() {
    return functions;
  }

  public String getUri() {
    return uri;
  }

  public void setFunctions(Map<String, MongoFunction> functions) {
    this.functions = functions;
  }

  public void setUri(String uri) {
    this.uri = uri;
  }
}
