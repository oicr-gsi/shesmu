package ca.on.oicr.gsi.shesmu.plugin.filter;

public class ActionFilterSourceFile extends ActionFilter {
  private String[] files;

  @Override
  public <F> F convert(ActionFilterBuilder<F> filterBuilder) {
    return maybeNegate(filterBuilder.fromFile(files), filterBuilder);
  }

  public String[] getFiles() {
    return files;
  }

  public void setFiles(String[] files) {
    this.files = files;
  }
}
