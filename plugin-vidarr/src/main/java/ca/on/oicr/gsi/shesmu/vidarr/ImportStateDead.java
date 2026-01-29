package ca.on.oicr.gsi.shesmu.vidarr;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.vidarr.api.ImportRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class ImportStateDead extends ImportState {
  @Override
  public AvailableCommands commands() {
    return AvailableCommands.RESET_ONLY;
  }

  @Override
  public PerformResult perform(
      URI vidarrUrl, ImportRequest request, Duration lastGeneratedByOlive, boolean isOliveLive)
      throws IOException, InterruptedException {
    return new PerformResult(List.of(), ActionState.ZOMBIE, this);
  }

  @Override
  public Optional<ImportState> reattempt() {
    return Optional.empty();
  }

  @Override
  public boolean retry(URI vidarrUrl) {
    return false;
  }

  @Override
  public long retryMinutes() {
    return 86400;
  }

  @Override
  public boolean search(Pattern query) {
    return false;
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
    return Stream.of("vidarr-state:dead");
  }

  @Override
  public void writeJson(ObjectMapper mapper, ObjectNode node) {
    node.put("importState", "dead");
  }
}
