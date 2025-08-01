package ca.on.oicr.gsi.shesmu.cardea;

import java.util.Set;

public class CaseSummaryDto {
  private String assayName;
  private String assayVersion;
  private String caseIdentifier;
  private String caseStatus;
  private String completedDate;
  private Set<String> limsIds;
  private long requisitionId;
  private String requisitionName;

  public String getAssayName() {
    return assayName;
  }

  public void setAssayName(String assayName) {
    this.assayName = assayName;
  }

  public String getAssayVersion() {
    return assayVersion;
  }

  public void setAssayVersion(String assayVersion) {
    this.assayVersion = assayVersion;
  }

  public String getCaseIdentifier() {
    return caseIdentifier;
  }

  public void setCaseIdentifier(String caseIdentifier) {
    this.caseIdentifier = caseIdentifier;
  }

  public String getCaseStatus() {
    return caseStatus;
  }

  public void setCaseStatus(String caseStatus) {
    this.caseStatus = caseStatus;
  }

  public String getCompletedDate() {
    return completedDate;
  }

  public void setCompletedDate(String completedDate) {
    this.completedDate = completedDate;
  }

  public Set<String> getLimsIds() {
    return limsIds;
  }

  public void setLimsIds(Set<String> limsIds) {
    this.limsIds = limsIds;
  }

  public long getRequisitionId() {
    return requisitionId;
  }

  public void setRequisitionId(long requisitionId) {
    this.requisitionId = requisitionId;
  }

  public String getRequisitionName() {
    return requisitionName;
  }

  public void setRequisitionName(String requisitionName) {
    this.requisitionName = requisitionName;
  }
}
