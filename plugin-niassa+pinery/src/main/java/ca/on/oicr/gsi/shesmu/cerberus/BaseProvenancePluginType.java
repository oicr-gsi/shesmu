package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.provenance.FileProvenanceFilter;
import ca.on.oicr.gsi.provenance.model.FileProvenance;
import ca.on.oicr.gsi.provenance.model.IusLimsKey;
import ca.on.oicr.gsi.provenance.model.LimsKey;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public abstract class BaseProvenancePluginType<C extends AutoCloseable>
    extends PluginFileType<BaseProvenancePluginType.FileConfiguration> {
  class FileConfiguration extends PluginFile {
    private class ItemCache extends ValueCache<Stream<CerberusFileProvenanceValue>> {

      public ItemCache() {
        super(name + " " + fileName().toString(), 60, ReplacingRecord::new);
      }

      @Override
      protected Stream<CerberusFileProvenanceValue> fetch(Instant lastUpdated) throws Exception {
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
                  final CerberusFileProvenanceValue result =
                      new CerberusFileProvenanceValue(
                          fp.getFileSWID().toString(),
                          limsAttr(fp, "cell_viability", badSetInRecord::add)
                              .map(Double::parseDouble),
                          fp.getLastModified().toInstant(),
                          IUSUtils.singleton(
                                  fp.getRootSampleNames(),
                                  reason -> badSetInRecord.add("samplenames:" + reason),
                                  false)
                              .orElse(""),
                          limsAttr(fp, "geo_external_name", badSetInRecord::add).orElse(""),
                          fp.getFileAttributes(),
                          IUSUtils.parseLong(fp.getFileSize()),
                          limsAttr(fp, "geo_group_id_description", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_group_id", badSetInRecord::add).orElse(""),
                          IUSUtils.singleton(
                                  fp.getSequencerRunPlatformNames(),
                                  reason -> badSetInRecord.add("instrument_model: " + reason),
                                  true)
                              .orElse(""),
                          fp.getWorkflowRunInputFileSWIDs()
                              .stream()
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
                          limsAttr(fp, "sex", badSetInRecord::add),
                          limsAttr(fp, "spike_in", badSetInRecord::add),
                          limsAttr(fp, "spike_in_dilution_factor", badSetInRecord::add),
                          limsAttr(fp, "spike_in_volume_ul", badSetInRecord::add)
                              .map(Double::parseDouble),
                          fp.getStatus() == FileProvenance.Status.STALE,
                          limsAttr(fp, "target_cell_recovery", badSetInRecord::add)
                              .map(Double::parseDouble),
                          limsAttr(fp, "geo_targeted_resequencing", badSetInRecord::add).orElse(""),
                          fp.getLastModified().toInstant(),
                          limsAttr(fp, "geo_tissue_origin", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_preparation", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_region", badSetInRecord::add).orElse(""),
                          limsAttr(fp, "geo_tissue_type", badSetInRecord::add).orElse(""),
                          fp.getWorkflowName(),
                          fp.getWorkflowSWID().toString(),
                          fp.getWorkflowAttributes(),
                          Optional.ofNullable(fp.getWorkflowRunSWID())
                              .map(Object::toString)
                              .orElse(""),
                          fp.getWorkflowRunAttributes(),
                          parseWorkflowVersion(
                              fp.getWorkflowVersion(),
                              () -> {
                                badVersions.incrementAndGet();
                                badRecord.set(true);
                              }));

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

    public FileConfiguration(Path fileName, String instanceName) {
      super(fileName, instanceName);
      cache = new ItemCache();
    }

    @Override
    public void configuration(SectionRenderer renderer) throws XMLStreamException {
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

  public static Tuple parseWorkflowVersion(String input, Runnable isBad) {
    if (input != null) {
      final Matcher m3 = WORKFLOW_VERSION3.matcher(input);
      if (m3.matches()) {
        return new Tuple(
            Long.parseLong(m3.group(1)), Long.parseLong(m3.group(2)), Long.parseLong(m3.group(3)));
      }
      final Matcher m2 = WORKFLOW_VERSION2.matcher(input);
      if (m2.matches()) {
        return new Tuple(Long.parseLong(m2.group(1)), Long.parseLong(m2.group(2)), 0L);
      }
    }
    isBad.run();
    return new Tuple(0L, 0L, 0L);
  }

  private static final Pattern COLON = Pattern.compile(":");
  public static final Map<FileProvenanceFilter, Set<String>> PROVENANCE_FILTER =
      new EnumMap<>(FileProvenanceFilter.class);
  private static final Pattern WORKFLOW_VERSION2 = Pattern.compile("^(\\d+)\\.(\\d+)$");
  private static final Pattern WORKFLOW_VERSION3 = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)$");
  private static final Gauge badSetError =
      Gauge.build(
              "shesmu_file_provenance_bad_set_size",
              "The number of records where a set contained not exactly one item.")
          .labelNames("filename")
          .register();
  private static final Gauge badFilePathError =
      Gauge.build(
              "shesmu_file_provenance_bad_file_path",
              "The number of records where the file path was missing.")
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

  private final String name;

  public BaseProvenancePluginType(String name, String extension) {
    super(MethodHandles.lookup(), BaseProvenancePluginType.FileConfiguration.class, extension);
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
