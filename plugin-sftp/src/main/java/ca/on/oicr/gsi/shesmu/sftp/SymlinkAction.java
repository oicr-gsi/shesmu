package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionCommand.Preference;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Gauge;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SymlinkAction extends Action {
  private static final ActionCommand<SymlinkAction> HUMAN_APPROVE_COMMAND =
      new ActionCommand<>(
          SymlinkAction.class,
          "SFTP-HUMAN-APPROVE",
          FrontEndIcon.HAND_THUMBS_UP,
          "Allow to run",
          Preference.ALLOW_BULK) {

        @Override
        protected Response execute(SymlinkAction action, Optional user) {
          if (!action.automatic) {
            action.automatic = true;
            return Response.ACCEPTED;
          }
          return Response.IGNORED;
        }
      };
  private static final Gauge filesInTheWay =
      Gauge.build(
              "shesmu_sftp_files_in_the_way",
              "The number of non-symlink files preventing the creation of a symlink.")
          .labelNames("target")
          .register();

  @ActionParameter(required = false)
  public boolean automatic = true;

  private final Supplier<SftpServer> connection;
  private boolean fileInTheWay;
  private boolean force;
  private Path link;
  private Optional<Instant> mtime = Optional.empty();
  private Path target;

  public SymlinkAction(Supplier<SftpServer> connection) {
    super("sftp-symlink");
    this.connection = connection;
  }

  @Override
  public Stream<ActionCommand<?>> commands() {
    return automatic ? Stream.empty() : Stream.of(HUMAN_APPROVE_COMMAND);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    var that = (SymlinkAction) o;
    return Objects.equals(link, that.link) && Objects.equals(target, that.target);
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return mtime;
  }

  @ActionParameter(required = false)
  public void force(boolean force) {
    this.force = force;
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(link.toString().getBytes(StandardCharsets.UTF_8));
    digest.accept(new byte[] {0});
    digest.accept(target.toString().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public int hashCode() {
    return Objects.hash(link, target);
  }

  @ActionParameter
  public void link(Path link) {
    this.link = link;
  }

  @Override
  public ActionState perform(ActionServices services) {
    // The logic for creating the symlink happens in the other class so that it can make serialised
    // requests to the remote end
    final var result =
        connection
            .get()
            .makeSymlink(
                link,
                target.toString(),
                force,
                fileInTheWay,
                t -> mtime = Optional.of(t),
                automatic);
    if (result.second() != fileInTheWay) {
      filesInTheWay
          .labels(connection.get().name())
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
    return query.matcher(link.toString()).matches() || query.matcher(target.toString()).matches();
  }

  @ActionParameter
  public void target(Path target) {
    this.target = target;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.put("link", link.toString());
    node.put("target", target.toString());
    node.put("instance", connection.get().name());
    return node;
  }
}
