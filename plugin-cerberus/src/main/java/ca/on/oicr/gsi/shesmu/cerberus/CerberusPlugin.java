package ca.on.oicr.gsi.shesmu.cerberus;

import ca.on.oicr.gsi.cerberus.JoinSource;
import ca.on.oicr.gsi.cerberus.fileprovenance.FileProvenanceConsumer;
import ca.on.oicr.gsi.cerberus.fileprovenance.ProvenanceRecord;
import ca.on.oicr.gsi.cerberus.pinery.LimsProvenanceInfo;
import ca.on.oicr.gsi.cerberus.pinery.PineryProvenanceSource;
import ca.on.oicr.gsi.cerberus.vidarr.VidarrWorkflowRunSource;
import ca.on.oicr.gsi.provenance.model.LimsProvenance;
import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceSkippedValue;
import ca.on.oicr.gsi.shesmu.gsicommon.CerberusFileProvenanceValue;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.gsi.vidarr.api.ExternalKey;
import ca.on.oicr.gsi.vidarr.api.ProvenanceWorkflowRun;
import ca.on.oicr.ws.dto.LaneProvenanceDto;
import ca.on.oicr.ws.dto.SampleProvenanceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CerberusPlugin extends JsonPluginFile<Configuration> {
  private class FileProvenanceCache
      extends ValueCache<Optional<FileProvenanceOutput>, Optional<FileProvenanceOutput>> {

    public FileProvenanceCache(String name) {
      super("cerberus-fpr " + name, 10, SimpleRecord::new);
    }

    @Override
    protected Optional<FileProvenanceOutput> fetch(Instant lastUpdated) {
      final List<CerberusFileProvenanceValue> output = new ArrayList<>();
      final List<CerberusErrorValue> errors = new ArrayList<>();
      final List<CerberusFileProvenanceSkippedValue> output_skipped = new ArrayList<>();
      final var staleCount = new AtomicInteger();
      try {
        JoinSource.join(
            vidarrData,
            limsData,
            VidarrWorkflowRunSource::key,
            LimsProvenanceInfo::key,
            FileProvenanceConsumer.of(
                new FileProvenanceConsumer() {
                  @Override
                  public void error(
                      ProvenanceWorkflowRun<ExternalKey> vidarrWorkflow,
                      Stream<LimsProvenanceInfo> stream) {
                    errors.add(new CerberusErrorValue(vidarrWorkflow, stream));
                  }

                  @Override
                  public void file(
                      boolean stale,
                      boolean skip,
                      ProvenanceRecord<LimsProvenance> provenanceRecord) {
                    if (!provenanceRecord.asSubtype(
                            SampleProvenanceDto.class,
                            r -> {
                              if (skip) {
                                output_skipped.add(
                                    new SampleCerberusFileProvenanceSkippedRecord(stale, r));
                              } else {
                                output.add(new SampleCerberusFileProvenanceRecord(stale, r));
                              }
                            })
                        && !provenanceRecord.asSubtype(
                            LaneProvenanceDto.class,
                            r -> {
                              if (skip) {
                                output_skipped.add(
                                    new LaneCerberusFileProvenanceSkippedRecord(stale, r));
                              } else {
                                output.add(new LaneCerberusFileProvenanceRecord(stale, r));
                              }
                            })) {
                      throw new IllegalArgumentException(
                          provenanceRecord.lims().getClass()
                              + " is neither lane or sample provenance.");
                    }
                    if (stale) {
                      staleCount.incrementAndGet();
                    }
                  }
                }));
      } catch (IllegalArgumentException e) {
        throw e;
      } catch (Exception e) {
        return Optional.of(
            new FileProvenanceOutput(
                fileProvenance().collect(Collectors.toList()),
                errors,
                fileProvenanceSkipped().collect(Collectors.toList())));
      }
      errorRecords.labels(fileName().toString()).set(errors.size());
      staleRecords.labels(fileName().toString()).set(staleCount.get());
      goodRecords
          .labels(fileName().toString())
          .set(
              output.size()
                  - staleCount.get()); // Failed samples shouldn't get counted as good records
      return Optional.of(new FileProvenanceOutput(output, errors, output_skipped));
    }
  }

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Gauge errorRecords =
      Gauge.build("shesmu_cerberus_error_records", "The number of Workflows missing LIMS data")
          .labelNames("filename")
          .register();
  private static final Gauge goodRecords =
      Gauge.build(
              "shesmu_cerberus_good_records",
              "The number of good (non-stale) file provenance records")
          .labelNames("filename")
          .register();
  private static final Gauge staleRecords =
      Gauge.build(
              "shesmu_cerberus_stale_records",
              "The number of file provenance records joined against mismatching LIM data")
          .labelNames("filename")
          .register();
  private final FileProvenanceCache cache;
  private JoinSource<LimsProvenanceInfo> limsData = JoinSource.empty();
  private JoinSource<ProvenanceWorkflowRun<ExternalKey>> vidarrData = JoinSource.empty();

  public CerberusPlugin(Path filePath, String instanceName, Definer<CerberusPlugin> definer) {
    super(filePath, instanceName, MAPPER, Configuration.class);
    cache = new FileProvenanceCache(instanceName);
  }

  @Override
  public void configuration(SectionRenderer renderer) {}

  @ShesmuInputSource
  public Stream<CerberusErrorValue> errors() {
    return cache.get().stream().flatMap(FileProvenanceOutput::errors);
  }

  @ShesmuInputSource
  public Stream<CerberusFileProvenanceValue> fileProvenance() {
    return cache.get().stream().flatMap(FileProvenanceOutput::fileProvenance);
  }

  @ShesmuInputSource
  public Stream<CerberusFileProvenanceSkippedValue> fileProvenanceSkipped() {
    return cache.get().stream().flatMap(FileProvenanceOutput::fileProvenanceSkipped);
  }

  @Override
  public Stream<String> services() {
    return Stream.of("cerberus");
  }

  @Override
  protected Optional<Integer> update(Configuration configuration) {
    limsData =
        JoinSource.all(
            configuration.getPinery().entrySet().stream()
                .flatMap(
                    entry ->
                        Stream.of(
                            PineryProvenanceSource.lanes(
                                entry.getKey(),
                                entry.getValue().getUrl(),
                                entry.getValue().getVersions()),
                            PineryProvenanceSource.samples(
                                entry.getKey(),
                                entry.getValue().getUrl(),
                                entry.getValue().getVersions()))));
    final var versions =
        configuration.getPinery().values().stream()
            .flatMap(pc -> pc.getVersions().stream())
            .map(v -> "pinery-hash-" + v)
            .collect(Collectors.toSet());
    vidarrData =
        JoinSource.all(
            configuration.getVidarr().entrySet().stream()
                .map(e -> VidarrWorkflowRunSource.of(e.getKey(), e.getValue(), versions)));
    return Optional.empty();
  }
}
