package ca.on.oicr.gsi.shesmu.runscanner;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.runscanner.dto.IlluminaNotificationDto;
import ca.on.oicr.gsi.runscanner.dto.NotificationDto;
import ca.on.oicr.gsi.runscanner.dto.type.IlluminaChemistry;
import ca.on.oicr.gsi.shesmu.plugin.cache.*;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public final class RunScannerClient extends JsonPluginFile<Configuration> {
  private abstract class BaseRunCache<T> extends KeyValueCache<String, Optional<T>, Optional<T>> {

    public BaseRunCache(String name, int ttl, RecordFactory<Optional<T>, Optional<T>> recordCtor) {
      super(name, ttl, recordCtor);
    }

    protected abstract T extract(NotificationDto dto) throws Exception;

    @Override
    protected final Optional<T> fetch(String runName, Instant lastUpdated) throws Exception {
      if (!url.isPresent()) {
        return Optional.empty();
      }
      final HttpGet request =
          new HttpGet(String.format("%s/run/%s", url.get(), new URLCodec().encode(runName)));
      try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
        if (response.getStatusLine().getStatusCode() != 200) {
          return Optional.empty();
        }
        NotificationDto run =
            MAPPER.readValue(response.getEntity().getContent(), NotificationDto.class);
        return Optional.ofNullable(extract(run));
      }
    }
  }

  private class RunCache extends BaseRunCache<NotificationDto> {

    public RunCache(Path fileName) {
      super("runscanner " + fileName.toString(), 30, SimpleRecord::new);
    }

    @Override
    protected NotificationDto extract(NotificationDto dto) throws Exception {
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
    protected Long extract(NotificationDto dto) throws Exception {
      if (dto instanceof IlluminaNotificationDto) {
        return (long) ((IlluminaNotificationDto) dto).getScoreCycle();
      }
      return null;
    }
  }

  /**
   * Determine the correct flow cell geometry for a run
   *
   * @param notificationDto the run description
   */
  private static Set<Set<Long>> computeGeometry(NotificationDto notificationDto) {
    if (!(notificationDto instanceof IlluminaNotificationDto)) {
      return Collections.emptySet();
    }
    final IlluminaNotificationDto run = (IlluminaNotificationDto) notificationDto;
    // If chemistry is NextSeq or HiSeq onboard clustering or NovaSeq Standard
    final boolean isJoined =
        run.getChemistry() == IlluminaChemistry.NS_HIGH
            || run.getChemistry() == IlluminaChemistry.NS_MID
            || run.getWorkflowType() != null
                && (run.getWorkflowType().equals("NovaSeqStandard")
                    || run.getWorkflowType().equals("OnBoardClustering"));
    return LANE_GEOMETRY_CACHE.computeIfAbsent(
        new Pair<>(run.getLaneCount(), isJoined), RunScannerClient::createFlowCellLayout);
  }

  /**
   * Create a set of sets, where all lanes that are merged are in a set together
   *
   * @param format the number of lanes and whether the lanes are joined or not
   */
  private static Set<Set<Long>> createFlowCellLayout(Pair<Integer, Boolean> format) {
    if (format.second()) {
      return Collections.singleton(
          LongStream.rangeClosed(1, format.first())
              .boxed()
              .collect(Collectors.toCollection(TreeSet::new)));
    } else {
      return LongStream.rangeClosed(1, format.first())
          .mapToObj(Collections::singleton)
          .collect(Collectors.toSet());
    }
  }

  static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
  private static final Map<Pair<Integer, Boolean>, Set<Set<Long>>> LANE_GEOMETRY_CACHE =
      new ConcurrentHashMap<>();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  private final RunCache runCache;
  private final RunCycleCache runCycleCache;
  private Optional<String> url = Optional.empty();

  public RunScannerClient(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    runCache = new RunCache(fileName);
    runCycleCache = new RunCycleCache(fileName);
  }

  @ShesmuMethod(
      description =
          "Get the current scored cycle detected by the Run Scanner defined in {file} for an Illumina run.")
  public Optional<Long> $_cycle(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCycleCache.get(runName);
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description =
          "Get the serial number of the flowcell detected by the Run Scanner defined in {file}.")
  public Optional<String> $_flowcell(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache.get(runName).map(NotificationDto::getContainerSerialNumber);
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description =
          "Get the lane splitting/merging layout of the flowcell detected by the Run Scanner defined in {file}.")
  public Set<Set<Long>> $_flowcell_geometry(
      @ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache
          .get(runName)
          .map(RunScannerClient::computeGeometry)
          .orElse(Collections.emptySet());
    } catch (InitialCachePopulationException e) {
      return Collections.emptySet();
    }
  }

  @ShesmuMethod(
      description = "Get the number of lanes detected by the Run Scanner defined in {file}.")
  public Optional<Long> $_lane_count(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache.get(runName).map(r -> (long) r.getLaneCount());
    } catch (InitialCachePopulationException e) {
      return Optional.empty();
    }
  }

  @ShesmuMethod(
      description = "Get the number of reads detected by the Run Scanner defined in {file}.")
  public Optional<Long> $_read_ends(@ShesmuParameter(description = "name of run") String runName) {
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
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    renderer.line("Filename", fileName().toString());
    url.ifPresent(u -> renderer.link("URL", u, u));
  }

  @Override
  protected Optional<Integer> update(Configuration value) {
    url = Optional.ofNullable(value.getUrl());
    return Optional.empty();
  }
}
