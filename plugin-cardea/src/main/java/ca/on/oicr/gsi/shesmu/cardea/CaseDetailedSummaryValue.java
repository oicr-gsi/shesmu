package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CaseDetailedSummaryValue {
  private final String assayName;
  private final String assayVersion;
  private final String caseIdentifier;
  private final String caseStatus;
  private final Optional<Instant> completedDate;
  private final Optional<Instant> clinicalCompletedDate;
  private final Set<Tuple> sequencing;
  private final long requisitionId;
  private final String requisitionName;
  private final boolean stopped;
  private final boolean paused;

  public CaseDetailedSummaryValue(
      String assayName,
      String assayVersion,
      String caseIdentifier,
      String caseStatus,
      Instant completedDate,
      Instant clinicalCompletedDate,
      long requisitionId,
      String requisitionName,
      Set<SequencingTestValueToBeDeprecated> sequencingTestValues,
      boolean stopped,
      boolean paused) {
    super();
    this.assayName = assayName;
    this.assayVersion = assayVersion;
    this.caseIdentifier = caseIdentifier;
    this.caseStatus = caseStatus;
    this.completedDate = completedDate == null ? Optional.empty() : Optional.of(completedDate);
    this.clinicalCompletedDate =
        clinicalCompletedDate == null ? Optional.empty() : Optional.of(clinicalCompletedDate);
    this.sequencing =
        sequencingTestValues.stream()
            .map(
                sequencingTest ->
                    new Tuple(
                        sequencingTest.test(),
                        sequencingTest.type(),
                        sequencingTest.limsIds(),
                        sequencingTest.complete()))
            .collect(Collectors.toSet());
    this.requisitionId = requisitionId;
    this.requisitionName = requisitionName;
    this.stopped = stopped;
    this.paused = paused;
  }

  public CaseDetailedSummaryValue(
      String assayName,
      String assayVersion,
      String caseIdentifier,
      String caseStatus,
      Instant completedDate,
      Instant clinicalCompletedDate,
      long requisitionId,
      String requisitionName,
      Stream<SequencingTestDto> sequencingTestDtosStream,
      boolean stopped,
      boolean paused) {
    this(
        assayName,
        assayVersion,
        caseIdentifier,
        caseStatus,
        completedDate,
        clinicalCompletedDate,
        requisitionId,
        requisitionName,
        sequencingTestDtosStream
            .map(
                st ->
                    new SequencingTestValueToBeDeprecated(
                        st.getTest(), st.getType(), st.getLimsIds(), st.isComplete()))
            .collect(Collectors.toUnmodifiableSet()),
        stopped,
        paused);
  }

  @ShesmuVariable
  public String assayName() {
    return assayName;
  }

  @ShesmuVariable
  public String assayVersion() {
    return assayVersion;
  }

  @ShesmuVariable
  public String caseIdentifier() {
    return caseIdentifier;
  }

  @ShesmuVariable
  public String caseStatus() {
    return caseStatus;
  }

  @ShesmuVariable
  public Optional<Instant> completedDate() {
    return completedDate;
  }

  @ShesmuVariable
  public Optional<Instant> clinicalCompletedDate() {
    return clinicalCompletedDate;
  }

  @ShesmuVariable(type = "ao4test$stype$scomplete$blimsIds$ao3id$sqcFailed$bsupplemental$b")
  public Set<Tuple> sequencing() {
    return sequencing;
  }

  @ShesmuVariable
  public long requisitionId() {
    return requisitionId;
  }

  @ShesmuVariable
  public String requisitionName() {
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
