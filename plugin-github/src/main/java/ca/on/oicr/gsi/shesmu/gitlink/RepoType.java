package ca.on.oicr.gsi.shesmu.gitlink;

public enum RepoType {
  BITBUCKET {
    @Override
    public String format(String url, String suffix, int line) {
      return String.format("%s/browse/%s#%d", url, suffix, line);
    }
  },
  GITHUB {
    @Override
    public String format(String url, String suffix, int line) {
      return String.format("%s/master/%s#L%d", url, suffix, line);
    }
  };

  public abstract String format(String url, String suffix, int line);
}
