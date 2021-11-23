package ca.on.oicr.gsi.shesmu.pipedev;

import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceSkippedValue;
import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceValue;
import ca.on.oicr.gsi.shesmu.gsicommon.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFile;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Gauge;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BaseProvenancePluginType<C extends AutoCloseable>
    extends PluginFileType<BaseProvenancePluginType.FileConfiguration> {
  class FileConfiguration extends PluginFile {
    private class ItemCache
        extends ValueCache<
            Stream<CerberusFileProvenanceValue>, Stream<CerberusFileProvenanceValue>> {

      public ItemCache() {
        super(name + " " + fileName().toString(), 60, ReplacingRecord::new);
      }

      @Override
      protected Stream<CerberusFileProvenanceValue> fetch(Instant lastUpdated) {
        final AtomicInteger badFilePaths = new AtomicInteger();
        final AtomicInteger badSets = new AtomicInteger();
        final AtomicInteger badVersions = new AtomicInteger();
        final Map<String, Integer> badSetCounts = new TreeMap<>();
        return client
            .map(BaseProvenancePluginType.this::fetch)
            .orElseGet(Stream::empty)
            .filter(
                fp ->
                    (fp.getSkip() == null || fp.getSkip().equals("false"))
                        && fp.getStatus() != FileProvenance.Status.ERROR)
            .filter(
                fp -> {
                  if (fp.getFilePath() == null) {
                    badFilePaths.incrementAndGet();
                    return false;
                  }
                  return true;
                })
            .map(
                fp -> {
                  final AtomicReference<Boolean> badRecord = new AtomicReference<>(false);
                  final Set<String> badSetInRecord = new TreeSet<>();
                  final Optional<LimsKey> limsKey =
                      IUSUtils.singleton(
                              fp.getIusLimsKeys(),
                              reason -> badSetInRecord.add("limskey:" + reason),
                              true)
                          .map(IusLimsKey::getLimsKey);
                  final Optional<Tuple> workflowVersion =
                      IUSUtils.parseWorkflowVersion(fp.getWorkflowVersion());
                  if (!workflowVersion.isPresent()) {
                    badVersions.incrementAndGet();
                    badRecord.set(true);
                  }
                  final CerberusFileProvenanceValue result =
                      new PipeDevCerberusFileProvenanceValue(
                          fp.getFileSWID().toString(),
                          limsAttr(fp, "barcode_kit", badSetInRecord::add),
                          limsAttr(fp, "batches", badSetInRecord::add)
                              .<Set<String>>map(
                                  s ->
                                      COMMA
                                          .splitAsStream(s)
                                          .collect(Collectors.toCollection(TreeSet::new)))
                              .orElse(Set.of()),
                          limsAttr(fp, "cell_viability", badSetInRecord::add)
                              .map(Double::parseDouble),
                          fp.getLastModified().toInstant(),
                          IUSUtils.singleton(
                                  fp.getRootSampleNames(),
                                  reason -> badSetInRecord.add("samplenames:" + reason),
                                  false)
                              .orElse(""),
                          limsAttr(fp, "geo_external_name", badSetInRecord::add).orElse(""),
                          new Tuple(
                              limsKey.map(LimsKey::getId).orElse(""),
                              limsKey.map(LimsKey::getProvider).orElse(""),
                              fp.getStatus() == FileProvenance.Status.STALE,
                              Collections.singletonMap(
                                  "pinery-legacy", limsKey.map(LimsKey::getVersion).orElse(""))),
                          limsAttr(fp, "geo_tube_id", badSetInRecord::add).orElse(""),
                          fp.getFileAttributes(),
                          IUSUtils.parseLong(fp.getFileSize()),
                          limsAttr(fp, "geo_group_id_description", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_group_id", badSetInRecord::add).orElse(""),
                          IUSUtils.singleton(
                                  fp.getSequencerRunPlatformNames(),
                                  reason -> badSetInRecord.add("instrument_model: " + reason),
                                  true)
                              .orElse(""),
                          fp.getWorkflowRunInputFileSWIDs().stream()
                              .map(Object::toString)
                              .collect(Collectors.toSet()),
                          packIUS(fp),
                          limsAttr(fp, "geo_prep_kit", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_library_source_template_type", badSetInRecord::add)
                              .orElse(""),
                          IUSUtils.singleton(
                                  fp.getSampleNames(),
                                  reason -> badSetInRecord.add("librarynames:" + reason),
                                  false)
                              .orElse(""),
                          limsAttr(fp, "geo_library_size_code", badSetInRecord::add)
                              .map(IUSUtils::parseLong)
                              .orElse(0L),
                          limsAttr(fp, "geo_library_type", badSetInRecord::add).orElse(""),
                          new Tuple(
                              limsKey.map(LimsKey::getId).orElse(""),
                              limsKey.map(LimsKey::getProvider).orElse(""),
                              limsKey
                                  .map(LimsKey::getLastModified)
                                  .map(ZonedDateTime::toInstant)
                                  .orElse(Instant.EPOCH),
                              limsKey.map(LimsKey::getVersion).orElse("")),
                          fp.getFileMetaType(),
                          fp.getFileMd5sum(),
                          limsAttr(fp, "geo_organism", badSetInRecord::add).orElse(""),
                          Paths.get(fp.getFilePath()),
                          IUSUtils.singleton(
                                  fp.getStudyTitles(),
                                  reason -> badSetInRecord.add("study:" + reason),
                                  false)
                              .orElse(""),
                          limsAttr(fp, "reference_slide_id", badSetInRecord::add),
                          limsAttr(fp, "sequencing_control_type", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "sex", badSetInRecord::add),
                          limsAttr(fp, "spike_in", badSetInRecord::add),
                          limsAttr(fp, "spike_in_dilution_factor", badSetInRecord::add),
                          limsAttr(fp, "spike_in_volume_ul", badSetInRecord::add)
                              .map(Double::parseDouble),
                          fp.getStatus() == FileProvenance.Status.STALE,
                          limsAttr(fp, "subproject", badSetInRecord::add).filter(p -> !p.isBlank()),
                          limsAttr(fp, "target_cell_recovery", badSetInRecord::add)
                              .map(Double::parseDouble),
                          limsAttr(fp, "geo_targeted_resequencing", badSetInRecord::add).orElse(""),
                          fp.getLastModified().toInstant(),
                          IUSUtils.singleton(
                                  fp.getParentSampleNames(),
                                  reason -> badSetInRecord.add("parents:" + reason),
                                  false)
                              .map(IUSUtils::tissue)
                              .orElse(""),
                          limsAttr(fp, "geo_tissue_origin", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_preparation", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_region", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_type", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "umis", badSetInRecord::add)
                              .map("true"::equalsIgnoreCase)
                              .orElse(false),
                          fp.getWorkflowName(),
                          fp.getWorkflowSWID().toString(),
                          fp.getWorkflowAttributes(),
                          Optional.ofNullable(fp.getWorkflowRunSWID())
                              .map(Object::toString)
                              .orElse(""),
                          fp.getWorkflowRunAttributes(),
                          workflowVersion.orElse(IUSUtils.UNKNOWN_VERSION));

                  if (!badSetInRecord.isEmpty()) {
                    badSets.incrementAndGet();
                    badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, Integer::sum));
                    return null;
                  }
                  return badRecord.get() ? null : result;
                })
            .filter(Objects::nonNull)
            .onClose(
                () -> {
                  badFilePathError.labels(fileName().toString()).set(badFilePaths.get());
                  badSetError.labels(fileName().toString()).set(badSets.get());
                  badWorkflowVersions.labels(fileName().toString()).set(badVersions.get());
                  badSetCounts.forEach(
                      (key, value) ->
                          badSetMap
                              .labels(
                                  Stream.concat(
                                          Stream.of(fileName().toString()),
                                          COLON.splitAsStream(key))
                                      .toArray(String[]::new))
                              .set(value));
                });
      }
    }

    private class SkippedItemCache
        extends ValueCache<
            Stream<CerberusFileProvenanceSkippedValue>,
            Stream<CerberusFileProvenanceSkippedValue>> {
      private SkippedItemCache() {
        super(name + "-skipped " + fileName().toString(), 60, ReplacingRecord::new);
      }

      @Override
      protected Stream<CerberusFileProvenanceSkippedValue> fetch(Instant lastUpdated) {
        final AtomicInteger badFilePaths = new AtomicInteger();
        final AtomicInteger badSets = new AtomicInteger();
        final AtomicInteger badVersions = new AtomicInteger();
        final Map<String, Integer> badSetCounts = new TreeMap<>();
        return client
            .map(BaseProvenancePluginType.this::fetch)
            .orElseGet(Stream::empty)
            .filter(
                fp ->
                    (fp.getSkip() == null || fp.getSkip().equals("true"))
                        && fp.getStatus() != FileProvenance.Status.ERROR)
            .filter(
                fp -> {
                  if (fp.getFilePath() == null) {
                    badFilePaths.incrementAndGet();
                    return false;
                  }
                  return true;
                })
            .map(
                fp -> {
                  final AtomicReference<Boolean> badRecord = new AtomicReference<>(false);
                  final Set<String> badSetInRecord = new TreeSet<>();
                  final Optional<LimsKey> limsKey =
                      IUSUtils.singleton(
                              fp.getIusLimsKeys(),
                              reason -> badSetInRecord.add("limskey:" + reason),
                              true)
                          .map(IusLimsKey::getLimsKey);
                  final Optional<Tuple> workflowVersion =
                      IUSUtils.parseWorkflowVersion(fp.getWorkflowVersion());
                  if (!workflowVersion.isPresent()) {
                    badVersions.incrementAndGet();
                    badRecord.set(true);
                  }
                  final CerberusFileProvenanceSkippedValue result =
                      new PipeDevCerberusFileProvenanceSkippedValue(
                          fp.getFileSWID().toString(),
                          limsAttr(fp, "barcode_kit", badSetInRecord::add),
                          limsAttr(fp, "batches", badSetInRecord::add)
                              .<Set<String>>map(
                                  s ->
                                      COMMA
                                          .splitAsStream(s)
                                          .collect(Collectors.toCollection(TreeSet::new)))
                              .orElse(Set.of()),
                          limsAttr(fp, "cell_viability", badSetInRecord::add)
                              .map(Double::parseDouble),
                          fp.getLastModified().toInstant(),
                          IUSUtils.singleton(
                                  fp.getRootSampleNames(),
                                  reason -> badSetInRecord.add("samplenames:" + reason),
                                  false)
                              .orElse(""),
                          limsAttr(fp, "geo_external_name", badSetInRecord::add).orElse(""),
                          new Tuple(
                              limsKey.map(LimsKey::getId).orElse(""),
                              limsKey.map(LimsKey::getProvider).orElse(""),
                              fp.getStatus() == FileProvenance.Status.STALE,
                              Collections.singletonMap(
                                  "pinery-legacy", limsKey.map(LimsKey::getVersion).orElse(""))),
                          limsAttr(fp, "geo_tube_id", badSetInRecord::add).orElse(""),
                          fp.getFileAttributes(),
                          IUSUtils.parseLong(fp.getFileSize()),
                          limsAttr(fp, "geo_group_id_description", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_group_id", badSetInRecord::add).orElse(""),
                          IUSUtils.singleton(
                                  fp.getSequencerRunPlatformNames(),
                                  reason -> badSetInRecord.add("instrument_model: " + reason),
                                  true)
                              .orElse(""),
                          fp.getWorkflowRunInputFileSWIDs().stream()
                              .map(Object::toString)
                              .collect(Collectors.toSet()),
                          packIUS(fp),
                          limsAttr(fp, "geo_prep_kit", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_library_source_template_type", badSetInRecord::add)
                              .orElse(""),
                          IUSUtils.singleton(
                                  fp.getSampleNames(),
                                  reason -> badSetInRecord.add("librarynames:" + reason),
                                  false)
                              .orElse(""),
                          limsAttr(fp, "geo_library_size_code", badSetInRecord::add)
                              .map(IUSUtils::parseLong)
                              .orElse(0L),
                          limsAttr(fp, "geo_library_type", badSetInRecord::add).orElse(""),
                          new Tuple(
                              limsKey.map(LimsKey::getId).orElse(""),
                              limsKey.map(LimsKey::getProvider).orElse(""),
                              limsKey
                                  .map(LimsKey::getLastModified)
                                  .map(ZonedDateTime::toInstant)
                                  .orElse(Instant.EPOCH),
                              limsKey.map(LimsKey::getVersion).orElse("")),
                          fp.getFileMetaType(),
                          fp.getFileMd5sum(),
                          limsAttr(fp, "geo_organism", badSetInRecord::add).orElse(""),
                          Paths.get(fp.getFilePath()),
                          IUSUtils.singleton(
                                  fp.getStudyTitles(),
                                  reason -> badSetInRecord.add("study:" + reason),
                                  false)
                              .orElse(""),
                          limsAttr(fp, "reference_slide_id", badSetInRecord::add),
                          limsAttr(fp, "sequencing_control_type", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "sex", badSetInRecord::add),
                          limsAttr(fp, "spike_in", badSetInRecord::add),
                          limsAttr(fp, "spike_in_dilution_factor", badSetInRecord::add),
                          limsAttr(fp, "spike_in_volume_ul", badSetInRecord::add)
                              .map(Double::parseDouble),
                          limsAttr(fp, "subproject", badSetInRecord::add).filter(p -> !p.isBlank()),
                          limsAttr(fp, "target_cell_recovery", badSetInRecord::add)
                              .map(Double::parseDouble),
                          limsAttr(fp, "geo_targeted_resequencing", badSetInRecord::add).orElse(""),
                          fp.getLastModified().toInstant(),
                          IUSUtils.singleton(
                                  fp.getParentSampleNames(),
                                  reason -> badSetInRecord.add("parents:" + reason),
                                  false)
                              .map(IUSUtils::tissue)
                              .orElse(""),
                          limsAttr(fp, "geo_tissue_origin", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_preparation", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_region", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_type", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "umis", badSetInRecord::add)
                              .map("true"::equalsIgnoreCase)
                              .orElse(false),
                          fp.getWorkflowName(),
                          fp.getWorkflowSWID().toString(),
                          fp.getWorkflowAttributes(),
                          Optional.ofNullable(fp.getWorkflowRunSWID())
                              .map(Object::toString)
                              .orElse(""),
                          fp.getWorkflowRunAttributes(),
                          workflowVersion.orElse(IUSUtils.UNKNOWN_VERSION));

                  if (!badSetInRecord.isEmpty()) {
                    badSets.incrementAndGet();
                    badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, Integer::sum));
                    return null;
                  }
                  return badRecord.get() ? null : result;
                })
            .filter(Objects::nonNull)
            .onClose(
                () -> {
                  badFilePathError.labels(fileName().toString()).set(badFilePaths.get());
                  badSetError.labels(fileName().toString()).set(badSets.get());
                  badWorkflowVersions.labels(fileName().toString()).set(badVersions.get());
                  badSetCounts.forEach(
                      (key, value) ->
                          badSetMap
                              .labels(
                                  Stream.concat(
                                          Stream.of(fileName().toString()),
                                          COLON.splitAsStream(key))
                                      .toArray(String[]::new))
                              .set(value));
                });
      }
    }

    private final ItemCache cache;

    private Optional<C> client = Optional.empty();

    private boolean ok;

    private final SkippedItemCache skippedCache;

    public FileConfiguration(Path fileName, String instanceName) {
      super(fileName, instanceName);
      cache = new ItemCache();
      skippedCache = new SkippedItemCache();
    }

    @Override
    public void configuration(SectionRenderer renderer) {
      renderer.line("Configuration Good?", ok ? "Yes" : "No");
    }

    @Override
    public Stream<String> services() {
      return BaseProvenancePluginType.this.services();
    }

    @Override
    public void stop() {
      client.ifPresent(BaseProvenancePluginType::close);
    }

    @ShesmuInputSource
    public Stream<CerberusFileProvenanceValue> stream(boolean readStale) {
      return readStale ? cache.getStale() : cache.get();
    }

    @ShesmuInputSource
    public Stream<CerberusFileProvenanceSkippedValue> streamSkipped(boolean readStale) {
      return readStale ? skippedCache.getStale() : skippedCache.get();
    }

    @Override
    public Optional<Integer> update() {
      try {
        Optional<C> oldClient = client;
        this.client = Optional.of(createClient(fileName()));
        cache.invalidate();
        oldClient.ifPresent(BaseProvenancePluginType::close);
        ok = true;
      } catch (final Exception e) {
        e.printStackTrace();
        ok = false;
      }
      return Optional.empty();
    }
  }

  private static void close(AutoCloseable client) {
    try {
      client.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static Optional<String> limsAttr(FileProvenance fp, String key, Consumer<String> isBad) {
    return IUSUtils.singleton(
        fp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), false);
  }

  private static Tuple packIUS(FileProvenance fp) {
    final Iterator<String> runName = fp.getSequencerRunNames().iterator();
    final Iterator<String> laneName = fp.getLaneNames().iterator();
    final Iterator<String> tagName = fp.getIusTags().iterator();
    if (runName.hasNext() && laneName.hasNext() && tagName.hasNext()) {
      return new Tuple(runName.next(), IUSUtils.parseLaneNumber(laneName.next()), tagName.next());
    }
    return new Tuple("", 0L, "");
  }

  private static final Pattern COLON = Pattern.compile(":");
  private static final Pattern COMMA = Pattern.compile(",");
  public static final Map<FileProvenanceFilter, Set<String>> PROVENANCE_FILTER =
      new EnumMap<>(FileProvenanceFilter.class);
  public static final Map<FileProvenanceFilter, Set<String>> PROVENANCE_SKIPPED_FILTER =
      new EnumMap<>(FileProvenanceFilter.class);
  private static final Gauge badFilePathError =
      Gauge.build(
              "shesmu_file_provenance_bad_file_path",
              "The number of records where the file path was missing.")
          .labelNames("filename")
          .register();
  private static final Gauge badSetError =
      Gauge.build(
              "shesmu_file_provenance_bad_set_size",
              "The number of records where a set contained not exactly one item.")
          .labelNames("filename")
          .register();
  private static final Gauge badSetMap =
      Gauge.build(
              "shesmu_file_provenance_bad_set",
              "The number of provenace records with sets not containing exactly one item.")
          .labelNames("filename", "property", "reason")
          .register();
  private static final Gauge badWorkflowVersions =
      Gauge.build(
              "shesmu_file_provenance_bad_workflow",
              "The number of records with a bad workflow version (not x.y.z) was received from Provenance.")
          .labelNames("filename")
          .register();

  static {
    PROVENANCE_FILTER.put(FileProvenanceFilter.processing_status, Collections.singleton("success"));
    PROVENANCE_FILTER.put(
        FileProvenanceFilter.workflow_run_status, Collections.singleton("completed"));
    PROVENANCE_FILTER.put(FileProvenanceFilter.skip, Collections.singleton("false"));
  }

  static {
    PROVENANCE_SKIPPED_FILTER.put(
        FileProvenanceFilter.processing_status, Collections.singleton("success"));
    PROVENANCE_SKIPPED_FILTER.put(
        FileProvenanceFilter.workflow_run_status, Collections.singleton("completed"));
    PROVENANCE_SKIPPED_FILTER.put(FileProvenanceFilter.skip, Collections.singleton("true"));
  }

  private final String name;

  public BaseProvenancePluginType(String name, String extension, String namespace) {
    super(
        MethodHandles.lookup(),
        BaseProvenancePluginType.FileConfiguration.class,
        extension,
        namespace);
    this.name = name;
  }

  @Override
  public final FileConfiguration create(
      Path filePath,
      String instanceName,
      Definer<BaseProvenancePluginType.FileConfiguration> definer) {
    return new FileConfiguration(filePath, instanceName);
  }

  protected abstract C createClient(Path fileName) throws Exception;

  protected abstract Stream<? extends FileProvenance> fetch(C client);

  public abstract Stream<String> services();
}
