package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

public class NabuValue {
  private final Optional<String> comment;
  private final boolean deleted;
  private final Path filepath;
  private final long fileqcid;
  private final String fileswid;
  private final String project;
  private final Instant qcdate;
  private final Optional<Boolean> qcpassed;
  private final String username;

  public NabuValue(
      long fileqcid,
      Optional<String> comment,
      boolean deleted,
      Path filepath,
      String fileswid,
      String project,
      Instant qcdate,
      Optional<Boolean> qcpassed,
      String username) {
    super();
    this.fileqcid = fileqcid;
    this.comment = comment;
    this.deleted = deleted;
    this.filepath = filepath;
    this.fileswid = fileswid;
    this.project = project;
    this.qcdate = qcdate;
    this.qcpassed = qcpassed;
    this.username = username;
  }

  @ShesmuVariable
  public Optional<String> comment() {
    return comment;
  }

  @ShesmuVariable
  public boolean deleted() {
    return deleted;
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
  public String fileswid() {
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
  public String username() {
    return username;
  }
}
