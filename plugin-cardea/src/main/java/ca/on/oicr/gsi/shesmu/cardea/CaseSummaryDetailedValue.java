package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CaseSummaryDetailedValue {
  private final String assayName;
  private final String assayVersion;
  private final String caseIdentifier;
  private final String caseStatus;
  private final Optional<Instant> completedDate;
  private final Set<Tuple> sequencing;
  private final long requisitionId;
  private final String requisitionName;
  private final boolean stopped;
  private final boolean paused;

  public CaseSummaryDetailedValue(
      String assayName,
      String assayVersion,
      String caseIdentifier,
      String caseStatus,
      Optional<Instant> completedDate,
      Stream<SequencingTestValue> sequencingTestValueStream,
      long requisitionId,
      String requisitionName,
      boolean stopped,
      boolean paused) {
    super();
    this.assayName = assayName;
    this.assayVersion = assayVersion;
    this.caseIdentifier = caseIdentifier;
    this.caseStatus = caseStatus;
    this.completedDate = completedDate;
    this.sequencing =
        sequencingTestValueStream
            .map(
                sequencingTest ->
                    new Tuple(
                        sequencingTest.test(),
                        sequencingTest.type(),
                        sequencingTest.isComplete(),
                        sequencingTest.limsIds()))
            .collect(Collectors.toSet());
    this.requisitionId = requisitionId;
    this.requisitionName = requisitionName;
    this.stopped = stopped;
    this.paused = paused;
  }

  @ShesmuVariable
  public String assay_name() {
    return assayName;
  }

  @ShesmuVariable
  public String assay_version() {
    return assayVersion;
  }

  @ShesmuVariable
  public String case_identifier() {
    return caseIdentifier;
  }

  @ShesmuVariable
  public String case_status() {
    return caseStatus;
  }

  @ShesmuVariable
  public Optional<Instant> completed_date() {
    return completedDate;
  }

  @ShesmuVariable(type = "ao4test$stype$sis_complete$blims_ids$ao2lims_id$ssupplemental$b")
  public Set<Tuple> sequencing() {
    return sequencing;
  }

  @ShesmuVariable
  public long requisition_id() {
    return requisitionId;
  }

  @ShesmuVariable
  public String requisition_name() {
    return requisitionName;
  }

  @ShesmuVariable
  public boolean stopped() {
    return stopped;
  }

  @ShesmuVariable
  public boolean paused() {
    return paused;
  }
}
