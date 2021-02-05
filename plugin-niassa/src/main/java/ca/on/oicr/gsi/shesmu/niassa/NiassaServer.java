package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.shesmu.pipedev.CerberusAnalysisProvenanceValue;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.SupplementaryInformation;
import ca.on.oicr.gsi.shesmu.plugin.SupplementaryInformation.DisplayElement;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.*;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import io.seqware.common.model.WorkflowRunStatus;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import net.sourceforge.seqware.common.metadata.Metadata;
import net.sourceforge.seqware.common.metadata.MetadataWS;
import net.sourceforge.seqware.common.model.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

class NiassaServer extends JsonPluginFile<Configuration> {
  private interface MaxCheck {
    MaxStatus check(String workflowName);

    Stream<Pair<DisplayElement, DisplayElement>> display(String workflowName);
  }

  private class AnalysisCache
      extends KeyValueCache<Long, Stream<AnalysisState>, Stream<AnalysisState>> {
    public AnalysisCache(Path fileName) {
      super(
          "niassa-analysis " + fileName.toString(),
          120,
          TimeoutRecord.limit(45, ConcurrencyLimitedRecord.limit(4, ReplacingRecord::new)));
    }

    @Override
    protected Stream<AnalysisState> fetch(Long key, Instant lastUpdated) throws IOException {
      final MetadataWS metadata = metadataConstructor.get();
      if (metadata.getWorkflow(key.intValue()) == null) {
        definer.log(
            String.format("No such workflow %d fetching matches!", key), Collections.emptyMap());
        metadata.clean_up();
        return Stream.empty();
      }
      slowFetch.labels(key.toString()).set(0);
      final Runnable incrementSlowFetch = slowFetch.labels(key.toString())::inc;
      final Map<Integer, LimsKey> limsKeyCache = new HashMap<>();
      final Map<FileProvenanceFilter, Set<String>> filters =
          new EnumMap<>(FileProvenanceFilter.class);
      filters.put(FileProvenanceFilter.workflow, Collections.singleton(Long.toString(key)));
      final AtomicLong badStatusCount = new AtomicLong();
      final Set<Pair<String, String>> seenLimsKeys = new HashSet<>();
      final Gauge.Child monitor = badAnalysisData.labels(url, Long.toString(key));
      try {
        final List<AnalysisState> data =
            metadata.streamAnalysisProvenance(filters)
                .filter(
                    ap -> {
                      if (ap.getWorkflowRunStatus() == null) {
                        badStatusCount.incrementAndGet();
                        return false;
                      }
                      return true;
                    })
                .filter(ap -> ap.getWorkflowId() != null)
                .collect(Collectors.groupingBy(AnalysisProvenance::getWorkflowRunId)).entrySet()
                .stream()
                .map(
                    e ->
                        new AnalysisState(
                            e.getKey(),
                            () -> metadata.getWorkflowRunWithIuses(e.getKey()),
                            iusAccession ->
                                limsKeyCache.computeIfAbsent(
                                    iusAccession, metadata::getLimsKeyFrom),
                            e.getValue(),
                            incrementSlowFetch))
                .peek(as -> as.addSeenLimsKeys(seenLimsKeys))
                .collect(Collectors.toList());
        monitor.set(0);
        return data.stream();
      } catch (Exception e) {
        seenLimsKeys.clear();
        monitor.set(1);
        throw e;
      } finally {
        badStatus.labels(url, Long.toString(key)).set(badStatusCount.get());
        metadata.clean_up();
        final Set<Pair<String, String>> stale =
            staleKeys.computeIfAbsent(key, k -> new HashSet<>());
        synchronized (stale) {
          for (final Pair<String, String> limsKey : seenLimsKeys) {
            if (stale.remove(limsKey)) {
              definer.log(
                  String.format(
                      "Purging stale lock on %s/%s for workflow %d",
                      limsKey.first(), limsKey.second(), key),
                  Collections.emptyMap());
            }
          }
        }
      }
    }
  }

  private class AnalysisDataCache
      extends ValueCache<
          Stream<CerberusAnalysisProvenanceValue>, Stream<CerberusAnalysisProvenanceValue>> {
    public AnalysisDataCache(Path fileName) {
      super(
          "niassa-data-analysis " + fileName.toString(),
          20,
          TimeoutRecord.limit(45, ReplacingRecord::new));
    }

    @Override
    protected Stream<CerberusAnalysisProvenanceValue> fetch(Instant lastUpdated)
        throws IOException {
      final MetadataWS metadata = metadataConstructor.get();
      return metadata
          .streamAnalysisProvenance(Collections.emptyMap())
          .onClose(metadata::clean_up)
          .map(CerberusAnalysisProvenanceValue::new);
    }
  }

  private class DirectoryAndIniCache
      extends KeyValueCache<
          Long, Optional<WorkflowRunEssentials>, Optional<WorkflowRunEssentials>> {
    public DirectoryAndIniCache(Path fileName) {
      super(
          "niassa-dir+ini " + fileName.toString(),
          60 * 24 * 365,
          TimeoutRecord.limit(5, SimpleRecord::new));
    }

    @Override
    protected Optional<WorkflowRunEssentials> fetch(Long key, Instant lastUpdated)
        throws Exception {
      final MetadataWS metadata = metadataConstructor.get();
      final WorkflowRun run = metadata.getWorkflowRun(key.intValue());
      metadata.clean_up();
      final Properties ini = new Properties();
      ini.load(new StringReader(run.getIniFile()));

      final Optional<String> cromwellId =
          run.getWorkflowRunAttributes().stream()
              .filter(attr -> attr.getTag().equals("cromwell-workflow-id"))
              .map(WorkflowRunAttribute::getValue)
              .findFirst();
      final Pair<String, Map<String, List<CromwellCall>>> cromwellCalls =
          cromwellId
              .flatMap(
                  id ->
                      cromwellUrl.map(
                          root -> String.format("%s/api/workflows/v1/%s/metadata", root, id)))
              .flatMap(
                  url -> {
                    final HttpGet request = new HttpGet(url);
                    request.addHeader("Accept", ContentType.APPLICATION_JSON.getMimeType());
                    try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
                      if (response.getStatusLine().getStatusCode() / 100 != 2) {
                        return Optional.empty();
                      }
                      final CromwellMetadataResponse results =
                          MAPPER.readValue(
                              response.getEntity().getContent(), CromwellMetadataResponse.class);
                      return Optional.of(new Pair<>(results.getWorkflowRoot(), results.getCalls()));
                    } catch (Exception e) {
                      e.printStackTrace();
                      return Optional.empty();
                    }
                  })
              .orElse(new Pair<>(null, Collections.emptyMap()));

      return Optional.of(
          new WorkflowRunEssentials(
              run.getCurrentWorkingDir(),
              cromwellId.orElse(null),
              cromwellCalls.first(),
              ini,
              cromwellCalls.second()));
    }
  }

  public final class LaunchLock implements AutoCloseable {

    private final Set<Pair<String, String>> activeLimsKeys;
    private final boolean isLive;
    private final Map<Pair<String, String>, ca.on.oicr.gsi.provenance.model.LimsKey>
        lockedLimsKeys = new HashMap<>();
    private final LockLogger logger;
    private final long workflowAccession;

    /**
     * Create a lock on a chunk of LIMS key space
     *
     * @param limsKeys the LIMS keys that we as a workflow own
     * @param activeLimsKeys the total set of locked LIMS keys for this workflow SWID+annotation
     *     combination
     */
    public LaunchLock(
        LockLogger logger,
        long workflowAccession,
        List<SimpleLimsKey> limsKeys,
        Set<Pair<String, String>> activeLimsKeys) {
      this.logger = logger;
      this.workflowAccession = workflowAccession;
      this.activeLimsKeys = activeLimsKeys;
      // We're going to place all of our LIMS keys into the active LIMS key set and, if any are
      // already present, someone else holds the lock, so we will back out.
      synchronized (this.activeLimsKeys) {
        boolean isLive = true;
        for (final SimpleLimsKey limsKey : limsKeys) {
          // We don't want to use the full LIMS key because if there are different versions, they
          // should block each other, so just provider + ID
          final Pair<String, String> limsKeyId = new Pair<>(limsKey.getProvider(), limsKey.getId());

          if (lockedLimsKeys.containsKey(limsKeyId)) {
            // Duplicate LIMS keys in input. This is bad and will probably fail elsewhere, but we
            // can lock it successfully.
            logger.log(
                limsKey,
                "There's an olive that has produced multiple LIMS keys with the same provider/id and that's a bug");
            continue;
          }
          // Add this lims key to the set of active locks. Add returns true if it was newly added
          if (this.activeLimsKeys.add(limsKeyId)) {
            // Track that we added this LIMS key and therefore own it
            lockedLimsKeys.put(limsKeyId, limsKey);
          } else {
            // Backout any LIMS keys we already locked
            this.activeLimsKeys.removeAll(lockedLimsKeys.keySet());
            isLive = false;
            break;
          }
        }
        this.isLive = isLive;
      }
      if (isLive) {
        limsKeys.forEach(k -> logger.log(k, "Acquired lock"));
      }
    }

    @Override
    public void close() throws Exception {
      if (isLive) {
        synchronized (activeLimsKeys) {
          activeLimsKeys.removeAll(lockedLimsKeys.keySet());
        }
        lockedLimsKeys.values().forEach(k -> logger.log(k, "Released lock"));
      }
    }

    public boolean isLive() {
      // If we have successfully obtained a lock on these LIMS keys, we are theoretically ready to
      // go. However, if a workflow has previously looked at them, we want to wait for a cache
      // refresh. We need to acquire the lock before we trigger the cache refresh though, to ensure
      // mutual exclusion between two actions with the same LIMS key. So, we let the action acquire
      // the lock, pull from the cache, and then check if the keys that we own have been successfuly
      // updated by the cache. The cache will erase these keys if it successfully refreshed and when
      // we are done, we are going to mark these keys as stale in the cache, so the next action has
      // to wait for the cache to refresh before trying to use them.
      if (isLive) {
        final Set<Pair<String, String>> stale =
            staleKeys.computeIfAbsent(workflowAccession, k -> new HashSet<>());
        synchronized (stale) {
          if (lockedLimsKeys.keySet().stream().anyMatch(stale::contains)) {
            lockedLimsKeys
                .values()
                .forEach(k -> logger.log(k, "Lock is stale after refresh; backing out."));
            return false;
          }
          // At this point, we assume the workflow is going to launch, which means this LIMS keys
          // are now unavailable for another launching workflow.
          stale.addAll(lockedLimsKeys.keySet());
        }
        return true;
      }
      return false;
    }
  }

  private class MaxInFlightCache
      extends KeyValueCache<Long, Optional<MaxCheck>, Optional<MaxCheck>> {
    public MaxInFlightCache(Path fileName) {
      super(
          "niassa-max-in-flight " + fileName.toString(),
          5,
          TimeoutRecord.limit(5, SimpleRecord::new));
    }

    private int countRecords(String report) {
      int counter = 0;
      for (int i = 0; i < report.length(); i++) {
        if (report.charAt(i) == '\n') {
          counter++;
        }
      }
      return Math.max(0, counter - 1);
    }

    @Override
    protected Optional<MaxCheck> fetch(Long workflowSwid, Instant lastUpdated) throws IOException {
      final MetadataWS metadata = metadataConstructor.get();
      final Workflow workflow = metadata.getWorkflow(workflowSwid.intValue());
      if (workflow == null) {
        definer.log(
            String.format("No such workflow %d for max-in-flight check!", workflowSwid),
            Collections.emptyMap());
        metadata.clean_up();
        return Optional.empty();
      }
      final int count =
          Stream.of(
                  WorkflowRunStatus.pending,
                  WorkflowRunStatus.running,
                  WorkflowRunStatus.submitted,
                  WorkflowRunStatus.submitted_retry)
              .mapToInt(
                  status ->
                      countRecords(
                          metadata.getWorkflowRunReport(
                              workflowSwid.intValue(), status, null, null)))
              .sum();
      currentInFlight.labels(Long.toString(workflowSwid), workflow.getName()).set(count);
      metadata.clean_up();
      return Optional.of(
          new MaxCheck() {
            // Every time we successfully start a workflow, we're going to count it as running and
            // prevent other workflows from launching. This is to avoid multiple threads from
            // overriding the max-in-flight limit by launching concurrently. We don't need to
            // maintain this state; once the cache is invalidated, this object will die, but the new
            // running count will reflect anything we have added to the started count.
            private int started;

            public synchronized MaxStatus check(String workflowName) {
              foundRunning.labels(url(), workflowName, workflow.getName()).set(count + started);
              if (count + started < maxInFlight.getOrDefault(workflowName, 0)) {
                started++;
                currentInFlight.labels(Long.toString(workflowSwid), workflow.getName()).set(count);
                return MaxStatus.RUN;
              } else {
                return MaxStatus.TOO_MANY_RUNNING;
              }
            }

            @Override
            public Stream<Pair<DisplayElement, DisplayElement>> display(String workflowName) {
              return Stream.of(
                  new Pair<>(
                      SupplementaryInformation.text("Max in Flight"),
                      SupplementaryInformation.text(
                          Integer.toString(maxInFlight.getOrDefault(workflowName, 0)))),
                  new Pair<>(
                      SupplementaryInformation.text("Current in Flight"),
                      SupplementaryInformation.text(Integer.toString(count + started))));
            }
          });
    }
  }

  private class SkipLaneCache
      extends ValueCache<Stream<Pair<Tuple, Tuple>>, Stream<Pair<Tuple, Tuple>>> {
    public SkipLaneCache(Path fileName) {
      super(
          "niassa-skipped " + fileName.toString(),
          20,
          TimeoutRecord.limit(45, ReplacingRecord::new));
    }

    @Override
    protected Stream<Pair<Tuple, Tuple>> fetch(Instant lastUpdated) throws IOException {
      final MetadataWS metadata = metadataConstructor.get();
      return metadata
          .streamAnalysisProvenance(Collections.emptyMap())
          .onClose(metadata::clean_up)
          .filter(ap -> ap.getSkip() != null && ap.getSkip() && ap.getWorkflowId() == null)
          .flatMap(ap -> ap.getIusLimsKeys().stream())
          .map(
              iusLimsKey -> {
                final Tuple limsKey =
                    new Tuple(
                        iusLimsKey.getLimsKey().getId(),
                        iusLimsKey.getLimsKey().getVersion(),
                        iusLimsKey.getLimsKey().getProvider());
                final IUS originalIUS = metadata.getIUS(iusLimsKey.getIusSWID());
                final Tuple ius =
                    new Tuple(
                        originalIUS.getLane().getSequencerRun().getName(),
                        originalIUS.getLane().getLaneIndex().longValue(),
                        originalIUS.getTag());
                return new Pair<>(ius, limsKey);
              });
    }
  }

  private static final AnnotationType<FileAttribute> FILE =
      new AnnotationType<FileAttribute>() {
        @Override
        public FileAttribute create() {
          return new FileAttribute();
        }

        @Override
        public File fetch(Metadata metadata, int accession) {
          return metadata.getFile(accession);
        }

        @Override
        public String name() {
          return "File";
        }

        @Override
        public void save(Metadata metadata, int accession, FileAttribute attribute) {
          metadata.annotateFile(accession, attribute, null);
        }
      };
  static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
  private static final AnnotationType<IUSAttribute> IUS =
      new AnnotationType<IUSAttribute>() {
        @Override
        public IUSAttribute create() {
          return new IUSAttribute();
        }

        @Override
        public IUS fetch(Metadata metadata, int accession) {
          return metadata.getIUS(accession);
        }

        @Override
        public String name() {
          return "IUS";
        }

        @Override
        public void save(Metadata metadata, int accession, IUSAttribute attribute) {
          metadata.annotateIUS(accession, attribute, null);
        }
      };
  static final ObjectMapper MAPPER = new ObjectMapper();
  private static final AutoUpdatingDirectory<WorkflowFile> WORKFLOWS =
      new AutoUpdatingDirectory<>(".niassawf", WorkflowFile::new);
  private static final AnnotationType<WorkflowRunAttribute> WORKFLOW_RUN =
      new AnnotationType<WorkflowRunAttribute>() {
        @Override
        public WorkflowRunAttribute create() {
          return new WorkflowRunAttribute();
        }

        @Override
        public WorkflowRun fetch(Metadata metadata, int accession) {
          return metadata.getWorkflowRun(accession);
        }

        @Override
        public String name() {
          return "Workflow Run";
        }

        @Override
        public void save(Metadata metadata, int accession, WorkflowRunAttribute attribute) {
          metadata.annotateWorkflowRun(accession, attribute, null);
        }
      };
  private static final Gauge badAnalysisData =
      Gauge.build(
              "shesmu_niassa_bad_analysis_data",
              "Whether the last download of analysis provenance was successful or not.")
          .labelNames("target", "workflow")
          .register();
  private static final Gauge badStatus =
      Gauge.build("shesmu_niassa_bad_status", "The number of workflow runs that have null status.")
          .labelNames("target", "workflow")
          .register();
  private static final Gauge currentInFlight =
      Gauge.build(
              "shesmu_niassa_current_in_flight",
              "The number of jobs currently running for a particular workflow SWID.")
          .labelNames("workflow", "workflow_name")
          .register();
  private static final Gauge foundRunning =
      Gauge.build(
              "shesmu_niassa_found_running",
              "The number of workflow runs that Shesmu believes it has found. This is used for the max in flight checks.")
          .labelNames("target", "workflow", "workflow_name")
          .register();
  private static final Gauge maxInFlightGauge =
      Gauge.build(
              "shesmu_niassa_max_in_flight",
              "The maximum allowed number of running jobs for a particular workflow SWID.")
          .labelNames("workflow", "action_name")
          .register();
  static final Gauge slowFetch =
      Gauge.build(
              "shesmu_niassa_slow_fetch_count",
              "The number of times a slow fetch (get workflow run and LIMS keys separately) had to be used.")
          .labelNames("workflow")
          .register();

  static ActionState processingStateToActionState(String state) {
    if (state == null) {
      return ActionState.UNKNOWN;
    }
    return processingStateToActionState(WorkflowRunStatus.valueOf(state));
  }

  static ActionState processingStateToActionState(WorkflowRunStatus state) {
    switch (state) {
      case submitted:
      case submitted_retry:
        return ActionState.WAITING;
      case pending:
        return ActionState.QUEUED;
      case running:
        return ActionState.INFLIGHT;
      case cancelled:
      case submitted_cancel:
      case failed:
        return ActionState.FAILED;
      case completed:
        return ActionState.SUCCEEDED;
      default:
        return ActionState.UNKNOWN;
    }
  }

  private final AnalysisCache analysisCache;
  private final AnalysisDataCache analysisDataCache;
  private Optional<Configuration> configuration = Optional.empty();
  private Optional<String> cromwellUrl = Optional.empty();
  private final Definer<NiassaServer> definer;
  private final DirectoryAndIniCache directoryAndIniCache;
  private String host;
  private final Map<Integer, Set<Pair<String, String>>> launchLocks = new ConcurrentHashMap<>();
  private final Map<String, Integer> maxInFlight = new ConcurrentHashMap<>();
  private final MaxInFlightCache maxInFlightCache;
  private Supplier<MetadataWS> metadataConstructor =
      () -> {
        throw new IllegalStateException("Metadata WS is not initialised");
      };
  private Properties settings = new Properties();
  private final ValueCache<Stream<Pair<Tuple, Tuple>>, Stream<Pair<Tuple, Tuple>>> skipCache;
  private final Map<Long, Set<Pair<String, String>>> staleKeys = new ConcurrentSkipListMap<>();
  private final Runnable unsubscribe = WORKFLOWS.subscribe(this::updateWorkflows);
  private String url;

  public NiassaServer(Path fileName, String instanceName, Definer<NiassaServer> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    this.definer = definer;
    analysisCache = new AnalysisCache(fileName);
    analysisDataCache = new AnalysisDataCache(fileName);
    directoryAndIniCache = new DirectoryAndIniCache(fileName);
    maxInFlightCache = new MaxInFlightCache(fileName);
    skipCache = new SkipLaneCache(fileName);
  }

  public LaunchLock acquireLock(
      long workflowAccession,
      Map<String, String> annotations,
      List<SimpleLimsKey> limsKeys,
      LockLogger logger) {
    return new LaunchLock(
        logger,
        workflowAccession,
        limsKeys,
        launchLocks.computeIfAbsent(
            Objects.hash(workflowAccession, annotations), k -> new HashSet<>()));
  }

  public KeyValueCache<Long, Stream<AnalysisState>, Stream<AnalysisState>> analysisCache() {
    return analysisCache;
  }

  @ShesmuAction(description = "Add an annotation to a file.")
  public AnnotationAction<FileAttribute> annotate_file() {
    return new AnnotationAction<>(definer, FILE);
  }

  @ShesmuAction(description = "Add an annotation to an IUS.")
  public AnnotationAction<IUSAttribute> annotate_ius() {
    return new AnnotationAction<>(definer, IUS);
  }

  @ShesmuAction(description = "Add an annotation to a workflow run.")
  public AnnotationAction<WorkflowRunAttribute> annotate_workflow_run() {
    return new AnnotationAction<>(definer, WORKFLOW_RUN);
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    renderer.line("Filename", fileName().toString());
    configuration.ifPresent(
        c -> {
          renderer.line("Settings", c.getSettings());
        });
  }

  public Optional<String> cromwellUrl() {
    return cromwellUrl;
  }

  public WorkflowRunEssentials directoryAndIni(long workflowRun) {
    return directoryAndIniCache.get(workflowRun).orElse(WorkflowRunEssentials.EMPTY);
  }

  public Stream<Pair<DisplayElement, DisplayElement>> displayMaxInfo(
      long workflowAccession, String workflowName) {
    if (workflowAccession == 0) {
      return Stream.of(
          new Pair<>(
              SupplementaryInformation.text("Max in Flight"),
              SupplementaryInformation.bold("Not a real workflow")));
    }
    try {
      return maxInFlightCache
          .getStale(workflowAccession)
          .map(m -> m.display(workflowName))
          .orElseGet(
              () ->
                  Stream.of(
                      new Pair<>(
                          SupplementaryInformation.text("Max in Flight"),
                          SupplementaryInformation.text("Data not available"))));
    } catch (InitialCachePopulationException e) {
      return Stream.of(
          new Pair<>(
              SupplementaryInformation.text("Max in Flight"),
              SupplementaryInformation.text("Data not available yet")));
    }
  }

  public String host() {
    return host;
  }

  public void invalidateDirectoryAndIni(long workfowRunSwid) {
    directoryAndIniCache.invalidate(workfowRunSwid);
  }

  public void invalidateMaxInFlight(long workflowRunSwid) {
    maxInFlightCache.invalidate(workflowRunSwid);
  }

  @ShesmuMethod(
      description = "Whether an IUS and LIMS key combination has been marked as skipped in {file}.")
  public boolean is_skipped(
      @ShesmuParameter(description = "IUS", type = "t3sis") Tuple ius,
      @ShesmuParameter(description = "LIMS key", type = "t3sss") Tuple lims) {
    return skipCache.get().anyMatch(new Pair<>(ius, lims)::equals);
  }

  public MaxStatus maxInFlight(
      ActionServices services,
      String workflowName,
      long workflowAccession,
      Set<String> overloadedServices) {
    // Ban all jobs with invalid accessions from running
    if (workflowAccession < 1) {
      return MaxStatus.INVALID_SWID;
    }
    overloadedServices.addAll(services.isOverloaded("niassa-launch", workflowName));
    if (!overloadedServices.isEmpty()) {
      return MaxStatus.EXTERNAL_THROTTLE;
    }
    synchronized (this) {
      return maxInFlightCache
          .get(workflowAccession)
          .map(running -> running.check(workflowName))
          .orElse(MaxStatus.RUN);
    }
  }

  public Metadata metadata() {
    return metadataConstructor.get();
  }

  @ShesmuInputSource
  public Stream<CerberusAnalysisProvenanceValue> provenance(boolean readStale) {
    return readStale ? analysisDataCache.getStale() : analysisDataCache.get();
  }

  public Stream<String> services() {
    return configuration.map(Configuration::getServices).orElse(Collections.emptyList()).stream();
  }

  public Properties settings() {
    return settings;
  }

  @Override
  public void stop() {
    unsubscribe.run();
  }

  @Override
  protected synchronized Optional<Integer> update(Configuration value) {
    // Read the settings
    final Properties settings = new Properties();
    try (InputStream settingsInput = new FileInputStream(value.getSettings())) {
      settings.load(settingsInput);
    } catch (final Exception e) {
      e.printStackTrace();
      return Optional.of(2);
    }
    cromwellUrl = Optional.ofNullable(value.getCromwellUrl());
    metadataConstructor =
        () ->
            new MetadataWS(
                settings.getProperty("SW_REST_URL"),
                settings.getProperty("SW_REST_USER"),
                settings.getProperty("SW_REST_PASS"));
    host = settings.getProperty("SW_HOST", host);
    url = settings.getProperty("SW_REST_URL", url);
    this.settings = settings;
    analysisCache.invalidateAll();
    maxInFlightCache.invalidateAll();
    skipCache.invalidate();
    configuration = Optional.of(value);
    updateWorkflows();
    return Optional.empty();
  }

  private void updateWorkflows() {
    definer.clearActions();
    definer.clearConstants();
    final Map<String, AlgebraicValue> workflowKind = new TreeMap<>();

    configuration
        .map(Configuration::getPrefix)
        .ifPresent(
            prefix ->
                WORKFLOWS.stream()
                    .flatMap(WorkflowFile::stream)
                    .forEach(
                        wc -> {
                          maxInFlightGauge
                              .labels(
                                  Long.toString(wc.second().getAccession()), prefix + wc.first())
                              .set(wc.second().getMaxInFlight());
                          maxInFlight.put(prefix + wc.first(), wc.second().getMaxInFlight());
                          wc.second().define(prefix + wc.first(), definer, workflowKind);
                        }));

    definer.defineConstant(
        "workflow_kind",
        "The kind/category of workflow for a particular action name",
        Imyhat.dictionary(
            Imyhat.STRING,
            workflowKind.values().stream()
                .map(AlgebraicValue::name)
                .map(Imyhat::algebraicTuple)
                .reduce(Imyhat::unify)
                .orElse(Imyhat.BAD)),
        workflowKind);
  }

  public String url() {
    return url;
  }
}
