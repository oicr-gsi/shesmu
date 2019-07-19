package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class DeleteAction extends Action {
  private final Supplier<SftpServer> connection;
  private Path target;

  public DeleteAction(Supplier<SftpServer> connection) {
    super("sftp-rm");
    this.connection = connection;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DeleteAction that = (DeleteAction) o;
    return Objects.equals(target, that.target);
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return Optional.empty();
  }

  @Override
  public int hashCode() {
    return Objects.hash(target);
  }

  @Override
  public ActionState perform(ActionServices services) {
    // The logic for removing happens in the other class so that it can make
    // serialised requests to the remote end
    return connection.get().rm(target.toString()) ? ActionState.SUCCEEDED : ActionState.FAILED;
  }

  @Override
  public int priority() {
    return 0;
  }

  @Override
  public long retryMinutes() {
    return 10;
  }

  @Override
  public boolean search(Pattern query) {
    return query.matcher(target.toString()).matches();
  }

  @ActionParameter
  public void target(Path target) {
    this.target = target;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("target", target.toString());
    node.put("instance", connection.get().name());
    return node;
  }
}
