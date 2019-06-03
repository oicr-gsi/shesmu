package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import java.util.List;
import java.util.stream.Collectors;

public class BamMergeOutputInfo {
  private final List<Pair<String, Integer>> files;
  private final String outputName;

  public BamMergeOutputInfo(String outputName, List<Pair<String, Integer>> files) {
    this.outputName = outputName;
    this.files = files;
  }

  public String files() {
    return files.stream().map(Pair::first).collect(Collectors.joining(","));
  }

  public String iusLimsKeySwids() {
    return files.stream().map(Pair::second).map(Object::toString).collect(Collectors.joining(","));
  }

  public String outputName() {
    return outputName;
  }
}
