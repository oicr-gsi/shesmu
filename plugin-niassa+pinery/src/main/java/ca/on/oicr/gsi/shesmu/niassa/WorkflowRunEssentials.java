package ca.on.oicr.gsi.shesmu.niassa;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class WorkflowRunEssentials {
  public static final WorkflowRunEssentials EMPTY =
      new WorkflowRunEssentials(null, null, null, Collections.emptyMap(), Collections.emptyMap());
  private final String cromwellId;
  private final Map<String, List<CromwellCall>> cromwellLogs;
  private final String cromwellRoot;
  private final String currentDirectory;
  private final Map<Object, Object> ini;

  public WorkflowRunEssentials(
      String currentDirectory,
      String cromwellId,
      String cromwellRoot,
      Map<Object, Object> ini,
      Map<String, List<CromwellCall>> cromwellLogs) {
    this.currentDirectory = currentDirectory;
    this.cromwellId = cromwellId;
    this.cromwellRoot = cromwellRoot;
    this.ini = ini;
    this.cromwellLogs = cromwellLogs;
  }

  public String cromwellId() {
    return cromwellId;
  }

  public Map<String, List<CromwellCall>> cromwellLogs() {
    return cromwellLogs;
  }

  public String cromwellRoot() {
    return cromwellRoot;
  }

  public String currentDirectory() {
    return currentDirectory;
  }

  public Map<Object, Object> ini() {
    return ini;
  }
}
