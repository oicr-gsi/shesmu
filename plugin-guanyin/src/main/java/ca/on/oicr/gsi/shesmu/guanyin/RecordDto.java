package ca.on.oicr.gsi.shesmu.guanyin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bean of record responses from Guanyin
 *
 * <p>This does not map all fields: only the ones actually consume by Shesmu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordDto {
  private boolean finished;
  private String generated;
  private long id;
  private long report;

  @JsonProperty("date_generated")
  public String getGenerated() {
    return generated;
  }

  @JsonProperty("report_record_id")
  public long getId() {
    return id;
  }

  @JsonProperty("report_id")
  public long getReport() {
    return report;
  }

  public boolean isFinished() {
    return finished;
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  public void setGenerated(String generated) {
    this.generated = generated;
  }

  public void setId(long id) {
    this.id = id;
  }

  public void setReport(long report) {
    this.report = report;
  }
}
