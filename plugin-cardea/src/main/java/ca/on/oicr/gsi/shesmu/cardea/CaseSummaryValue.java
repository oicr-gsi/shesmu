package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public class CaseSummaryValue {
  private final String assayName;
  private final String assayVersion;
  private final String caseIdentifier;
  private final String caseStatus;
  private final Optional<Instant> completedDate;
  private final Set<String> limsIds;
  private final long requisitionId;
  private final String requisitionName;

  public CaseSummaryValue(
      String assayName,
      String assayVersion,
      String caseIdentifier,
      String caseStatus,
      Optional<Instant> completedDate,
      Set<String> limsIds,
      long requisitionId,
      String requisitionName) {
    super();
    this.assayName = assayName;
    this.assayVersion = assayVersion;
    this.caseIdentifier = caseIdentifier;
    this.caseStatus = caseStatus;
    this.completedDate = completedDate;
    this.limsIds = limsIds;
    this.requisitionId = requisitionId;
    this.requisitionName = requisitionName;
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
  public Set<String> limsIds() {
    return limsIds;
  }

  @ShesmuVariable
  public long requisitionId() {
    return requisitionId;
  }

  @ShesmuVariable
  public String requisitionName() {
    return requisitionName;
  }
}
