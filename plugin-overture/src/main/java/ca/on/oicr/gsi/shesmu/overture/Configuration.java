package ca.on.oicr.gsi.shesmu.overture;

public final class Configuration {
  private String authorization;
  private String songUrl;

  public String getAuthorization() {
    return authorization;
  }

  public String getSongUrl() {
    return songUrl;
  }

  public void setAuthorization(String authorization) {
    this.authorization = authorization;
  }

  public void setSongUrl(String songUrl) {
    this.songUrl = songUrl;
  }
}
