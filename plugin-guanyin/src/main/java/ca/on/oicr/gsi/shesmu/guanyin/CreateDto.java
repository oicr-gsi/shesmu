package ca.on.oicr.gsi.shesmu.guanyin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bean of record responses from Guanyin
 *
 * <p>This does not map all fields: only the ones actually consume by Shesmu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreateDto {
  private long id;

  @JsonProperty("report_record_id")
  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }
}
