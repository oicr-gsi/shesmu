package ca.on.oicr.gsi.shesmu.nabu;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Path;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NabuFileQcDto {

  private String comment;

  @JsonProperty("fileid")
  private String fileId;

  @JsonProperty("filepath")
  private Path filePath;

  @JsonProperty("fileqcid")
  private long fileQcId;

  @JsonProperty("fileswid")
  private String fileSwid;

  private String project;

  @JsonProperty("qcdate")
  private String qcDate;

  @JsonProperty("qcpassed")
  private Boolean qcPassed;

  private String username;
  private String workflow;

  public String getComment() {
    return comment;
  }

  public String getFileId() {
    return fileId;
  }

  public Path getFilePath() {
    return filePath;
  }

  public long getFileQcId() {
    return fileQcId;
  }

  public String getFileSwid() {
    return fileSwid;
  }

  public String getProject() {
    return project;
  }

  public String getQcDate() {
    return qcDate;
  }

  public Boolean getQcPassed() {
    return qcPassed;
  }

  public String getUsername() {
    return username;
  }

  public String getWorkflow() {
    return workflow;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public void setFileId(String fileId) {
    this.fileId = fileId;
  }

  public void setFilePath(Path filePath) {
    this.filePath = filePath;
  }

  public void setFileQcId(long fileQcId) {
    this.fileQcId = fileQcId;
  }

  public void setFileSwid(String fileSwid) {
    this.fileSwid = fileSwid;
  }

  public void setProject(String project) {
    this.project = project;
  }

  public void setQcDate(String qcDate) {
    this.qcDate = qcDate;
  }

  public void setQcPassed(Boolean qcPassed) {
    this.qcPassed = qcPassed;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public void setWorkflow(String workflow) {
    this.workflow = workflow;
  }
}
