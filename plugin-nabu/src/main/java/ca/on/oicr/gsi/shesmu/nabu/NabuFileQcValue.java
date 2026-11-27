package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.plugin.types.PathOrUri;
import java.time.Instant;
import java.util.Optional;

public class NabuFileQcValue {

  private final Optional<String> comment;
  private final String file_id;
  private final AlgebraicValue file_path;
  private final Optional<String> file_swid;
  private final long fileqc_id;
  private final String project;
  private final Instant qc_date;
  private final Optional<Boolean> qc_passed;
  private final String username;
  private final String workflow;

  public NabuFileQcValue(
      long fileqc_id,
      Optional<String> comment,
      String file_id,
      String file_path,
      Optional<String> file_swid,
      String project,
      Instant qc_date,
      Optional<Boolean> qc_passed,
      String workflow,
      String username) {
    super();
    this.fileqc_id = fileqc_id;
    this.comment = comment;
    this.file_id = file_id;
    // Nabu may return a record without a file path; leave it null, as before
    this.file_path = file_path == null ? null : PathOrUri.parse(file_path);
    this.file_swid = file_swid;
    this.project = project;
    this.qc_date = qc_date;
    this.qc_passed = qc_passed;
    this.workflow = workflow;
    this.username = username;
  }

  @ShesmuVariable
  public Optional<String> comment() {
    return comment;
  }

  @ShesmuVariable
  public String file_id() {
    return file_id;
  }

  /**
   * The file's path or, for remote files (ex. gs://bucket/file), its URI
   *
   * <p>The type is <code>PATH{path} | URI{scheme = string, host = string, name = string}</code>
   */
  @ShesmuVariable(type = PathOrUri.DESCRIPTOR)
  public AlgebraicValue file_path() {
    return file_path;
  }

  @ShesmuVariable
  public Optional<String> file_swid() {
    return file_swid;
  }

  @ShesmuVariable
  public long fileqc_id() {
    return fileqc_id;
  }

  @ShesmuVariable
  public String project() {
    return project;
  }

  @ShesmuVariable
  public Instant qc_date() {
    return qc_date;
  }

  @ShesmuVariable
  public Optional<Boolean> qc_passed() {
    return qc_passed;
  }

  @ShesmuVariable
  public String username() {
    return username;
  }

  @ShesmuVariable
  public String workflow() {
    return workflow;
  }
}
