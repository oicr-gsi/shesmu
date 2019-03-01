package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

public class SymlinkAction extends Action {
  private static final Gauge filesInTheWay =
      Gauge.build(
              "shesmu_sftp_files_in_the_way",
              "The number of non-symlink files preventing the creation of a symlink.")
          .labelNames("target")
          .register();
  private final SftpServer connection;
  private boolean fileInTheWay;
  private String link;
  private String target;

  public SymlinkAction(SftpServer connection) {
    super("sftp-symlink");
    this.connection = connection;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SymlinkAction that = (SymlinkAction) o;
    return Objects.equals(link, that.link) && Objects.equals(target, that.target);
  }

  @Override
  public int hashCode() {
    return Objects.hash(link, target);
  }

  @ActionParameter
  public void link(Path link) {
    this.link = link.toString();
  }

  @Override
  public ActionState perform(ActionServices services) {
    // The logic for creating the symlink happens in the other class so that it can make serialised
    // requests to the remote end
    final Pair<ActionState, Boolean> result = connection.makeSymlink(link, target, fileInTheWay);
    if (result.second() != fileInTheWay) {
      filesInTheWay
          .labels(connection.name())
          .inc((result.second() ? 1 : 0) - (fileInTheWay ? 1 : 0));
      fileInTheWay = result.second();
    }
    return result.first();
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
    return query.matcher(link).matches() || query.matcher(target).matches();
  }

  @ActionParameter
  public void target(Path target) {
    this.target = target.toString();
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final ObjectNode node = mapper.createObjectNode();
    node.put("link", link);
    node.put("target", target);
    node.put("instance", connection.name());
    return node;
  }
}
