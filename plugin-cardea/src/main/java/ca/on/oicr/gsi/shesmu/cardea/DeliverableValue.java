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
  public String caseIdentifier() {
    return caseIdentifier;
  }

  @ShesmuVariable
  public String deliverableCategory() {
    return deliverableCategory;
  }

  @ShesmuVariable
  public boolean analysisReviewSkipped() {
    return analysisReviewSkipped;
  }

  @ShesmuVariable
  public Optional<Instant> analysisReviewQcDate() {
    return analysisReviewQcDate;
  }

  @ShesmuVariable
  public Optional<String> analysisReviewQcStatus() {
    return analysisReviewQcStatus;
  }

  @ShesmuVariable
  public Optional<String> analysisReviewQcUser() {
    return analysisReviewQcUser;
  }

  @ShesmuVariable
  public Optional<Instant> releaseApprovalQcDate() {
    return releaseApprovalQcDate;
  }

  @ShesmuVariable
  public Optional<String> releaseApprovalQcStatus() {
    return releaseApprovalQcStatus;
  }

  @ShesmuVariable
  public Optional<String> releaseApprovalQcUser() {
    return releaseApprovalQcUser;
  }

  @ShesmuVariable
  public String deliverable() {
    return deliverable;
  }

  @ShesmuVariable
  public Optional<Instant> releaseQcDate() {
    return releaseQcDate;
  }

  @ShesmuVariable
  public Optional<String> releaseQcStatus() {
    return releaseQcStatus;
  }

  @ShesmuVariable
  public Optional<String> releaseQcUser() {
    return releaseQcUser;
  }
}
