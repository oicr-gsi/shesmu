package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

public class ActionFilterSourceFile extends ActionFilter {
  private String[] files;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.fromFile(Stream.of(files)), filterBuilder);
  }

  public String[] getFiles() {
    return files;
  }

  public void setFiles(String[] files) {
    this.files = files;
  }
}
