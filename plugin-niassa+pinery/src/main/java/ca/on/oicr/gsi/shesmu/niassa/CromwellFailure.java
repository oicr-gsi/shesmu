package ca.on.oicr.gsi.shesmu.niassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CromwellFailure {
  private List<CromwellFailure> causedBy;
  private String message;

  public List<CromwellFailure> getCausedBy() {
    return causedBy;
  }

  public String getMessage() {
    return message;
  }

  public void setCausedBy(List<CromwellFailure> causedBy) {
    this.causedBy = causedBy;
  }

  public void setMessage(String message) {
    this.message = message;
  }
}
