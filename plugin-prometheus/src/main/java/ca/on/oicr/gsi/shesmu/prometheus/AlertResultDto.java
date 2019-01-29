package ca.on.oicr.gsi.shesmu.prometheus;

import java.util.List;

/** Bean containing list of alerting results JSON from Alert Manager */
public class AlertResultDto {
  private List<AlertDto> data;
  private String status;

  public List<AlertDto> getData() {
    return data;
  }

  public String getStatus() {
    return status;
  }

  public void setData(List<AlertDto> data) {
    this.data = data;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
