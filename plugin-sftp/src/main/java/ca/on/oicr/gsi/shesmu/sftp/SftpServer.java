package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.InvalidatableRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Counter;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.xml.stream.XMLStreamException;
import net.schmizz.sshj.SSHClient;
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
      client.loadKnownHosts();

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

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final FileAttributes NXFILE = new FileAttributes.Builder().withSize(-1).build();
  private static final Counter symlinkErrors =
      Counter.build(
              "shesmu_sftp_symlink_errors",
              "The number of errors communicating with the SFTP server while trying to create a symlink.")
          .labelNames("target")
          .register();
  private Optional<Configuration> configuration = Optional.empty();
  private final ConnectionCache connection;
  private final Supplier<SftpServer> supplier;
  private final FileAttributeCache fileAttributes;

  public SftpServer(Path fileName, String instanceName, Supplier<SftpServer> supplier) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    fileAttributes = new FileAttributeCache(fileName);
    connection = new ConnectionCache(fileName);
    this.supplier = supplier;
  }

  @ShesmuMethod(
      description =
          "Returns true if the file or directory exists on the SFTP server described in {file}.")
  public synchronized boolean $_exists(
      @ShesmuParameter(description = "path to file") Path fileName,
      @ShesmuParameter(description = "value to return on error") boolean errorValue) {
    return fileAttributes.get(fileName).map(a -> a.getSize() != -1).orElse(errorValue);
  }

  @ShesmuMethod(
      description =
          "Gets the last modification timestamp of a file or directory living on the SFTP server described in {file}.")
  public synchronized Instant $_mtime(
      @ShesmuParameter(description = "path to file") Path fileName,
      @ShesmuParameter(description = "time to return on error") Instant errorValue) {
    return fileAttributes
        .get(fileName)
        .map(a -> Instant.ofEpochSecond(a.getMtime()))
        .orElse(errorValue);
  }

  @ShesmuMethod(
      description =
          "Get the size of a file, in bytes, living on the SFTP server described in {file}.")
  public synchronized long $_size(
      @ShesmuParameter(description = "path to file") Path fileName,
      @ShesmuParameter(description = "size to return on error") long errorValue) {
    return fileAttributes.get(fileName).map(FileAttributes::getSize).orElse(errorValue);
  }

  @ShesmuAction(description = "Create a symlink on the SFTP server described in {file}.")
  public SymlinkAction $_symlink() {
    return new SymlinkAction(supplier);
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
    renderer.line("Active", connection.get().isPresent() ? "Yes" : "No");
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

  @Override
  public synchronized Optional<Integer> update(Configuration configuration) {
    this.configuration = Optional.of(configuration);
    fileAttributes.invalidateAll();
    connection.invalidate();
    return connection.get().isPresent() ? Optional.empty() : Optional.of(1);
  }
}
