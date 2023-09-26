package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.time.Instant;
import java.util.stream.Stream;

/**
 * Check if an action was generated from a particular olive script file or <code>.actnow</code> file
 */
public class ActionFilterSourceFile extends ActionFilter {
  private String[] files;

  @Override
  public <F> F convert(ActionFilterBuilder<F, ActionState, String, Instant, Long> filterBuilder) {
    return maybeNegate(filterBuilder.fromFile(Stream.of(files)), filterBuilder);
  }

  /**
   * Gets the files to be checked
   *
   * @return the file names
   */
  public String[] getFiles() {
    return files;
  }

  /**
   * Sets the files to be checked
   *
   * @param files the file names
   */
  public void setFiles(String[] files) {
    this.files = files;
  }
}
