package ca.on.oicr.gsi.shesmu.cardea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LimsIdDto {
  private String id;
  private boolean supplemental;
  private boolean qcFailed;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isSupplemental() {
    return supplemental;
  }

  public void setSupplemental(boolean supplemental) {
    this.supplemental = supplemental;
  }

  public boolean isQcFailed() {
    return qcFailed;
  }

  public void setQcFailed(boolean qcFailed) {
    this.qcFailed = qcFailed;
  }
}
