package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.vidarr.api.SubmitMode;
import java.time.Duration;

/** Determines when a workflow run should be actually submitted */
public interface SubmissionPolicy {
  static SubmissionPolicy ALWAYS = (lastGeneratedByAnOlive, isOliveLive) -> SubmitMode.RUN;
  static SubmissionPolicy DRY_RUN = (lastGeneratedByAnOlive, isOliveLive) -> SubmitMode.DRY_RUN;
  static SubmissionPolicy IS_LIVE =
      (lastGeneratedByAnOlive, isOliveLive) -> isOliveLive ? SubmitMode.RUN : SubmitMode.DRY_RUN;

  static SubmissionPolicy maxDelay(long maximum) {
    return (lastGeneratedByAnOlive, isOliveLive) ->
        lastGeneratedByAnOlive.getSeconds() > maximum ? SubmitMode.DRY_RUN : SubmitMode.RUN;
  }

  SubmitMode mode(Duration lastGeneratedByAnOlive, boolean isOliveLive);
}
