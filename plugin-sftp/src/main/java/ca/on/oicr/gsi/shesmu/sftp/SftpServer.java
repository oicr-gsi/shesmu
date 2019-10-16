package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.*;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.refill.CustomRefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Message;
import net.schmizz.sshj.common.SSHPacket;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.*;

public class SftpServer extends JsonPluginFile<Configuration> {
  private class ConnectionCache extends ValueCache<Optional<Pair<SSHClient, SFTPClient>>> {

    public ConnectionCache(Path fileName) {
      super(
          "sftp " + fileName.toString(),
          10, //
          InvalidatableRecord.checking( //
              pair -> pair.first().isAuthenticated(), //
              pair -> {
                try {
                  pair.first().close();
                } catch (final Exception e) {
                  e.printStackTrace();
                }
              }));
    }

    @Override
    protected Optional<Pair<SSHClient, SFTPClient>> fetch(Instant lastUpdated) throws Exception {
      if (!configuration.isPresent()) return Optional.empty();
      final SSHClient client = new SSHClient();
      client.addHostKeyVerifier((s, i, publicKey) -> true);

      client.connect(configuration.get().getHost(), configuration.get().getPort());
      client.authPublickey(configuration.get().getUser());
      return Optional.of(new Pair<>(client, client.newSFTPClient()));
    }
  }

  private class FileAttributeCache extends KeyValueCache<Path, Optional<FileAttributes>> {
    public FileAttributeCache(Path fileName) {
      super("sftp " + fileName.toString(), 10, SimpleRecord::new);
    }

    @Override
    protected Optional<FileAttributes> fetch(Path fileName, Instant lastUpdated)
        throws IOException {
      final SFTPClient client = connection.get().map(Pair::second).orElse(null);
      if (client == null) return Optional.empty();
      final FileAttributes attributes = client.statExistence(fileName.toString());

      return Optional.of(attributes == null ? NXFILE : attributes);
    }
  }

  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final FileAttributes NXFILE = new FileAttributes.Builder().withSize(-1).build();
  private static final Gauge refillExitStatus =
      Gauge.build(
              "shesmu_sftp_refill_exit_status",
              "The last exit status of the remotely executed SSH process.")
          .labelNames("filename", "name")
          .register();
  private static final Counter symlinkErrors =
      Counter.build(
              "shesmu_sftp_symlink_errors",
              "The number of errors communicating with the SFTP server while trying to create a symlink.")
          .labelNames("target")
          .register();
  private Optional<Configuration> configuration = Optional.empty();
  private final ConnectionCache connection;
  private final Definer<SftpServer> definer;
  private final FileAttributeCache fileAttributes;

  public SftpServer(Path fileName, String instanceName, Definer<SftpServer> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    fileAttributes = new FileAttributeCache(fileName);
    connection = new ConnectionCache(fileName);
    this.definer = definer;
  }

  @ShesmuMethod(
      description =
          "Returns true if the file or directory exists on the SFTP server described in {file}.")
  public synchronized Optional<Boolean> $_exists(
      @ShesmuParameter(description = "path to file") Path fileName) {
    return fileAttributes.get(fileName).map(a -> a.getSize() != -1);
  }

  @ShesmuMethod(
      description =
          "Gets the last modification timestamp of a file or directory living on the SFTP server described in {file}.")
  public synchronized Optional<Instant> $_mtime(
      @ShesmuParameter(description = "path to file") Path fileName) {
    return fileAttributes.get(fileName).map(a -> Instant.ofEpochSecond(a.getMtime()));
  }

  @ShesmuAction(
      description = "Remove a file (not directory) on an SFTP server described in {file}.")
  public DeleteAction $_rm() {
    return new DeleteAction(definer);
  }

  @ShesmuMethod(
      description =
          "Get the size of a file, in bytes, living on the SFTP server described in {file}.")
  public synchronized Optional<Long> $_size(
      @ShesmuParameter(description = "path to file") Path fileName) {
    return fileAttributes.get(fileName).map(FileAttributes::getSize);
  }

  @ShesmuAction(description = "Create a symlink on the SFTP server described in {file}.")
  public SymlinkAction $_symlink() {
    return new SymlinkAction(definer);
  }

  @Override
  public synchronized void configuration(SectionRenderer renderer) throws XMLStreamException {
    renderer.line("Filename", fileName().toString());
    configuration.ifPresent(
        configuration -> {
          renderer.line("Host", configuration.getHost());
          renderer.line("Port", configuration.getPort());
          renderer.line("User", configuration.getUser());
        });
    try {
      renderer.line("Active", connection.get().isPresent() ? "Yes" : "No");
    } catch (InitialCachePopulationException e) {
      renderer.line("Active", "Error on startup");
    }
  }

  synchronized Pair<ActionState, Boolean> makeSymlink(
      Path link,
      String target,
      boolean force,
      boolean fileInTheWay,
      Consumer<Instant> updateMtime) {
    final Optional<SFTPClient> client = connection.get().map(Pair::second);
    if (!client.isPresent()) {
      return new Pair<>(ActionState.UNKNOWN, fileInTheWay);
    }
    try {
      final SFTPClient sftp = client.get();
      // Because this library thinks that a file not existing is an error state worthy of exception,
      // it throws whenever stat or lstat is called. There's a wrapper for stat that catches the
      // exception and returns null, but there's no equivalent for lstat, so we reproduce that catch
      // logic here.
      final String linkStr = link.toString();
      try {
        final FileAttributes attributes = sftp.lstat(linkStr);
        updateMtime.accept(Instant.ofEpochSecond(attributes.getMtime()));
        // File exists and it is a symlink
        if (attributes.getType() == FileMode.Type.SYMLINK
            && sftp.readlink(linkStr).equals(target)) {
          // It's what we want; done
          return new Pair<>(ActionState.SUCCEEDED, false);
        }
        // We've been told to blow it away
        if (force) {
          sftp.rm(linkStr);
          sftp.symlink(linkStr, target);
          updateMtime.accept(Instant.now());
          return new Pair<>(ActionState.SUCCEEDED, true);
        }
        // It exists and it's not already a symlink to what we want; bail
        return new Pair<>(ActionState.FAILED, true);
      } catch (SFTPException sftpe) {
        if (sftpe.getStatusCode() == Response.StatusCode.NO_SUCH_FILE) {
          // Create parent if necessary
          final String dirStr = link.getParent().toString();
          if (sftp.statExistence(dirStr) == null) {
            sftp.mkdirs(dirStr);
          }

          // File does not exist, create it.
          sftp.symlink(linkStr, target);
          updateMtime.accept(Instant.now());
          return new Pair<>(ActionState.SUCCEEDED, false);
        } else {
          // The SFTP connection might be in an error state, so reset it to be sure.
          connection.invalidate();
          throw sftpe;
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
      symlinkErrors.labels(name()).inc();
      return new Pair<>(ActionState.UNKNOWN, fileInTheWay);
    }
  }

  public synchronized boolean refill(String name, String command, ArrayNode data) {
    return connection
        .get()
        .map(Pair::first)
        .map(
            client -> {
              int exitStatus;
              try (final Session session = client.startSession()) {

                try (final Session.Command process = session.exec(command);
                    final BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                  if ("UPDATE".equals(reader.readLine())) {
                    try (final OutputStream output = process.getOutputStream()) {
                      MAPPER.writeValue(output, data);
                      // Send EOF to the remote end since closing the stream doesn't do that:
                      // https://github.com/hierynomus/sshj/issues/143
                      client
                          .getTransport()
                          .write(
                              new SSHPacket(Message.CHANNEL_EOF).putUInt32(process.getRecipient()));
                    }
                  }
                  process.join();
                  exitStatus = process.getExitStatus() == null ? 255 : process.getExitStatus();
                }
              } catch (Exception e) {
                e.printStackTrace();
                exitStatus = 255;
              }
              refillExitStatus.labels(fileName().toString(), name).set(exitStatus);
              return exitStatus == 0;
            })
        .orElse(false);
  }

  synchronized boolean rm(String path) {
    final Optional<SFTPClient> client = connection.get().map(Pair::second);
    if (!client.isPresent()) {
      return false;
    }
    try {
      if (client.get().statExistence(path) != null) {
        client.get().rm(path);
      }
      return true;
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
  }

  @Override
  public synchronized Optional<Integer> update(Configuration configuration) {
    this.configuration = Optional.of(configuration);
    fileAttributes.invalidateAll();
    connection.invalidate();
    definer.clearRefillers();
    for (final Map.Entry<String, RefillerConfig> entry : configuration.getRefillers().entrySet()) {
      definer.defineRefiller(
          entry.getKey(),
          String.format(
              "Remote SSH command %s on %s.",
              entry.getValue().getCommand(), configuration.getHost()),
          new Definer.RefillDefiner() {
            @Override
            public <I> Definer.RefillInfo<I, SshRefiller<I>> info(Class<I> rowType) {
              return new Definer.RefillInfo<I, SshRefiller<I>>() {
                @Override
                public SshRefiller<I> create() {
                  return new SshRefiller<>(definer, entry.getKey(), entry.getValue().getCommand());
                }

                @Override
                public Stream<CustomRefillerParameter<SshRefiller<I>, I>> parameters() {
                  return entry
                      .getValue()
                      .getParameters()
                      .entrySet()
                      .stream()
                      .map(
                          parameter ->
                              new CustomRefillerParameter<SshRefiller<I>, I>(
                                  parameter.getKey(), Imyhat.parse(parameter.getValue())) {
                                @Override
                                public void store(
                                    SshRefiller<I> refiller, Function<I, Object> function) {
                                  refiller.writers.add(
                                      (row, output) ->
                                          type()
                                              .accept(
                                                  new PackJsonObject(output, name()),
                                                  function.apply(row)));
                                }
                              });
                }

                @Override
                public Class<? extends Refiller> type() {
                  return SshRefiller.class;
                }
              };
            }
          });
    }
    return connection.get().isPresent() ? Optional.empty() : Optional.of(1);
  }
}
