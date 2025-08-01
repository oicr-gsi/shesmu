package ca.on.oicr.gsi.shesmu.cardea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.Set;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaseDetailedSummaryDto {
  private String assayName;
  private String assayVersion;
  private String caseIdentifier;
  private String caseStatus;
  private Instant completedDate;
  private Instant clinicalCompletedDate;
  private Set<DeliverableDto> deliverables;
  private Set<SequencingTestDto> sequencing;
  private long requisitionId;
  private String requisitionName;
  private boolean stopped;
  private boolean paused;

  public String getAssayName() {
    return assayName;
  }

  public String getAssayVersion() {
    return assayVersion;
  }

  public String getCaseIdentifier() {
    return caseIdentifier;
  }

  public String getCaseStatus() {
    return caseStatus;
  }

  public Instant getCompletedDate() {
    return completedDate;
  }

  public Instant getClinicalCompletedDate() {
    return clinicalCompletedDate;
  }

  public Set<DeliverableDto> getDeliverables() {
    return deliverables;
  }

  public Set<SequencingTestDto> getSequencing() {
    return sequencing;
  }

  public long getRequisitionId() {
    return requisitionId;
  }

  public String getRequisitionName() {
    return requisitionName;
  }

  public boolean isStopped() {
    return stopped;
  }

  public boolean isPaused() {
    return paused;
  }

  public void setAssayName(String assayName) {
    this.assayName = assayName;
  }

  public void setAssayVersion(String assayVersion) {
    this.assayVersion = assayVersion;
  }

  public void setCaseIdentifier(String caseIdentifier) {
    this.caseIdentifier = caseIdentifier;
  }

  public void setCaseStatus(String caseStatus) {
    this.caseStatus = caseStatus;
  }

  public void setCompletedDate(Instant completedDate) {
    this.completedDate = completedDate;
  }

  public void setClinicalCompletedDate(Instant clinicalCompletedDate) {
    this.clinicalCompletedDate = clinicalCompletedDate;
  }

  public void setDeliverables(Set<DeliverableDto> deliverables) {
    this.deliverables = deliverables;
  }

  public void setSequencing(Set<SequencingTestDto> sequencing) {
    this.sequencing = sequencing;
  }

  public void setRequisitionId(long requisitionId) {
    this.requisitionId = requisitionId;
  }

  public void setRequisitionName(String requisitionName) {
    this.requisitionName = requisitionName;
  }

  public void setStopped(boolean stopped) {
    this.stopped = stopped;
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }
}
