package ca.on.oicr.gsi.shesmu.cardea;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Set;

/** This class allows for proper deserialization of JSON data */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeliverableDto {
  private String deliverableCategory;
  private boolean analysisReviewSkipped;
  private String analysisReviewQcDate;
  private String analysisReviewQcStatus;
  private String analysisReviewQcUser;
  private String releaseApprovalQcDate;
  private String releaseApprovalQcStatus;
  private String releaseApprovalQcUser;
  private Set<ReleaseDto> releases;

  public String getDeliverableCategory() {
    return deliverableCategory;
  }

  public void setDeliverableCategory(String deliverableCategory) {
    this.deliverableCategory = deliverableCategory;
  }

  public boolean isAnalysisReviewSkipped() {
    return analysisReviewSkipped;
  }

  public void setAnalysisReviewSkipped(boolean analysisReviewSkipped) {
    this.analysisReviewSkipped = analysisReviewSkipped;
  }

  public String getAnalysisReviewQcDate() {
    return analysisReviewQcDate;
  }

  public void setAnalysisReviewQcDate(String analysisReviewQcDate) {
    this.analysisReviewQcDate = analysisReviewQcDate;
  }

  public String getAnalysisReviewQcStatus() {
    return analysisReviewQcStatus;
  }

  public void setAnalysisReviewQcStatus(String analysisReviewQcStatus) {
    this.analysisReviewQcStatus = analysisReviewQcStatus;
  }

  public String getAnalysisReviewQcUser() {
    return analysisReviewQcUser;
  }

  public void setAnalysisReviewQcUser(String analysisReviewQcUser) {
    this.analysisReviewQcUser = analysisReviewQcUser;
  }

  public String getReleaseApprovalQcDate() {
    return releaseApprovalQcDate;
  }

  public void setReleaseApprovalQcDate(String releaseApprovalQcDate) {
    this.releaseApprovalQcDate = releaseApprovalQcDate;
  }

  public String getReleaseApprovalQcStatus() {
    return releaseApprovalQcStatus;
  }

  public void setReleaseApprovalQcStatus(String releaseApprovalQcStatus) {
    this.releaseApprovalQcStatus = releaseApprovalQcStatus;
  }

  public String getReleaseApprovalQcUser() {
    return releaseApprovalQcUser;
  }

  public void setReleaseApprovalQcUser(String releaseApprovalQcUser) {
    this.releaseApprovalQcUser = releaseApprovalQcUser;
  }

  public Set<ReleaseDto> getReleases() {
    return releases;
  }

  public void setReleases(Set<ReleaseDto> releases) {
    this.releases = releases;
  }
}
