package ca.on.oicr.gsi.shesmu.sftp;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.LogLevel;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.schmizz.sshj.common.Message;
import net.schmizz.sshj.common.SSHPacket;
import net.schmizz.sshj.connection.channel.direct.Signal;
import net.schmizz.sshj.sftp.FileMode;
import net.schmizz.sshj.sftp.FileMode.Type;
import net.schmizz.sshj.sftp.Response;
import net.schmizz.sshj.sftp.Response.StatusCode;
import net.schmizz.sshj.sftp.SFTPException;

public class SftpServer extends JsonPluginFile<Configuration> {

  private class FileAttributeCache
      extends KeyValueCache<Pair<Path, Boolean>, Optional<AlgebraicValue>> {
    public FileAttributeCache(Path fileName) {
      super("sftp " + fileName.toString(), 10, SimpleRecord::new);
    }

    @Override
    protected Optional<AlgebraicValue> fetch(Pair<Path, Boolean> fileName, Instant lastUpdated)
        throws IOException {

      try (final var connection = connections.get()) {
        final var attributes =
            fileName.second()
                ? connection.sftp().stat(fileName.first().toString())
                : connection.sftp().lstat(fileName.first().toString());
        final var type =
            attributes.getType() == Type.SYMLINK
                ? new AlgebraicValue(
                    attributes.getType().name(),
                    fileName
                        .first()
                        .resolveSibling(connection.sftp().readlink(fileName.first().toString())))
                : new AlgebraicValue(attributes.getType().name());

        return Optional.of(
            new AlgebraicValue(
                "FILE",
                Instant.ofEpochSecond(attributes.getAtime()),
                Instant.ofEpochSecond(attributes.getMtime()),
                (long) attributes.getMode().getMask(),
                attributes.getSize(),
                type));
      } catch (SFTPException e) {
        if (e.getStatusCode() == StatusCode.NO_SUCH_FILE) {
          return Optional.of(NO_EXIST);
        }
        if (e.getStatusCode() == Response.StatusCode.PERMISSION_DENIED) {
          return Optional.of(NO_PERMISSION);
        }
        throw e;
      }
    }
  }

  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AlgebraicValue NO_EXIST = new AlgebraicValue("NO_EXIST");
  private static final AlgebraicValue NO_PERMISSION = new AlgebraicValue("NO_PERMISSION");
  private static final AlgebraicValue UNKNOWN = new AlgebraicValue("UNKNOWN");
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
  private static final Gauge refillResponseOk =
      Gauge.build(
              "shesmu_sftp_refill_response_ok",
              "Whether the refill command returned an approved value.")
          .labelNames("filename", "name")
          .register();
  private static final Counter symlinkErrors =
      Counter.build(
              "shesmu_sftp_symlink_errors",
              "The number of errors communicating with the SFTP server while trying to create a symlink.")
          .labelNames("target")
          .register();
  private static final Gauge refillItemsSent =
      Gauge.build("shesmu_sftp_refill_items_sent", "The number of items sent to the refiller")
          .labelNames("filename", "name")
          .register();
  private Optional<Configuration> configuration = Optional.empty();
  private final SshConnectionPool connections = new SshConnectionPool();
  private final Definer<SftpServer> definer;
  private final FileAttributeCache fileAttributes;

  public SftpServer(Path fileName, String instanceName, Definer<SftpServer> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    fileAttributes = new FileAttributeCache(fileName);
    this.definer = definer;
  }

  @Override
  public void configuration(SectionRenderer renderer) {
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

  public Thread drainOutput(
      String name, String command, BufferedReader errorReader, String stream) {
    final Map<String, String> labels = new TreeMap<>();
    labels.put("command", command);
    labels.put("name", name);
    labels.put("type", "refiller");
    labels.put("stream", stream);
    final var errorDrainThread =
        new Thread(() -> errorReader.lines().forEach(l -> definer.log(l, LogLevel.ERROR, labels)));
    errorDrainThread.start();
    return errorDrainThread;
  }

  @ShesmuJsonInputSource(format = "unix_file")
  public InputStream fileSystemData() throws Exception {

    return this.configuration
        .filter(c -> !c.getFileRoots().isEmpty())
        .<JsonInputSource>map(
            c -> {
              final var roots =
                  c.getFileRoots().stream()
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
                  connections,
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
    final var config = configuration.orElse(null);
    if (config == null) return new Pair<>(ActionState.UNKNOWN, fileInTheWay);

    try (final var connection = connections.get()) {
      // Because this library thinks that a file not existing is an error state worthy of
      // exception,
      // it throws whenever stat or lstat is called. There's a wrapper for stat that catches the
      // exception and returns null, but there's no equivalent for lstat, so we reproduce that
      // catch
      // logic here.
      final var linkStr = link.toString();
      try {
        final var attributes = connection.sftp().lstat(linkStr);
        updateMtime.accept(Instant.ofEpochSecond(attributes.getMtime()));
        // File exists and it is a symlink
        if (attributes.getType() == FileMode.Type.SYMLINK
            && connection.sftp().readlink(linkStr).equals(target)) {
          // It's what we want; done
          return new Pair<>(ActionState.SUCCEEDED, false);
        }
        if (!automatic) {
          return new Pair<>(ActionState.HALP, true);
        }
        // We've been told to blow it away
        if (force) {
          connection.sftp().rm(linkStr);
          // Fun fact: OpenSSH has these parameters reversed compared to the spec.
          // https://github.com/hierynomus/sshj/issues/144
          if (connection.client().getTransport().getServerVersion().contains("OpenSSH")) {
            connection.sftp().symlink(target, linkStr);
          } else {
            connection.sftp().symlink(linkStr, target);
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
          final var dirStr = link.getParent().toString();
          if (connection.sftp().statExistence(dirStr) == null) {
            connection.sftp().mkdirs(dirStr);
          }

          // File does not exist, create it.
          if (connection.client().getTransport().getServerVersion().contains("OpenSSH")) {
            connection.sftp().symlink(target, linkStr);
          } else {
            connection.sftp().symlink(linkStr, target);
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

  public boolean refill(String name, String command, ArrayNode data) {
    final var config = configuration.orElse(null);
    if (config == null) return false;
    int exitStatus;
    try (final var connection = connections.get()) {

      refillLastUpdate.labels(fileName().toString(), name).setToCurrentTime();
      refillItemsSent.labels(fileName().toString(), name).set(data.size());
      try (final var latency = refillLatency.start(fileName().toString(), name);
          final var session = connection.client().startSession()) {

        try (final var process = session.exec(command);
            final var reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final var errorReader =
                new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
          final Thread inputThread;
          final var response = reader.readLine();
          if (response == null) {
            inputThread = null;
            process.signal(Signal.KILL);
            refillResponseOk.labels(fileName().toString(), name).set(0);
            final Map<String, String> labels = new TreeMap<>();
            labels.put("command", command);
            labels.put("name", name);
            labels.put("type", "refiller");
            definer.log("Expected OK or UPDATE, but got no output", LogLevel.ERROR, labels);
          } else {
            switch (response) {
              case "UPDATE":
                refillResponseOk.labels(fileName().toString(), name).set(1);
                inputThread =
                    new Thread(
                        () -> {
                          try (final OutputStream output =
                              new PrometheusLoggingOutputStream(
                                  process.getOutputStream(),
                                  refillBytes,
                                  fileName().toString(),
                                  name)) {
                            MAPPER.writeValue(output, data);
                            // Send EOF to the remote end since closing the stream doesn't do that:
                            // https://github.com/hierynomus/sshj/issues/143
                            connection
                                .client()
                                .getTransport()
                                .write(
                                    new SSHPacket(Message.CHANNEL_EOF)
                                        .putUInt32(process.getRecipient()));
                          } catch (IOException e) {
                            e.printStackTrace();
                          }
                        });
                inputThread.start();
                break;
              case "OK":
                refillResponseOk.labels(fileName().toString(), name).set(1);
                inputThread = null;
                break;
              default:
                inputThread = null;
                process.signal(Signal.KILL);
                refillResponseOk.labels(fileName().toString(), name).set(0);
                final Map<String, String> labels = new TreeMap<>();
                labels.put("command", command);
                labels.put("name", name);
                labels.put("type", "refiller");
                definer.log(
                    "Expected OK or UPDATE, but got invalid response: " + response,
                    LogLevel.ERROR,
                    labels);
                break;
            }
          }
          final var errorDrainThread = drainOutput(name, command, errorReader, "stderr");
          final var outputDrainThread = drainOutput(name, command, reader, "stdout");
          process.join();
          outputDrainThread.join();
          errorDrainThread.join();
          if (inputThread != null) {
            inputThread.join();
          }
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
    final var config = configuration.orElse(null);
    if (config == null) return ActionState.UNKNOWN;
    try (final var connection = connections.get()) {
      if (connection.sftp().statExistence(path) != null) {
        if (automatic) {
          connection.sftp().rm(path);
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
      type =
          "u4FILE$o5atime$dmtime$dperm$isize$itype$u8BLOCK_SPECIAL$t0CHAR_SPECIAL$t0DIRECTORY$t0FIFO_SPECIAL$t0REGULAR$t0SOCKET_SPECIAL$t0SYMLINK$t1pUNKNOWN$t0NO_EXIST$t0NO_PERMISSION$t0UNKNOWN$t0",
      description =
          "Gets the `stat` information (size, last modification timestamp, etc) of a file or"
              + " directory living on the SFTP server described in {file}.")
  public AlgebraicValue stat(
      @ShesmuParameter(description = "path to file") Path fileName,
      @ShesmuParameter(description = "resolve symlinks (aka stat); otherwise lstat")
          boolean resolveLinks) {
    return fileAttributes.get(new Pair<>(fileName, resolveLinks)).orElse(UNKNOWN);
  }

  @ShesmuAction(description = "Create a symlink on the SFTP server described in {file}.")
  public SymlinkAction symlink() {
    return new SymlinkAction(definer);
  }

  @Override
  public synchronized Optional<Integer> update(Configuration configuration) {
    this.configuration = Optional.of(configuration);
    connections.configure(
        configuration.getHost(), configuration.getPort(), configuration.getUser());
    fileAttributes.invalidateAll();
    definer.clearRefillers();
    for (final var entry : configuration.getRefillers().entrySet()) {
      definer.defineRefiller(
          entry.getKey(),
          String.format(
              "Remote SSH command %s on %s.",
              entry.getValue().getCommand(), configuration.getHost()),
          new Definer.RefillDefiner() {
            @Override
            public <I> Definer.RefillInfo<I, SshRefiller<I>> info(Class<I> rowType) {
              return new Definer.RefillInfo<>() {
                @Override
                public SshRefiller<I> create() {
                  return new SshRefiller<>(definer, entry.getKey(), entry.getValue().getCommand());
                }

                @Override
                public Stream<CustomRefillerParameter<SshRefiller<I>, I>> parameters() {
                  return entry.getValue().getParameters().entrySet().stream()
                      .map(
                          parameter ->
                              new CustomRefillerParameter<>(
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
    for (final var entry : configuration.getFunctions().entrySet()) {
      final var returns = entry.getValue().getReturns();
      final var parameters = entry.getValue().getParameters().toArray(Imyhat[]::new);
      final var cache =
          new KeyValueCache<Tuple, Optional<Object>>(
              String.format("sftp-function %s %s", fileName(), entry.getKey()),
              entry.getValue().getTtl(),
              SimpleRecord::new) {
            final String command = entry.getValue().getCommand();
            final String name = entry.getKey();

            @Override
            protected Optional<Object> fetch(Tuple key, Instant lastUpdated) throws Exception {
              try (final var connection = connections.get()) {
                try (final var session = connection.client().startSession()) {

                  try (final var process = session.exec(command);
                      final var reader =
                          new BufferedReader(new InputStreamReader(process.getInputStream()));
                      final var errorReader =
                          new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                    try (final OutputStream output =
                        new PrometheusLoggingOutputStream(
                            process.getOutputStream(), refillBytes, fileName().toString(), name)) {
                      final var array = MAPPER.createArrayNode();
                      for (var i = 0; i < parameters.length; i++) {
                        parameters[i].accept(new PackJsonArray(array), key.get(i));
                      }
                      MAPPER.writeValue(output, array);
                      // Send EOF to the remote end since closing the stream doesn't do that:
                      // https://github.com/hierynomus/sshj/issues/143
                      connection
                          .client()
                          .getTransport()
                          .write(
                              new SSHPacket(Message.CHANNEL_EOF).putUInt32(process.getRecipient()));
                    }
                    final var jsonResult = MAPPER.readTree(reader);
                    final Map<String, String> labels = new TreeMap<>();
                    labels.put("command", command);
                    labels.put("name", name);
                    labels.put("type", "function");
                    errorReader.lines().forEach(l -> definer.log(l, LogLevel.ERROR, labels));
                    process.join();
                    if (process.getExitStatus() == null || process.getExitStatus() != 0) {
                      return Optional.empty();
                    }
                    final var result = returns.apply(new UnpackJson(jsonResult));
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
    for (final var source : configuration.getJsonSources()) {
      definer.defineSource(
          source.getFormat(),
          source.getTtl(),
          new SshJsonInputSource(connections, Optional.empty(), source.getCommand()));
    }
    return Optional.empty();
  }
}
