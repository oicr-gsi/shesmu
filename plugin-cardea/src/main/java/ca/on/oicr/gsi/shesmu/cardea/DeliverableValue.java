package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Optional;

public class DeliverableValue {
  private final String caseIdentifier;
  private final String deliverableCategory;
  private final boolean analysisReviewSkipped;
  private final Optional<Instant> analysisReviewQcDate;
  private final Optional<String> analysisReviewQcStatus;
  private final Optional<String> analysisReviewQcUser;
  private final Optional<Instant> releaseApprovalQcDate;
  private final Optional<String> releaseApprovalQcStatus;
  private final Optional<String> releaseApprovalQcUser;
  private final String deliverable;
  private final Optional<Instant> releaseQcDate;
  private final Optional<String> releaseQcStatus;
  private final Optional<String> releaseQcUser;

  public DeliverableValue(
      String caseIdentifier,
      String deliverableCategory,
      boolean analysisReviewSkipped,
      String analysisReviewQcDate,
      String analysisReviewQcStatus,
      String analysisReviewQcUser,
      String releaseApprovalQcDate,
      String releaseApprovalQcStatus,
      String releaseApprovalQcUser,
      String deliverable,
      String releaseQcDate,
      String releaseQcStatus,
      String releaseQcUser) {
    super();
    this.caseIdentifier = caseIdentifier;
    this.deliverableCategory = deliverableCategory;
    this.analysisReviewSkipped = analysisReviewSkipped;
    this.analysisReviewQcDate =
        analysisReviewQcDate == null
            ? Optional.empty()
            : Optional.of(Instant.parse(analysisReviewQcDate));
    this.analysisReviewQcStatus =
        analysisReviewQcStatus == null ? Optional.empty() : Optional.of(analysisReviewQcStatus);
    this.analysisReviewQcUser =
        analysisReviewQcUser == null ? Optional.empty() : Optional.of(analysisReviewQcUser);
    this.releaseApprovalQcDate =
        releaseApprovalQcDate == null
            ? Optional.empty()
            : Optional.of(Instant.parse(releaseApprovalQcDate));
    this.releaseApprovalQcStatus =
        releaseApprovalQcStatus == null ? Optional.empty() : Optional.of(releaseApprovalQcStatus);
    this.releaseApprovalQcUser =
        releaseApprovalQcUser == null ? Optional.empty() : Optional.of(releaseApprovalQcUser);
    this.deliverable = deliverable;
    this.releaseQcDate =
        releaseQcDate == null ? Optional.empty() : Optional.of(Instant.parse(releaseQcDate));
    this.releaseQcStatus =
        releaseQcStatus == null ? Optional.empty() : Optional.of(releaseQcStatus);
    this.releaseQcUser = releaseQcUser == null ? Optional.empty() : Optional.of(releaseQcUser);
  }

  @ShesmuVariable
  public String case_identifier() {
    return caseIdentifier;
  }

  @ShesmuVariable
  public String deliverable_category() {
    return deliverableCategory;
  }

  @ShesmuVariable
  public boolean analysis_review_skipped() {
    return analysisReviewSkipped;
  }

  @ShesmuVariable
  public Optional<Instant> analysis_review_qc_date() {
    return analysisReviewQcDate;
  }

  @ShesmuVariable
  public Optional<String> analysis_review_qc_status() {
    return analysisReviewQcStatus;
  }

  @ShesmuVariable
  public Optional<String> analysis_review_qc_user() {
    return analysisReviewQcUser;
  }

  @ShesmuVariable
  public Optional<Instant> release_approval_qc_date() {
    return releaseApprovalQcDate;
  }

  @ShesmuVariable
  public Optional<String> release_approval_qc_status() {
    return releaseApprovalQcStatus;
  }

  @ShesmuVariable
  public Optional<String> release_approval_qc_user() {
    return releaseApprovalQcUser;
  }

  @ShesmuVariable
  public String deliverable() {
    return deliverable;
  }

  @ShesmuVariable
  public Optional<Instant> release_qc_date() {
    return releaseQcDate;
  }

  @ShesmuVariable
  public Optional<String> release_qc_status() {
    return releaseQcStatus;
  }

  @ShesmuVariable
  public Optional<String> release_qc_user() {
    return releaseQcUser;
  }
}
