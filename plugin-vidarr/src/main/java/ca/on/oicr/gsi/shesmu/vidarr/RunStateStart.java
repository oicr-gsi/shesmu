package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class RunStateStart extends RunState {
  @Override
  public boolean search(Pattern query) {
    return false;
  }

  @Override
  public AvailableCommands commands() {
    return null;
  }

  @Override
  public boolean delete(URI vidarrUrl) {
    return false;
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
      boolean isOliveLive)
      throws IOException, InterruptedException {
    return null;
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
    return 0;
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
    return Stream.of("vidarr-state:start");
  }

  @Override
  public void writeJson(JsonMapper mapper, ObjectNode node) {
    node.put("runState", "attempt");
  }
}
