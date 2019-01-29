package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.cache.InvalidatableRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.sftp.FileAttributes;
import net.schmizz.sshj.sftp.SFTPClient;

public class SftpServer extends JsonPluginFile<Configuration> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

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

  private static final FileAttributes NXFILE = new FileAttributes.Builder().withSize(-1).build();

  private Optional<Configuration> configuration = Optional.empty();
  private final ConnectionCache connection;
  private final FileAttributeCache fileAttributes;

  public SftpServer(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    fileAttributes = new FileAttributeCache(fileName);
    connection = new ConnectionCache(fileName);
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

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    renderer.line("Filename", fileName().toString());
    configuration.ifPresent(
        configuration -> {
          renderer.line("Host", configuration.getHost());
          renderer.line("Port", configuration.getPort());
          renderer.line("User", configuration.getUser());
        });
    renderer.line("Active", connection.get().isPresent() ? "Yes" : "No");
  }

  @Override
  public synchronized Optional<Integer> update(Configuration configuration) {
    this.configuration = Optional.of(configuration);
    fileAttributes.invalidateAll();
    connection.invalidate();
    return connection.get().isPresent() ? Optional.empty() : Optional.of(1);
  }
}
