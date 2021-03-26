package ca.on.oicr.gsi.shesmu.intervals;

public class Configuration {
  private String directory;
  private String replacementPrefix;

  public String getDirectory() {
    return directory;
  }

  public String getReplacementPrefix() {
    return replacementPrefix;
  }

  public void setDirectory(String directory) {
    this.directory = directory;
  }

  public void setReplacementPrefix(String replacementPrefix) {
    this.replacementPrefix = replacementPrefix;
  }
}
