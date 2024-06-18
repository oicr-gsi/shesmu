package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class RunStateConflicted extends RunState {

  private final List<String> errors;
  private final List<String> ids;

  public RunStateConflicted(List<String> ids) {
    super();
    this.ids = ids;
    errors =
        List.of(
            "Multiple possible Vidarr matches! Fix workflow runs in Vidarr and reset this action or change the olive and purge this action: "
                + String.join(", ", ids));
  }

  @Override
  public boolean search(Pattern query) {
    return ids.stream().anyMatch(id -> query.matcher(id).matches());
  }

  @Override
  public AvailableCommands commands() {
    return AvailableCommands.RESET_ONLY;
  }

  @Override
  public boolean delete(URI vidarrUrl) {
    return false;
  }

  public List<String> errors() {
    return errors;
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return Optional.empty();
  }

  @Override
  public OptionalInt getAttempt() {
    return OptionalInt.empty();
  }

  @Override
  public PerformResult perform(
      URI vidarrUrl,
      SubmitWorkflowRequest request,
      SubmissionPolicy submissionPolicy,
      Duration lastGeneratedByOlive,
      boolean isOliveLive) {
    return new PerformResult(errors, ActionState.HALP, this);
  }

  @Override
  public Optional<RunState> reattempt() {
    return Optional.empty();
  }

  @Override
  public boolean retry(URI vidarrUrl) {
    return false;
  }

  @Override
  public long retryMinutes() {
    return 60;
  }

  @Override
  public OptionalInt sortKey(String key) {
    return OptionalInt.empty();
  }

  @Override
  public Stream<String> sortKeys() {
    return Stream.empty();
  }

  @Override
  public Stream<String> tags() {
    return Stream.of("vidarr-state:conflict");
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("runState", "conflict");
    ids.forEach(node.putArray("possibleMatches")::add);
  }
}
