package ca.on.oicr.gsi.shesmu.json;

public final class RemoteConfiguration extends BaseConfiguration {
  private int ttl = 10;
  private String url;

  public int getTtl() {
    return ttl;
  }

  public String getUrl() {
    return url;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
