package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.provenance.model.SampleProvenance;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.cache.MergingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonListBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.runscanner.RunScannerPluginType;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.ws.dto.InstrumentModelDto;
import ca.on.oicr.ws.dto.LaneProvenanceDto;
import ca.on.oicr.ws.dto.RunDto;
import ca.on.oicr.ws.dto.RunDtoPosition;
import ca.on.oicr.ws.dto.SampleProjectDto;
import ca.on.oicr.ws.dto.SampleProvenanceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.prometheus.client.Gauge;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.function.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PinerySource extends JsonPluginFile<PineryConfiguration> {

  private final class ItemCache extends ValueCache<Stream<PineryIUSValue>, Stream<PineryIUSValue>> {
    private ItemCache(Path fileName) {
      super("pinery " + fileName.toString(), 30, ReplacingRecord::new);
    }

    @Override
    protected Stream<PineryIUSValue> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return Stream.empty();
      }
      final PineryConfiguration cfg = config.get();
      final Map<String, Integer> badSetCounts = new TreeMap<>();
      final Map<String, RunDto> allRuns =
          HTTP_CLIENT
              .send(
                  HttpRequest.newBuilder(URI.create(cfg.getUrl() + "/sequencerruns")).GET().build(),
                  new JsonListBodyHandler<>(MAPPER, RunDto.class))
              .body()
              .get()
              .collect(
                  Collectors.toMap(
                      RunDto::getName,
                      Function.identity(),
                      // If duplicate run names occur, pick one at random, because the universe is
                      // spiteful, so we spite it back.
                      (a, b) -> a));
      final Set<Pair<String, String>> validLanes = new HashSet<>();
      return Stream.concat(
              lanes(
                  cfg.getUrl(),
                  cfg.getVersion(),
                  cfg.getProvider(),
                  badSetCounts,
                  allRuns,
                  (run, lane) -> validLanes.add(new Pair<>(run, lane))),
              samples(
                  cfg.getUrl(),
                  cfg.getVersion(),
                  cfg.getProvider(),
                  badSetCounts,
                  allRuns,
                  (run, lane) -> validLanes.contains(new Pair<>(run, lane))))
          .onClose(
              () ->
                  badSetCounts.forEach(
                      (key, value) ->
                          badSetMap
                              .labels(
                                  Stream.concat(
                                          Stream.of(fileName().toString()),
                                          Stream.of(key.split(":")))
                                      .toArray(String[]::new))
                              .set(value)));
    }

    private Stream<PineryIUSValue> lanes(
        String baseUrl,
        int version,
        String provider,
        Map<String, Integer> badSetCounts,
        Map<String, RunDto> allRuns,
        BiConsumer<String, String> addLane)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              HttpRequest.newBuilder(
                      URI.create(baseUrl + "/provenance/v" + version + "/lane-provenance"))
                  .GET()
                  .build(),
              new JsonListBodyHandler<>(MAPPER, LaneProvenanceDto.class))
          .body()
          .get()
          .filter(
              lp ->
                  isRunValid(allRuns.get(lp.getSequencerRunName()))
                      && (lp.getSkip() == null || !lp.getSkip()))
          .map(
              lp -> {
                final RunDto run = allRuns.get(lp.getSequencerRunName());
                if (run == null) {
                  badSetCounts.merge("run:null", 1, Integer::sum);
                  return null;
                }
                final Instant lastModified =
                    lp.getLastModified() == null ? Instant.EPOCH : lp.getLastModified().toInstant();
                addLane.accept(lp.getSequencerRunName(), lp.getLaneNumber());

                return new PineryIUSValue(
                    Optional.empty(),
                    run.getRunBasesMask() == null ? "" : run.getRunBasesMask(),
                    Set.of(),
                    Optional.empty(),
                    Optional.ofNullable(lp.getCreatedDate()).map(ZonedDateTime::toInstant),
                    maybeGetRunField(run, RunDto::getContainerModel),
                    "",
                    Optional.empty(),
                    "",
                    new Tuple(
                        lp.getProvenanceId(),
                        config.get().shortProvider(),
                        false,
                        Map.of("pinery-hash-" + version, lp.getVersion())),
                    "",
                    flowcellGeometry(run),
                    "",
                    "",
                    lp.getSequencerRunPlatformModel(),
                    new Tuple(
                        lp.getSequencerRunName(),
                        IUSUtils.parseLaneNumber(lp.getLaneNumber()),
                        "NoIndex"),
                    "",
                    "",
                    "",
                    0L,
                    "",
                    new Tuple(lp.getLaneProvenanceId(), provider, lastModified, lp.getVersion()),
                    "",
                    Paths.get(
                        run.getRunDirectory() == null || run.getRunDirectory().equals("")
                            ? "/"
                            : run.getRunDirectory()),
                    "",
                    Optional.empty(),
                    Optional.empty(),
                    run.getId(),
                    runLaneCount(run),
                    getRunField(run, RunDto::getState),
                    "",
                    maybeGetRunField(run, RunDto::getSequencingKit),
                    maybeGetRunField(run, RunDto::getWorkflowType).orElse(""),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    run.getStartDate() == null || run.getStartDate().isEmpty()
                        ? Instant.EPOCH
                        : ZonedDateTime.parse(run.getStartDate()).toInstant(),
                    Optional.empty(),
                    Optional.empty(),
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    lastModified,
                    false,
                    false);
              })
          .filter(Objects::nonNull);
    }

    private Stream<PineryIUSValue> samples(
        String baseUrl,
        int version,
        String provider,
        Map<String, Integer> badSetCounts,
        Map<String, RunDto> allRuns,
        BiPredicate<String, String> hasLane)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              HttpRequest.newBuilder(
                      URI.create(baseUrl + "/provenance/v" + version + "/sample-provenance"))
                  .GET()
                  .build(),
              new JsonListBodyHandler<>(MAPPER, SampleProvenanceDto.class))
          .body()
          .get()
          .filter(
              sp ->
                  isRunValid(allRuns.get(sp.getSequencerRunName()))
                      && hasLane.test(sp.getSequencerRunName(), sp.getLaneNumber()))
          .map(
              sp -> {
                final Set<String> badSetInRecord = new TreeSet<>();
                final Instant lastModified =
                    sp.getLastModified() == null ? Instant.EPOCH : sp.getLastModified().toInstant();
                final RunDto run = allRuns.get(sp.getSequencerRunName());
                if (run == null) return null;
                final PineryIUSValue result =
                    new PineryIUSValue(
                        limsAttr(sp, "barcode_kit", badSetInRecord::add, false),
                        run.getRunBasesMask() == null ? "" : run.getRunBasesMask(),
                        limsAttr(sp, "batches", badSetInRecord::add, false)
                            .<Set<String>>map(
                                s ->
                                    COMMA
                                        .splitAsStream(s)
                                        .collect(Collectors.toCollection(TreeSet::new)))
                            .orElse(Set.of()),
                        limsAttr(sp, "cell_viability", badSetInRecord::add, false)
                            .map(Double::parseDouble),
                        Optional.ofNullable(sp.getCreatedDate()).map(ZonedDateTime::toInstant),
                        maybeGetRunField(run, RunDto::getContainerModel),
                        sp.getRootSampleName(),
                        limsAttr(sp, "dv200", badSetInRecord::add, false).map(Double::parseDouble),
                        limsAttr(sp, "geo_external_name", badSetInRecord::add, false).orElse(""),
                        new Tuple(
                            sp.getProvenanceId(),
                            config.get().shortProvider(),
                            false,
                            Map.of("pinery-hash-" + version, sp.getVersion())),
                        limsAttr(sp, "geo_tube_id", badSetInRecord::add, false).orElse(""),
                        flowcellGeometry(run),
                        limsAttr(sp, "geo_group_id_description", badSetInRecord::add, false)
                            .orElse(""),
                        limsAttr(sp, "geo_group_id", badSetInRecord::add, false).orElse(""),
                        sp.getSequencerRunPlatformModel(),
                        new Tuple(
                            sp.getSequencerRunName(),
                            IUSUtils.parseLaneNumber(sp.getLaneNumber()),
                            sp.getIusTag()),
                        limsAttr(sp, "geo_prep_kit", badSetInRecord::add, false).orElse(""),
                        limsAttr(sp, "geo_library_source_template_type", badSetInRecord::add, true)
                            .orElse(""),
                        sp.getSampleName(),
                        limsAttr(sp, "geo_library_size_code", badSetInRecord::add, false)
                            .map(IUSUtils::parseLong)
                            .orElse(0L),
                        limsAttr(sp, "geo_library_type", badSetInRecord::add, false).orElse(""),
                        new Tuple(
                            sp.getSampleProvenanceId(), provider, lastModified, sp.getVersion()),
                        limsAttr(sp, "geo_organism", badSetInRecord::add, true).orElse(""),
                        Paths.get(
                            run.getRunDirectory() == null || run.getRunDirectory().equals("")
                                ? "/"
                                : run.getRunDirectory()),
                        sp.getStudyTitle(),
                        limsAttr(sp, "reference_slide_id", badSetInRecord::add, false),
                        limsAttr(sp, "rin", badSetInRecord::add, false).map(Double::parseDouble),
                        run.getId(),
                        runLaneCount(run),
                        getRunField(run, RunDto::getState),
                        limsAttr(sp, "sequencing_control_type", badSetInRecord::add, false)
                            .orElse(""),
                        maybeGetRunField(run, RunDto::getSequencingKit),
                        maybeGetRunField(run, RunDto::getWorkflowType).orElse(""),
                        limsAttr(sp, "sex", badSetInRecord::add, false),
                        limsAttr(sp, "spike_in", badSetInRecord::add, false),
                        limsAttr(sp, "spike_in_dilution_factor", badSetInRecord::add, false),
                        limsAttr(sp, "spike_in_volume_ul", badSetInRecord::add, false)
                            .map(Double::parseDouble),
                        run.getStartDate() == null || run.getStartDate().isEmpty()
                            ? Instant.EPOCH
                            : ZonedDateTime.parse(run.getStartDate()).toInstant(),
                        limsAttr(sp, "subproject", badSetInRecord::add, false)
                            .filter(p -> !p.isBlank()),
                        limsAttr(sp, "target_cell_recovery", badSetInRecord::add, false)
                            .map(Double::parseDouble),
                        IUSUtils.tissue(sp.getParentSampleName()),
                        limsAttr(sp, "geo_tissue_type", badSetInRecord::add, true).orElse(""),
                        limsAttr(sp, "geo_tissue_origin", badSetInRecord::add, true).orElse(""),
                        limsAttr(sp, "geo_tissue_preparation", badSetInRecord::add, false)
                            .orElse(""),
                        limsAttr(sp, "geo_targeted_resequencing", badSetInRecord::add, false)
                            .orElse(""),
                        limsAttr(sp, "geo_tissue_region", badSetInRecord::add, false).orElse(""),
                        lastModified,
                        limsAttr(sp, "umis", badSetInRecord::add, false)
                            .map(Boolean::parseBoolean)
                            .orElse(false),
                        true);

                if (badSetInRecord.isEmpty()) {
                  return result;
                } else {
                  badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, Integer::sum));
                  return null;
                }
              })
          .filter(Objects::nonNull);
    }
  }

  private final class PlatformCache
      extends ValueCache<Optional<Map<String, String>>, Optional<Map<String, String>>> {
    private PlatformCache(Path fileName) {
      super("pinery-platform " + fileName.toString(), 30, SimpleRecord::new);
    }

    @Override
    protected Optional<Map<String, String>> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return Optional.empty();
      }
      final PineryConfiguration cfg = config.get();
      return Optional.of(
          HTTP_CLIENT
              .send(
                  HttpRequest.newBuilder(URI.create(config.get().getUrl() + "/instrumentmodels"))
                      .GET()
                      .build(),
                  new JsonListBodyHandler<>(MAPPER, InstrumentModelDto.class))
              .body()
              .get()
              .filter(i -> !i.getName().equals("unspecified"))
              .collect(
                  Collectors.toMap(InstrumentModelDto::getName, InstrumentModelDto::getPlatform)));
    }
  }

  private class ProjectCache
      extends ValueCache<Stream<SampleProjectDto>, Stream<SampleProjectDto>> {

    public ProjectCache(Path fileName) {
      super(
          "pinery_projects " + fileName.toString(),
          60,
          MergingRecord.by(SampleProjectDto::getName));
    }

    @Override
    protected Stream<SampleProjectDto> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) return Stream.empty();
      return HTTP_CLIENT
          .send(
              HttpRequest.newBuilder(URI.create(config.get().getUrl() + "/sample/projects"))
                  .GET()
                  .build(),
              new JsonListBodyHandler<>(MAPPER, SampleProjectDto.class))
          .body()
          .get();
    }
  }

  private static final Pattern COMMA = Pattern.compile(",");

  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Gauge badSetMap =
      Gauge.build(
              "shesmu_pinery_bad_set",
              "The number of provenance records with sets not containing exactly one item.")
          .labelNames("target", "property", "reason")
          .register();

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  private static Set<Set<Long>> flowcellGeometry(RunDto run) {
    final var lanes =
        run.getPositions().stream()
            .map(RunDtoPosition::getPosition)
            .max(Comparator.naturalOrder())
            .orElse(0);
    final var isJoined =
        Objects.equals(run.getChemistry(), "NS_HIGH")
            || Objects.equals(run.getChemistry(), "NS_MID")
            || run.getWorkflowType() != null
                && RunScannerPluginType.isWorkflowTypeJoined(run.getWorkflowType());
    return RunScannerPluginType.getFlowcellLayout(lanes, isJoined);
  }

  private static boolean isRunValid(RunDto run) {
    return run != null && run.getCreatedDate() != null;
  }

  private static Optional<String> limsAttr(
      SampleProvenance sp, String key, Consumer<String> isBad, boolean required) {
    return IUSUtils.singleton(
        sp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), required);
  }

  private static long runLaneCount(RunDto dto) {
    return dto.getPositions().stream()
        .map(RunDtoPosition::getPosition)
        .max(Comparator.naturalOrder())
        .orElse(0);
  }

  private final ItemCache cache;
  private Set<String> clinicalPipelines;
  private Optional<PineryConfiguration> config = Optional.empty();
  private final PlatformCache platforms;
  private final ProjectCache projects;

  public PinerySource(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, PineryConfiguration.class);
    projects = new ProjectCache(fileName);
    cache = new ItemCache(fileName);
    platforms = new PlatformCache(fileName);
  }

  @ShesmuMethod(
      name = "active_projects",
      description = "Projects marked active from in Pinery defined in {file}.")
  public Set<String> activeProjects() {
    return projects
        .get()
        .filter(SampleProjectDto::isActive)
        .map(SampleProjectDto::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @ShesmuMethod(name = "projects", description = "All projects from in Pinery defined in {file}.")
  public Set<String> allProjects() {
    return projects
        .get()
        .map(SampleProjectDto::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @ShesmuMethod(
      name = "clinical_projects",
      description = "Projects marked clinical from in Pinery defined in {file}.")
  public Set<String> clinicalProjects() {
    return projects
        .get()
        .filter(
            project -> project.getPipeline() != null && isClinicalPipeline(project.getPipeline()))
        .map(SampleProjectDto::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    final Optional<String> url = config.map(PineryConfiguration::getUrl);
    renderer.link("URL", url.orElse("about:blank"), url.orElse("Unknown"));
    renderer.line("Provider", config.map(PineryConfiguration::getProvider).orElse("Unknown"));
  }

  private String getRunField(RunDto run, Function<RunDto, String> getField) {
    Optional<String> val = maybeGetRunField(run, getField);
    return val.orElse("");
  }

  @ShesmuMethod(
      name = "is_clinical_pipeline",
      description = "Check if a project pipeline would be considered clinical.")
  public boolean isClinicalPipeline(String pipeline) {
    if (clinicalPipelines == null) {
      // This is the default behaviour removed from MISO
      return pipeline.equals("Clinical") || pipeline.startsWith("Accredited");
    }
    return clinicalPipelines.contains(pipeline);
  }

  private Optional<String> maybeGetRunField(RunDto run, Function<RunDto, String> getField) {
    Optional<String> maybeVal = Optional.empty();
    if (run != null) {
      maybeVal = Optional.ofNullable(getField.apply(run));
    }
    return maybeVal;
  }

  @ShesmuMethod(
      name = "platform_for_instrument_model",
      description = "The the platform of the instrument model for the Pinery defined in {file}.",
      type = "u7ILLUMINA$t0IONTORRENT$t0LS454$t0OXFORDNANOPORE$t0PACBIO$t0SOLID$t0UNKNOWN$t0")
  public AlgebraicValue platformForInstrumentModel(
      @ShesmuParameter(description = "The instrument model name as found in Pinery")
          String instrumentModel) {
    // Return values are
    // https://github.com/miso-lims/miso-lims/blob/master/core/src/main/java/uk/ac/bbsrc/tgac/miso/core/data/type/PlatformType.java + UNKNOWN
    return new AlgebraicValue(
        platforms.get().orElse(Map.of()).getOrDefault(instrumentModel, "UNKNOWN"));
  }

  @ShesmuMethod(
      name = "projects_with_secondary_scheme",
      description =
          "Projects marked with the secondary naming scheme from in Pinery defined in {file}.")
  public Set<String> secondaryProjects() {
    return projects
        .get()
        .filter(SampleProjectDto::isSecondaryNamingSCheme)
        .map(SampleProjectDto::getName)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public Stream<String> services() {
    return Stream.of("pinery");
  }

  @ShesmuInputSource
  public Stream<PineryIUSValue> streamIUS(boolean readStale) {
    return readStale ? cache.getStale() : cache.get();
  }

  @ShesmuInputSource
  public Stream<PineryProjectValue> streamProjects(boolean readStale) {
    final String provider = config.map(PineryConfiguration::getProvider).orElse("unknown");
    return (readStale ? projects.getStale() : projects.get())
        .map(backing -> new PineryProjectValue(backing, provider));
  }

  @Override
  protected Optional<Integer> update(PineryConfiguration value) {
    config = Optional.of(value);
    clinicalPipelines = value.getClinicalPipelines();
    projects.invalidate();
    cache.invalidate();
    return Optional.empty();
  }
}
