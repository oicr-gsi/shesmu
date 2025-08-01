package ca.on.oicr.gsi.shesmu.cardea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReleaseDto {
  private String deliverable;
  private String qcDate;
  private String qcStatus;
  private String qcUser;

  public String getDeliverable() {
    return deliverable;
  }

  public void setDeliverable(String deliverable) {
    this.deliverable = deliverable;
  }

  public String getQcDate() {
    return qcDate;
  }

  public void setQcDate(String qcDate) {
    this.qcDate = qcDate;
  }

  public String getQcStatus() {
    return qcStatus;
  }

  public void setQcStatus(String qcStatus) {
    this.qcStatus = qcStatus;
  }

  public String getQcUser() {
    return qcUser;
  }

  public void setQcUser(String qcUser) {
    this.qcUser = qcUser;
  }
}
