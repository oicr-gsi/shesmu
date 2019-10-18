package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.Gang;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;

public class TestValue {
  private final String accession;
  private final long file_size;
  private final long library_size;
  private final Path path;
  private final String project;
  private final Instant timestamp;
  private final String workflow;
  private final Tuple workflow_version;
  private final Set<String> stuff = new TreeSet<>();

  public TestValue(
      String accession,
      String path,
      long file_size,
      String workflow,
      Tuple workflow_version,
      String project,
      long library_size,
      Instant timestamp) {
    super();
    this.accession = accession;
    this.path = Paths.get(path);
    this.file_size = file_size;
    this.workflow = workflow;
    this.workflow_version = workflow_version;
    this.project = project;
    this.library_size = library_size;
    this.timestamp = timestamp;
    stuff.add(accession);
    stuff.add(workflow);
    stuff.add(project);
  }

  @ShesmuVariable(type = "as")
  public Set<String> stuff() {
    return stuff;
  }

  @ShesmuVariable(type = "s")
  public String accession() {
    return accession;
  }

  @ShesmuVariable(type = "i")
  public long file_size() {
    return file_size;
  }

  @ShesmuVariable(
      type = "i",
      signable = true,
      gangs = {@Gang(name = "useful_stuff", order = 1)})
  public long library_size() {
    return library_size;
  }

  @ShesmuVariable
  public Path path() {
    return path;
  }

  @ShesmuVariable(
      type = "s",
      signable = true,
      gangs = {@Gang(name = "useful_stuff", order = 0), @Gang(name = "workflow_run", order = 0)})
  public String project() {
    return project;
  }

  @ShesmuVariable(type = "d")
  public Instant timestamp() {
    return timestamp;
  }

  @ShesmuVariable(
      type = "s",
      gangs = {@Gang(name = "workflow_run", order = 1)})
  public String workflow() {
    return workflow;
  }

  @ShesmuVariable(type = "t3iii")
  public Tuple workflow_version() {
    return workflow_version;
  }
}
