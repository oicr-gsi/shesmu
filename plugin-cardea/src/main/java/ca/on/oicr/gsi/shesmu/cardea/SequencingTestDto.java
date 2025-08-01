package ca.on.oicr.gsi.shesmu.cardea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SequencingTestDto {
  private String test;
  private String type;
  private Set<LimsIdDto> limsIds;
  private boolean complete;

  public String getTest() {
    return test;
  }

  public void setTest(String test) {
    this.test = test;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Set<LimsIdDto> getLimsIds() {
    return limsIds;
  }

  public void setLimsIds(Set<LimsIdDto> limsIds) {
    this.limsIds = limsIds;
  }

  public boolean isComplete() {
    return complete;
  }

  public void setComplete(boolean complete) {
    this.complete = complete;
  }
}
