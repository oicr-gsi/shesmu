package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Optional;

public class ReleaseValue {
  private final String deliverable;
  private final Optional<Instant> qcDate;
  private final Optional<String> qcStatus;
  private final Optional<String> qcUser;

  public ReleaseValue(String deliverable, Instant qcDate, String qcStatus, String qcUser) {
    super();
    this.deliverable = deliverable;
    this.qcDate = qcDate == null ? Optional.empty() : Optional.of(qcDate);
    this.qcStatus = qcStatus == null ? Optional.empty() : Optional.of(qcStatus);
    this.qcUser = qcUser == null ? Optional.empty() : Optional.of(qcUser);
  }

  public ReleaseValue(String deliverable, String qcDate, String qcStatus, String qcUser) {
    this(deliverable, (qcDate == null ? null : Instant.parse(qcDate)), qcStatus, qcUser);
  }

  public ReleaseValue(ReleaseDto r) {
    this(r.getDeliverable(), r.getQcDate(), r.getQcStatus(), r.getQcUser());
  }

  @ShesmuVariable
  public String deliverable() {
    return deliverable;
  }

  @ShesmuVariable
  public Optional<Instant> qc_date() {
    return qcDate;
  }

  @ShesmuVariable
  public Optional<String> qc_status() {
    return qcStatus;
  }

  @ShesmuVariable
  public Optional<String> qcUser() {
    return qcUser;
  }
}
