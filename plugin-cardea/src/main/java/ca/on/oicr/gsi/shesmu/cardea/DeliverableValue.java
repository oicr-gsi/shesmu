package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeliverableValue {
  private final String deliverableCategory;
  private final boolean analysisReviewSkipped;
  private final Optional<Instant> analysisReviewQcDate;
  private final Optional<String> analysisReviewQcStatus;
  private final Optional<String> analysisReviewQcUser;
  private final Optional<Instant> releaseApprovalQcDate;
  private final Optional<String> releaseApprovalQcStatus;
  private final Optional<String> releaseApprovalQcUser;
  private final Set<Tuple> releases;

  public DeliverableValue(
      String deliverableCategory,
      boolean analysisReviewSkipped,
      Instant analysisReviewQcDate,
      String analysisReviewQcStatus,
      String analysisReviweQcUser,
      Instant releaseApprovalQcDate,
      String releaseApprovalQcStatus,
      String releaseApprovalQcUser,
      Set<ReleaseValue> releaseValues) {
    super();
    this.deliverableCategory = deliverableCategory;
    this.analysisReviewSkipped = analysisReviewSkipped;
    this.analysisReviewQcDate =
        analysisReviewQcDate == null ? Optional.empty() : Optional.of(analysisReviewQcDate);
    this.analysisReviewQcStatus =
        analysisReviewQcStatus == null ? Optional.empty() : Optional.of(analysisReviewQcStatus);
    this.analysisReviewQcUser =
        analysisReviweQcUser == null ? Optional.empty() : Optional.of(analysisReviweQcUser);
    this.releaseApprovalQcDate =
        releaseApprovalQcDate == null ? Optional.empty() : Optional.of(releaseApprovalQcDate);
    this.releaseApprovalQcStatus =
        releaseApprovalQcStatus == null ? Optional.empty() : Optional.of(releaseApprovalQcStatus);
    this.releaseApprovalQcUser =
        releaseApprovalQcUser == null ? Optional.empty() : Optional.of(releaseApprovalQcUser);
    this.releases =
        releaseValues.stream()
            .map(
                release ->
                    new Tuple(
                        release.deliverable(),
                        release.qcDate(),
                        release.qcStatus(),
                        release.qcUser()))
            .collect(Collectors.toSet());
  }

  public DeliverableValue(
      String deliverableCategory,
      boolean analysisReviewSkipped,
      String analysisReviewQcDate,
      String analysisReviewQcStatus,
      String analysisReviweQcUser,
      String releaseApprovalQcDate,
      String releaseApprovalQcStatus,
      String releaseApprovalQcUser,
      Stream<ReleaseDto> releasesDtosStream) {
    this(
        deliverableCategory,
        analysisReviewSkipped,
        analysisReviewQcDate == null ? null : Instant.parse(analysisReviewQcDate),
        analysisReviewQcStatus,
        analysisReviweQcUser,
        releaseApprovalQcDate == null ? null : Instant.parse(releaseApprovalQcDate),
        releaseApprovalQcStatus,
        releaseApprovalQcUser,
        releasesDtosStream
            .map(
                r ->
                    new ReleaseValue(
                        r.getDeliverable(), r.getQcDate(), r.getQcStatus(), r.getQcUser()))
            .collect(Collectors.toUnmodifiableSet()));
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

  @ShesmuVariable(type = "ao4deliverable$sqcDate$qiqcStatus$qsqcUser$qs")
  public Set<Tuple> releases() {
    return releases;
  }
}
