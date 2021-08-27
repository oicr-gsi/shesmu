package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.SubmitWorkflowRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class RunStateDead extends RunState {

  @Override
  public boolean canReattempt() {
    return false;
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
  public PerformResult perform(URI vidarrUrl, SubmitWorkflowRequest request) {
    return new PerformResult(List.of(), ActionState.ZOMBIE, this);
  }

  @Override
  public Optional<RunState> reattempt() {
    return Optional.empty();
  }

  @Override
  public long retryMinutes() {
    return 86400;
  }

  @Override
  public Stream<String> tags() {
    return Stream.of("vidarr-state:dead");
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("runState", "dead");
  }
}
