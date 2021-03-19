package ca.on.oicr.gsi.shesmu.runscanner;

import ca.on.oicr.gsi.runscanner.dto.IlluminaNotificationDto;
import ca.on.oicr.gsi.runscanner.dto.NotificationDto;
import ca.on.oicr.gsi.runscanner.dto.type.IlluminaChemistry;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.RecordFactory;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public final class RunScannerClient extends JsonPluginFile<Configuration> {
  private abstract class BaseRunCache<T> extends KeyValueCache<String, Optional<T>, Optional<T>> {

    public BaseRunCache(String name, int ttl, RecordFactory<Optional<T>, Optional<T>> recordCtor) {
      super(name, ttl, recordCtor);
    }

    protected abstract T extract(NotificationDto dto);

    @Override
    protected final Optional<T> fetch(String runName, Instant lastUpdated) throws Exception {
      if (url.isEmpty()) {
        return Optional.empty();
      }
      var response =
          HTTP_CLIENT.send(
              HttpRequest.newBuilder(
                      URI.create(
                          String.format(
                              "%s/run/%s",
                              url.get(), URLEncoder.encode(runName, StandardCharsets.UTF_8))))
                  .build(),
              new JsonBodyHandler<>(MAPPER, NotificationDto.class));
      if (response.statusCode() != 200) {
        return Optional.empty();
      }
      return Optional.ofNullable(extract(response.body().get()));
    }
  }

  private class RunCache extends BaseRunCache<NotificationDto> {

    public RunCache(Path fileName) {
      super("runscanner " + fileName.toString(), 30, SimpleRecord::new);
    }

    @Override
    protected NotificationDto extract(NotificationDto dto) {
      dto.setMetrics(null); // Discard metrics to save memory
      return dto;
    }
  }
  // Although this uses the same data as the run cache, it has a different TTL, so separate cache
  private class RunCycleCache extends BaseRunCache<Long> {

    public RunCycleCache(Path fileName) {
      super("runscanner-cycles " + fileName.toString(), 5, SimpleRecord::new);
    }

    @Override
    protected Long extract(NotificationDto dto) {
      if (dto instanceof IlluminaNotificationDto) {
        return (long) ((IlluminaNotificationDto) dto).getScoreCycle();
      }
      return null;
    }
  }

  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  /**
   * Determine the correct flow cell geometry for a run
   *
   * @param notificationDto the run description
   */
  private static Set<Set<Long>> computeGeometry(NotificationDto notificationDto) {
    if (!(notificationDto instanceof IlluminaNotificationDto)) {
      return Set.of();
    }
    final var run = (IlluminaNotificationDto) notificationDto;
    // If chemistry is NextSeq or HiSeq onboard clustering or NovaSeq Standard
    final var isJoined =
        run.getChemistry() == IlluminaChemistry.NS_HIGH
            || run.getChemistry() == IlluminaChemistry.NS_MID
            || run.getWorkflowType() != null
                && RunScannerPluginType.isWorkflowTypeJoined(run.getWorkflowType());
    return RunScannerPluginType.getFlowcellLayout(run.getLaneCount(), isJoined);
  }

  private final RunCache runCache;
  private final RunCycleCache runCycleCache;
  private Optional<String> url = Optional.empty();

  public RunScannerClient(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    runCache = new RunCache(fileName);
    runCycleCache = new RunCycleCache(fileName);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    renderer.line("Filename", fileName().toString());
    url.ifPresent(u -> renderer.link("URL", u, u));
  }

  @ShesmuMethod(
      description =
          "Get the current scored cycle detected by the Run Scanner defined in {file} for an Illumina run.")
  public Optional<Long> cycle(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCycleCache.get(runName);
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description =
          "Get the serial number of the flowcell detected by the Run Scanner defined in {file}.")
  public Optional<String> flowcell(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache.get(runName).map(NotificationDto::getContainerSerialNumber);
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description =
          "Get the lane splitting/merging layout of the flowcell detected by the Run Scanner defined in {file}.")
  public Set<Set<Long>> flowcell_geometry(
      @ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache.get(runName).map(RunScannerClient::computeGeometry).orElse(Set.of());
    } catch (InitialCachePopulationException e) {
      return Set.of();
    }
  }

  @ShesmuMethod(
      description = "Get the number of lanes detected by the Run Scanner defined in {file}.")
  public Optional<Long> lane_count(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache.get(runName).map(r -> (long) r.getLaneCount());
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description = "Get the number of reads detected by the Run Scanner defined in {file}.")
  public Optional<Long> read_ends(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache
          .get(runName)
          .filter(IlluminaNotificationDto.class::isInstance)
          .map(r -> (long) ((IlluminaNotificationDto) r).getNumReads());
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @Override
  protected Optional<Integer> update(Configuration value) {
    url = Optional.ofNullable(value.getUrl());
    return Optional.empty();
  }
}
