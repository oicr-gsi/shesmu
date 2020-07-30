package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.*;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.input.JsonInputSource;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuJsonInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonArray;
import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.json.UnpackJson;
import ca.on.oicr.gsi.shesmu.plugin.refill.CustomRefillerParameter;
import ca.on.oicr.gsi.shesmu.plugin.refill.Refiller;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.io.*;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.Message;
import net.schmizz.sshj.common.SSHPacket;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.sftp.*;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;

public class SftpServer extends JsonPluginFile<Configuration> {

  private class FileAttributeCache
      extends KeyValueCache<Path, Optional<FileAttributes>, Optional<FileAttributes>> {
    public FileAttributeCache(Path fileName) {
      super("sftp " + fileName.toString(), 10, SimpleRecord::new);
    }

    @Override
    protected Optional<FileAttributes> fetch(Path fileName, Instant lastUpdated)
        throws IOException {
      final Configuration config = configuration.orElse(null);
      if (config == null) return Optional.empty();

      try (final SSHClient client = new SSHClient()) {
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.connect(config.getHost(), config.getPort());
        client.authPublickey(config.getUser());
        final SFTPClient sftp = client.newSFTPClient();
        if (sftp == null) return Optional.empty();
        try {
          final FileAttributes attributes = sftp.statExistence(fileName.toString());

          return Optional.of(attributes == null ? NXFILE : attributes);
        } catch (SFTPException e) {
          if (e.getStatusCode() == Response.StatusCode.PERMISSION_DENIED) {
            return Optional.empty();
          }
          throw e;
        }
      }
    }
  }

  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final FileAttributes NXFILE = new FileAttributes.Builder().withSize(-1).build();
  private static final Gauge refillBytes =
      Gauge.build(
              "shesmu_sftp_refill_bytes_sent", "The number of bytes sent to the refill command.")
          .labelNames("filename", "name")
          .register();
  private static final Gauge refillExitStatus =
      Gauge.build(
              "shesmu_sftp_refill_exit_status",
              "The last exit status of the remotely executed SSH process.")
          .labelNames("filename", "name")
          .register();
  private static final Gauge refillLastUpdate =
      Gauge.build(
              "shesmu_sftp_refill_last_updated",
              "The timestamp when the refill command was last run.")
          .labelNames("filename", "name")
          .register();
  private static final LatencyHistogram refillLatency =
      new LatencyHistogram(
          "shesmu_sftp_refill_processing_time",
          "The time to run the remote refill script in seconds.",
          "filename",
          "name");
  private static final Counter symlinkErrors =
      Counter.build(
              "shesmu_sftp_symlink_errors",
              "The number of errors communicating with the SFTP server while trying to create a symlink.")
          .labelNames("target")
          .register();
  private Optional<Configuration> configuration = Optional.empty();
  private final Definer<SftpServer> definer;
  private final FileAttributeCache fileAttributes;

  public SftpServer(Path fileName, String instanceName, Definer<SftpServer> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    fileAttributes = new FileAttributeCache(fileName);
    this.definer = definer;
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
  }

  @ShesmuAction(
      description = "Remove a file (not directory) on an SFTP server described in {file}.")
  public DeleteAction delete() {
    return new DeleteAction(definer);
  }

  @ShesmuMethod(
      description =
          "Returns true if the file or directory exists on the SFTP server described in {file}.")
  public Optional<Boolean> exists(@ShesmuParameter(description = "path to file") Path fileName) {
    return fileAttributes.get(fileName).map(a -> a.getSize() != -1);
  }

  @ShesmuJsonInputSource(format = "unix_file")
  public InputStream fileSystemData() throws Exception {

    return this.configuration
        .filter(c -> !c.getFileRoots().isEmpty())
        .<JsonInputSource>map(
            c -> {
              final String roots =
                  c.getFileRoots()
                      .stream()
                      .map(
                          p -> {
                            try {
                              return MAPPER.writeValueAsString(p);
                            } catch (JsonProcessingException e) {
                              throw new RuntimeException(e);
                            }
                          })
                      .collect(Collectors.joining(" "));
              return new SshJsonInputSource(
                  c.getHost(),
                  c.getPort(),
                  c.getUser(),
                  Optional.ofNullable(c.getFileRootsTtl()).filter(x -> x > 0),
                  c.getListCommand() == null
                      ? String.format(
                          "echo '['; find %s -not -type d -printf ',\\n{\"fetched\":%d,\"file\":\"%%p\",\"size\":%%s,\"atime\":%%A@,\"ctime\":%%C@,\"mtime\":%%T@,\"user\":\"%%u\",\"group\":\"%%g\",\"perms\":%%m,\"host\":\"'$(hostname -f)'\"}'| tail -n +2; echo ']'",
                          roots, Instant.now().toEpochMilli())
                      : (c.getListCommand() + " " + roots));
            })
        .orElse(JsonInputSource.EMPTY)
        .fetch();
  }

  Pair<ActionState, Boolean> makeSymlink(
      Path link,
      String target,
      boolean force,
      boolean fileInTheWay,
      Consumer<Instant> updateMtime,
      boolean automatic) {
    final Configuration config = configuration.orElse(null);
    if (config == null) return new Pair<>(ActionState.UNKNOWN, fileInTheWay);

    try (final SSHClient client = new SSHClient()) {
      client.addHostKeyVerifier(new PromiscuousVerifier());

      client.connect(config.getHost(), config.getPort());
      client.authPublickey(config.getUser());
      final SFTPClient sftp = client.newSFTPClient();
      if (sftp == null) {
        return new Pair<>(ActionState.UNKNOWN, fileInTheWay);
      }
      // Because this library thinks that a file not existing is an error state worthy of
      // exception,
      // it throws whenever stat or lstat is called. There's a wrapper for stat that catches the
      // exception and returns null, but there's no equivalent for lstat, so we reproduce that
      // catch
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
        if (!automatic) {
          return new Pair<>(ActionState.HALP, true);
        }
        // We've been told to blow it away
        if (force) {
          sftp.rm(linkStr);
          // Fun fact: OpenSSH has these parameters reversed compared to the spec.
          // https://github.com/hierynomus/sshj/issues/144
          if (client.getTransport().getServerVersion().contains("OpenSSH")) {
            sftp.symlink(target, linkStr);
          } else {
            sftp.symlink(linkStr, target);
          }
          updateMtime.accept(Instant.now());
          return new Pair<>(ActionState.SUCCEEDED, true);
        }
        // It exists and it's not already a symlink to what we want; bail
        return new Pair<>(ActionState.FAILED, true);
      } catch (SFTPException sftpe) {
        if (sftpe.getStatusCode() == Response.StatusCode.NO_SUCH_FILE) {
          if (!automatic) {
            return new Pair<>(ActionState.HALP, true);
          }
          // Create parent if necessary
          final String dirStr = link.getParent().toString();
          if (sftp.statExistence(dirStr) == null) {
            sftp.mkdirs(dirStr);
          }

          // File does not exist, create it.
          if (client.getTransport().getServerVersion().contains("OpenSSH")) {
            sftp.symlink(target, linkStr);
          } else {
            sftp.symlink(linkStr, target);
          }
          updateMtime.accept(Instant.now());
          return new Pair<>(ActionState.SUCCEEDED, false);
        } else {
          // The SFTP connection might be in an error state, so reset it to be sure.
          throw sftpe;
        }
      }

    } catch (IOException e) {
      e.printStackTrace();
      symlinkErrors.labels(name()).inc();
      return new Pair<>(ActionState.UNKNOWN, fileInTheWay);
    }
  }

  @ShesmuMethod(
      description =
          "Gets the last modification timestamp of a file or directory living on the SFTP server described in {file}.")
  public Optional<Instant> modification_time(
      @ShesmuParameter(description = "path to file") Path fileName) {
    return fileAttributes
        .get(fileName)
        .filter(a -> a.getSize() != -1)
        .map(a -> Instant.ofEpochSecond(a.getMtime()));
  }

  public boolean refill(String name, String command, ArrayNode data) {
    final Configuration config = configuration.orElse(null);
    if (config == null) return false;
    int exitStatus;
    try (final SSHClient client = new SSHClient()) {
      client.addHostKeyVerifier(new PromiscuousVerifier());

      client.connect(config.getHost(), config.getPort());
      client.authPublickey(config.getUser());
      refillLastUpdate.labels(fileName().toString(), name).setToCurrentTime();
      try (final AutoCloseable latency = refillLatency.start(fileName().toString(), name);
          final Session session = client.startSession()) {

        try (final Session.Command process = session.exec(command);
            final BufferedReader reader =
                new BufferedReader(new InputStreamReader(process.getInputStream()));
            final BufferedReader errorReader =
                new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
          if ("UPDATE".equals(reader.readLine())) {
            try (final OutputStream output =
                new PrometheusLoggingOutputStream(
                    process.getOutputStream(), refillBytes, fileName().toString(), name)) {
              MAPPER.writeValue(output, data);
              // Send EOF to the remote end since closing the stream doesn't do that:
              // https://github.com/hierynomus/sshj/issues/143
              client
                  .getTransport()
                  .write(new SSHPacket(Message.CHANNEL_EOF).putUInt32(process.getRecipient()));
            }
          }
          final Map<String, String> labels = new TreeMap<>();
          labels.put("command", command);
          labels.put("name", name);
          labels.put("type", "refiller");
          labels.put("stream", "stderr");
          errorReader.lines().forEach(l -> definer.log(l, labels));
          labels.put("stream", "stdout");
          reader.lines().forEach(l -> definer.log(l, labels));
          process.join();
          exitStatus = process.getExitStatus() == null ? 255 : process.getExitStatus();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      exitStatus = 255;
    }
    refillExitStatus.labels(fileName().toString(), name).set(exitStatus);
    return exitStatus == 0;
  }

  ActionState rm(String path, boolean automatic) {
    final Configuration config = configuration.orElse(null);
    if (config == null) return ActionState.UNKNOWN;
    try (final SSHClient client = new SSHClient()) {
      client.addHostKeyVerifier(new PromiscuousVerifier());

      client.connect(config.getHost(), config.getPort());
      client.authPublickey(config.getUser());
      final SFTPClient sftp = client.newSFTPClient();
      if (sftp == null) {
        return ActionState.UNKNOWN;
      }
      if (sftp.statExistence(path) != null) {
        if (automatic) {
          sftp.rm(path);
          return ActionState.SUCCEEDED;
        } else {
          return ActionState.HALP;
        }
      } else {
        return ActionState.SUCCEEDED;
      }
    } catch (IOException e) {
      e.printStackTrace();
      return ActionState.FAILED;
    }
  }

  @ShesmuMethod(
      description =
          "Get the size of a file, in bytes, living on the SFTP server described in {file}.")
  public Optional<Long> size(@ShesmuParameter(description = "path to file") Path fileName) {
    return fileAttributes.get(fileName).filter(a -> a.getSize() != -1).map(FileAttributes::getSize);
  }

  @ShesmuAction(description = "Create a symlink on the SFTP server described in {file}.")
  public SymlinkAction symlink() {
    return new SymlinkAction(definer);
  }

  @Override
  public synchronized Optional<Integer> update(Configuration configuration) {
    this.configuration = Optional.of(configuration);
    fileAttributes.invalidateAll();
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
                                  parameter.getKey(), parameter.getValue()) {
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
    definer.clearFunctions();
    for (final Map.Entry<String, FunctionConfig> entry : configuration.getFunctions().entrySet()) {
      final Imyhat returns = entry.getValue().getReturns();
      final Imyhat[] parameters = entry.getValue().getParameters().stream().toArray(Imyhat[]::new);
      final KeyValueCache<Tuple, Optional<Object>, Optional<Object>> cache =
          new KeyValueCache<Tuple, Optional<Object>, Optional<Object>>(
              String.format("sftp-function %s %s", fileName(), entry.getKey()),
              entry.getValue().getTtl(),
              SimpleRecord::new) {
            final String command = entry.getValue().getCommand();
            final String name = entry.getKey();

            @Override
            protected Optional<Object> fetch(Tuple key, Instant lastUpdated) throws Exception {
              try (final SSHClient client = new SSHClient()) {
                client.addHostKeyVerifier(new PromiscuousVerifier());

                client.connect(configuration.getHost(), configuration.getPort());
                client.authPublickey(configuration.getUser());
                try (final Session session = client.startSession()) {

                  try (final Session.Command process = session.exec(command);
                      final BufferedReader reader =
                          new BufferedReader(new InputStreamReader(process.getInputStream()));
                      final BufferedReader errorReader =
                          new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    try (final OutputStream output =
                        new PrometheusLoggingOutputStream(
                            process.getOutputStream(), refillBytes, fileName().toString(), name)) {
                      final ArrayNode array = MAPPER.createArrayNode();
                      for (int i = 0; i < parameters.length; i++) {
                        parameters[i].accept(new PackJsonArray(array), key.get(i));
                      }
                      MAPPER.writeValue(output, array);
                      // Send EOF to the remote end since closing the stream doesn't do that:
                      // https://github.com/hierynomus/sshj/issues/143
                      client
                          .getTransport()
                          .write(
                              new SSHPacket(Message.CHANNEL_EOF).putUInt32(process.getRecipient()));
                    }
                    final JsonNode jsonResult = MAPPER.readTree(reader);
                    final Map<String, String> labels = new TreeMap<>();
                    labels.put("command", command);
                    labels.put("name", name);
                    labels.put("type", "function");
                    errorReader.lines().forEach(l -> definer.log(l, labels));
                    process.join();
                    if (process.getExitStatus() == null || process.getExitStatus() != 0) {
                      return Optional.empty();
                    }
                    final Object result = returns.apply(new UnpackJson(jsonResult));
                    return returns instanceof Imyhat.OptionalImyhat
                        ? ((Optional<?>) result).map(x -> x)
                        : Optional.of(result);
                  }
                }
              }
            }
          };
      definer.defineFunction(
          entry.getKey(),
          String.format("Function run via SSH defined in %s.", fileName()),
          returns.asOptional(),
          args -> {
            try {
              return cache.get(new Tuple(args));
            } catch (InitialCachePopulationException e) {
              return Optional.empty();
            }
          },
          Stream.of(parameters)
              .map(t -> new FunctionParameter("Parameter for SSH", t))
              .toArray(FunctionParameter[]::new));
    }
    definer.clearSource();
    for (final JsonDataSource source : configuration.getJsonSources()) {
      definer.defineSource(
          source.getFormat(),
          source.getTtl(),
          new SshJsonInputSource(
              configuration.getHost(),
              configuration.getPort(),
              configuration.getUser(),
              Optional.empty(),
              source.getCommand()));
    }
    return Optional.empty();
  }
}
