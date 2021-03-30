package ca.on.oicr.gsi.shesmu.runscanner;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.runscanner.dto.IlluminaNotificationDto;
import ca.on.oicr.gsi.runscanner.dto.NotificationDto;
import ca.on.oicr.gsi.runscanner.dto.ProgressiveRequestDto;
import ca.on.oicr.gsi.runscanner.dto.ProgressiveResponseDto;
import ca.on.oicr.gsi.runscanner.dto.type.IlluminaChemistry;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.prometheus.client.Gauge;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public final class RunScannerClient extends JsonPluginFile<Configuration> {

  /**
   * The state of how we should be syncing our cache to RunScanner
   *
   * <p>When RunScanner restarts, we have to wait for it to build its cache. We don't want to blow
   * away our cache since that would clear useful records that RunScanner hasn't scanned yet.
   * Therefore, we only clear old entries once we feel confident that Run Scanner's cache is full
   * and we've received it all.
   */
  enum PurgeState {
    /** We are totally synced and everything is good */
    FRESH,
    /**
     * The epoch between us and RunScanner has just detected to be different. We should eventually
     * clear the cache.
     */
    JUST_STALE,
    /**
     * We are not waiting for Run Scanner to finish scanning. We detect that Run Scanner is done
     * when it hasn't added any new runs between our requests.
     */
    WAITING_FOR_SYNC,
    /** RunScanner is synced and we should clear any old data. */
    STALE
  }

  private static final class RunInformation {

    private final Optional<Long> cycles;
    private final int epoch;
    private final Set<Set<Long>> geometry;
    private final int lanes;
    private final Optional<Long> readEnds;
    private final String serialNumber;

    private RunInformation(NotificationDto run, int epoch) {
      lanes = run.getLaneCount();
      geometry = computeGeometry(run);
      readEnds =
          run instanceof IlluminaNotificationDto
              ? Optional.of((long) ((IlluminaNotificationDto) run).getNumReads())
              : Optional.empty();
      cycles =
          run instanceof IlluminaNotificationDto
              ? Optional.of((long) ((IlluminaNotificationDto) run).getScoreCycle())
              : Optional.empty();
      serialNumber = run.getContainerSerialNumber();
      this.epoch = epoch;
    }

    public Optional<Long> cycle() {
      return cycles;
    }

    public Set<Set<Long>> geometry() {
      return geometry;
    }

    public long lanes() {
      return lanes;
    }

    public Optional<Long> readEnds() {
      return readEnds;
    }

    public String serialNumber() {
      return serialNumber;
    }
  }

  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Gauge fetchOk =
      Gauge.build(
              "shesmu_runscanner_response_ok", "Whether the last request to Run Scanner went well.")
          .labelNames("filename")
          .register();
  private static final LatencyHistogram refillTime =
      new LatencyHistogram(
          "shesmu_runscanner_refill_time", "The time to refill the run cache", "filename");

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

  private Instant lastUpdate = Instant.EPOCH;
  private final ProgressiveRequestDto request = new ProgressiveRequestDto();
  private final Map<String, RunInformation> runCache = new ConcurrentHashMap<>();
  private PurgeState runCachePurge = PurgeState.FRESH;
  private final Semaphore updateLock = new Semaphore(1);
  private Optional<String> url = Optional.empty();

  public RunScannerClient(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
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
      return getRun(runName).flatMap(RunInformation::cycle);
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description =
          "Get the serial number of the flowcell detected by the Run Scanner defined in {file}.")
  public Optional<String> flowcell(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return getRun(runName).map(RunInformation::serialNumber);
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
      return getRun(runName).map(RunInformation::geometry).orElse(Set.of());
    } catch (InitialCachePopulationException e) {
      return Set.of();
    }
  }

  private Optional<RunInformation> getRun(String runName) {
    final var now = Instant.now();
    final var url = this.url;
    if (url.isPresent() && updateLock.tryAcquire()) {
      try (final var ignored = refillTime.start(fileName().toString())) {
        if (Duration.between(lastUpdate, now).getSeconds() > 600) {
          if (runCachePurge == PurgeState.STALE) {
            runCache.entrySet().removeIf(e -> e.getValue().epoch != request.getEpoch());
            runCachePurge = PurgeState.FRESH;
          }
          if (runCachePurge == PurgeState.JUST_STALE) {
            runCachePurge = PurgeState.WAITING_FOR_SYNC;
          }
          var isMoreAvailable = true;
          var noUpdates = true;
          while (isMoreAvailable) {
            final var response =
                HTTP_CLIENT
                    .send(
                        HttpRequest.newBuilder(
                                URI.create(String.format("%s/runs/progressive", url.get())))
                            .POST(BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(request)))
                            .header("Content-type", "application/json")
                            .build(),
                        new JsonBodyHandler<>(MAPPER, ProgressiveResponseDto.class))
                    .body()
                    .get();
            isMoreAvailable = response.isMoreAvailable();
            if (request.getEpoch() != response.getEpoch()) {
              runCachePurge = PurgeState.JUST_STALE;
            }
            request.setEpoch(response.getEpoch());
            request.setToken(response.getToken());
            noUpdates &= response.getUpdates().isEmpty();
            for (final var run : response.getUpdates()) {
              runCache.put(run.getRunAlias(), new RunInformation(run, response.getEpoch()));
            }
          }
          if (noUpdates && runCachePurge == PurgeState.WAITING_FOR_SYNC) {
            runCachePurge = PurgeState.STALE;
          }
          lastUpdate = now;
          fetchOk.labels(fileName().toString()).set(1);
        }
      } catch (Exception e) {
        e.printStackTrace();
        fetchOk.labels(fileName().toString()).set(0);
      } finally {
        updateLock.release();
      }
    }
    return Optional.ofNullable(runCache.get(runName));
  }

  @ShesmuMethod(
      description = "Get the number of lanes detected by the Run Scanner defined in {file}.")
  public Optional<Long> lane_count(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return getRun(runName).map(RunInformation::lanes);
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description = "Get the number of reads detected by the Run Scanner defined in {file}.")
  public Optional<Long> read_ends(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return getRun(runName).flatMap(RunInformation::readEnds);
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
