package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NabuCaseArchiveDto extends NabuArchiveDto {

  private String caseIdentifier;

  public String getCaseIdentifier() {
    return caseIdentifier;
  }

  public void setCaseIdentifier(String caseIdentifier) {
    this.caseIdentifier = caseIdentifier;
  }
}
