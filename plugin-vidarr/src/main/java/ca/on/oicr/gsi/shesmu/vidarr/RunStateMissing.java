package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.ExternalKey;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

final class RunStateMissing extends RunState {
  private final List<String> errors;
  private final String id;
  private final List<ExternalKey> keys;

  public RunStateMissing(String id, List<ExternalKey> keys) {
    this.id = id;
    this.keys = keys;
    errors =
        Stream.concat(
                Stream.of("This workflow probably needs to be reprocessed."),
                keys.stream()
                    .map(
                        k ->
                            String.format(
                                "LIMS key %s/%s does not have a version that overlaps with LIMS key in Vidarr workflow run %s.",
                                k.getProvider(), k.getId(), id)))
            .collect(Collectors.toList());
  }

  @Override
  public boolean search(Pattern query) {
    return query.matcher(id).matches();
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
    return new PerformResult(errors, ActionState.FAILED, this);
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
    return Stream.of("vidarr-state:missing");
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("runState", "missingKeys");
    node.put("missingVersion", id);
    node.putPOJO("corruptExternalIds", keys);
  }
}
