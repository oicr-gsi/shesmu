package ca.on.oicr.gsi.shesmu.guanyin;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/**
 * Bean of record responses from Guanyin
 *
 * <p>This does not map all fields: only the ones actually consume by Shesmu
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecordDto {
  private List<String> filesIn;
  private boolean finished;
  private String freshestInputDate;
  private String generated;
  private long id;
  private boolean notificationDone;
  private ObjectNode parameters;
  private long report;
  private String reportPath;

  @JsonProperty("files_in")
  public List<String> getFilesIn() {
    return filesIn;
  }

  @JsonProperty("freshest_input_date")
  public String getFreshestInputDate() {
    return freshestInputDate;
  }

  @JsonProperty("date_generated")
  public String getGenerated() {
    return generated;
  }

  @JsonProperty("report_record_id")
  public long getId() {
    return id;
  }

  public ObjectNode getParameters() {
    return parameters;
  }

  @JsonProperty("report_id")
  public long getReport() {
    return report;
  }

  @JsonProperty("report_path")
  public String getReportPath() {
    return reportPath;
  }

  public boolean isFinished() {
    return finished;
  }

  @JsonProperty("notification_done")
  public boolean isNotificationDone() {
    return notificationDone;
  }

  public void setFilesIn(List<String> filesIn) {
    this.filesIn = filesIn;
  }

  public void setFinished(boolean finished) {
    this.finished = finished;
  }

  public void setFreshestInputDate(String freshestInputDate) {
    this.freshestInputDate = freshestInputDate;
  }

  public void setGenerated(String generated) {
    this.generated = generated;
  }

  public void setId(long id) {
    this.id = id;
  }

  public void setNotificationDone(boolean notificationDone) {
    this.notificationDone = notificationDone;
  }

  public void setParameters(ObjectNode parameters) {
    this.parameters = parameters;
  }

  public void setReport(long report) {
    this.report = report;
  }

  public void setReportPath(String reportPath) {
    this.reportPath = reportPath;
  }
}
