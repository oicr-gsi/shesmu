package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.model.AnalysisProvenance;
import ca.on.oicr.gsi.shesmu.cerberus.CerberusAnalysisProvenanceValue;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.*;
import ca.on.oicr.gsi.shesmu.plugin.files.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
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
import java.util.concurrent.atomic.AtomicLong;
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
  private class AnalysisCache extends KeyValueCache<Long, Stream<AnalysisState>> {
    public AnalysisCache(Path fileName) {
      super("niassa-analysis " + fileName.toString(), 120, ReplacingRecord::new);
    }

    @Override
    protected Stream<AnalysisState> fetch(Long key, Instant lastUpdated) throws IOException {
      if (metadata == null) {
        return Stream.empty();
      }
      slowFetch.labels(key.toString()).set(0);
      final Runnable incrementSlowFetch = slowFetch.labels(key.toString())::inc;
      final Map<Integer, LimsKey> limsKeyCache = new HashMap<>();
      final Map<FileProvenanceFilter, Set<String>> filters =
          new EnumMap<>(FileProvenanceFilter.class);
      filters.put(FileProvenanceFilter.workflow, Collections.singleton(Long.toString(key)));
      final AtomicLong badStatusCount = new AtomicLong();
      return metadata
          .getAnalysisProvenance(filters)
          .stream()
          .filter(
              ap -> {
                if (ap.getWorkflowRunStatus() == null) {
                  badStatusCount.incrementAndGet();
                  return false;
                }
                return true;
              })
          .onClose(() -> badStatus.labels(url, Long.toString(key)).set(badStatusCount.get()))
          .filter(
              ap ->
                  ap.getWorkflowId() != null
                      && (ap.getSkip() == null || !ap.getSkip())
                      && Stream.of(
                              ap.getFileAttributes(),
                              ap.getIusAttributes(),
                              ap.getWorkflowRunAttributes())
                          .noneMatch(a -> a.containsKey("skip")))
          .collect(Collectors.groupingBy(AnalysisProvenance::getWorkflowRunId))
          .entrySet()
          .stream()
          .map(
              e ->
                  new AnalysisState(
                      e.getKey(),
                      () -> metadata.getWorkflowRunWithIuses(e.getKey()),
                      iusAccession ->
                          limsKeyCache.computeIfAbsent(iusAccession, metadata::getLimsKeyFrom),
                      e.getValue(),
                      incrementSlowFetch));
    }
  }

  private class AnalysisDataCache extends ValueCache<Stream<CerberusAnalysisProvenanceValue>> {
    public AnalysisDataCache(Path fileName) {
      super("niassa-data-analysis " + fileName.toString(), 20, ReplacingRecord::new);
    }

    @Override
    protected Stream<CerberusAnalysisProvenanceValue> fetch(Instant lastUpdated)
        throws IOException {
      if (metadata == null) {
        return Stream.empty();
      }
      return metadata.getAnalysisProvenance().stream().map(CerberusAnalysisProvenanceValue::new);
    }
  }

  private class DirectoryAndIniCache extends KeyValueCache<Long, Optional<WorkflowRunEssentials>> {
    public DirectoryAndIniCache(Path fileName) {
      super("niassa-dir+ini " + fileName.toString(), 60 * 24 * 365, SimpleRecord::new);
    }

    @Override
    protected Optional<WorkflowRunEssentials> fetch(Long key, Instant lastUpdated)
        throws Exception {
      final WorkflowRun run = metadata.getWorkflowRun(key.intValue());
      final Properties ini = new Properties();
      ini.load(new StringReader(run.getIniFile()));
      final Optional<String> cromwellId =
          run.getWorkflowRunAttributes()
              .stream()
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

  private class MaxInFlightCache extends KeyValueCache<Long, Optional<Integer>> {
    public MaxInFlightCache(Path fileName) {
      super("niassa-max-in-flight " + fileName.toString(), 5, SimpleRecord::new);
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
    protected Optional<Integer> fetch(Long workflowRunSwid, Instant lastUpdated)
        throws IOException {
      if (metadata == null) {
        return Optional.empty();
      }
      return Optional.of(
          Stream.of(
                  WorkflowRunStatus.pending,
                  WorkflowRunStatus.running,
                  WorkflowRunStatus.submitted,
                  WorkflowRunStatus.submitted_retry)
              .mapToInt(
                  status ->
                      countRecords(
                          metadata.getWorkflowRunReport(
                              workflowRunSwid.intValue(), status, null, null)))
              .sum());
    }
  }

  private class SkipLaneCache extends ValueCache<Stream<Pair<Tuple, Tuple>>> {
    public SkipLaneCache(Path fileName) {
      super("niassa-skipped " + fileName.toString(), 20, ReplacingRecord::new);
    }

    @Override
    protected Stream<Pair<Tuple, Tuple>> fetch(Instant lastUpdated) throws IOException {
      if (metadata == null) {
        return Stream.empty();
      }
      return metadata
          .getAnalysisProvenance()
          .stream()
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
  private static final Gauge badStatus =
      Gauge.build("shesmu_niassa_bad_status", "The number of workflow runs that have null status.")
          .labelNames("target", "workflow")
          .register();
  private static final Gauge foundRunning =
      Gauge.build(
              "shesmu_niassa_found_running",
              "The number of workflow runs that Shesmu believes it has found. This is used for the max in flight checks.")
          .labelNames("target", "workflow")
          .register();
  static final Gauge slowFetch =
      Gauge.build(
              "shesmu_niassa_slow_fetch_count",
              "The number of times a slow fetch (get workflow run and LIMS keys separately) had to be used.")
          .labelNames("workflow")
          .register();
  private final AnalysisCache analysisCache;
  private final AnalysisDataCache analysisDataCache;
  private Optional<Configuration> configuration = Optional.empty();
  private Optional<String> cromwellUrl = Optional.empty();
  private final Definer<NiassaServer> definer;
  private final DirectoryAndIniCache directoryAndIniCache;
  private String host;
  private final Map<String, Integer> maxInFlight = new ConcurrentHashMap<>();
  private final MaxInFlightCache maxInFlightCache;
  private Metadata metadata;
  private Properties settings = new Properties();
  private final ValueCache<Stream<Pair<Tuple, Tuple>>> skipCache;
  private final Runnable unsubscribe = WORKFLOWS.subscribe(this::updateWorkflows);
  private String url;

  public NiassaServer(Path fileName, String instanceNane, Definer<NiassaServer> definer) {
    super(fileName, instanceNane, MAPPER, Configuration.class);
    this.definer = definer;
    analysisCache = new AnalysisCache(fileName);
    analysisDataCache = new AnalysisDataCache(fileName);
    directoryAndIniCache = new DirectoryAndIniCache(fileName);
    maxInFlightCache = new MaxInFlightCache(fileName);
    skipCache = new SkipLaneCache(fileName);
  }

  @ShesmuMethod(
      description = "Whether an IUS and LIMS key combination has been marked as skipped in {file}.")
  public boolean $_is_skipped(
      @ShesmuParameter(description = "IUS", type = "t3sis") Tuple ius,
      @ShesmuParameter(description = "LIMS key", type = "t3sss") Tuple lims) {
    return skipCache.get().anyMatch(new Pair<>(ius, lims)::equals);
  }

  public KeyValueCache<Long, Stream<AnalysisState>> analysisCache() {
    return analysisCache;
  }

  @ShesmuAction(description = "Add an annotation to a file.")
  public AnnotationAction<FileAttribute> annotate_$_file() {
    return new AnnotationAction<>(definer, FILE);
  }

  @ShesmuAction(description = "Add an annotation to an IUS.")
  public AnnotationAction<IUSAttribute> annotate_$_ius() {
    return new AnnotationAction<>(definer, IUS);
  }

  @ShesmuAction(description = "Add an annotation to a workflow run.")
  public AnnotationAction<WorkflowRunAttribute> annotate_$_workflow_run() {
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

  public String host() {
    return host;
  }

  public void invalidateMaxInFlight(long workflowRunSwid) {
    maxInFlightCache.invalidate(workflowRunSwid);
  }

  public synchronized boolean maxInFlight(String workflowName, long workflowAccession) {
    // Ban all jobs with invalid accessions from running
    if (workflowAccession < 1) {
      return true;
    }
    return maxInFlightCache
        .get(workflowAccession)
        .map(
            running -> {
              foundRunning.labels(url(), workflowName).set(running);
              return running >= maxInFlight.getOrDefault(workflowName, 0);
            })
        .orElse(true);
  }

  public Metadata metadata() {
    return metadata;
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
    metadata =
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

    configuration
        .map(Configuration::getPrefix)
        .ifPresent(
            prefix ->
                WORKFLOWS
                    .stream()
                    .flatMap(WorkflowFile::stream)
                    .forEach(
                        wc -> {
                          maxInFlight.put(prefix + wc.first(), wc.second().getMaxInFlight());
                          wc.second().define(prefix + wc.first(), definer);
                        }));
  }

  public String url() {
    return url;
  }
}
