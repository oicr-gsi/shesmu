package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public class NabuValue {
  private final Optional<String> comment;
  private final String fileid;
  private final Path filepath;
  private final long fileqcid;
  private final Optional<String> fileswid;
  private final String project;
  private final Instant qcdate;
  private final Optional<Boolean> qcpassed;
  private final String workflow;
  private final String username;

  public NabuValue(
      long fileqcid,
      Optional<String> comment,
      String fileid,
      Path filepath,
      Optional<String> fileswid,
      String project,
      Instant qcdate,
      Optional<Boolean> qcpassed,
      String workflow,
      String username) {
    super();
    this.fileqcid = fileqcid;
    this.comment = comment;
    this.fileid = fileid;
    this.filepath = filepath;
    this.fileswid = fileswid;
    this.project = project;
    this.qcdate = qcdate;
    this.qcpassed = qcpassed;
    this.workflow = workflow;
    this.username = username;
  }

  @ShesmuVariable
  public Optional<String> comment() {
    return comment;
  }

  @ShesmuVariable
  public String fileid() {
    return fileid;
  }

  @ShesmuVariable
  public Path filepath() {
    return filepath;
  }

  @ShesmuVariable
  public long fileqcid() {
    return fileqcid;
  }

  @ShesmuVariable
  public Optional<String> fileswid() {
    return fileswid;
  }

  @ShesmuVariable
  public String project() {
    return project;
  }

  @ShesmuVariable
  public Instant qcdate() {
    return qcdate;
  }

  @ShesmuVariable
  public Optional<Boolean> qcpassed() {
    return qcpassed;
  }

  @ShesmuVariable
  public String workflow() {
    return workflow;
  }

  @ShesmuVariable
  public String username() {
    return username;
  }
}
