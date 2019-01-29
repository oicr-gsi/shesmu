package ca.on.oicr.gsi.shesmu.pinery;

import ca.on.oicr.gsi.provenance.model.SampleProvenance;
import ca.on.oicr.gsi.shesmu.gsistd.input.IUSUtils;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.cache.MergingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import ca.on.oicr.pinery.client.HttpResponseException;
import ca.on.oicr.pinery.client.PineryClient;
import ca.on.oicr.ws.dto.RunDto;
import ca.on.oicr.ws.dto.SampleProjectDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public class PinerySource extends JsonPluginFile<PineryConfiguration> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private class ProjectCache extends ValueCache<Stream<SampleProjectDto>> {

    public ProjectCache(Path fileName) {
      super(
          "pinery_projects " + fileName.toString(),
          3600,
          MergingRecord.by(SampleProjectDto::getName));
    }

    @Override
    protected Stream<SampleProjectDto> fetch(Instant lastUpdated) throws Exception {
      if (!config.isPresent()) return Stream.empty();
      try (final PineryClient client = new PineryClient(config.get().getUrl())) {
        return client.getSampleProject().all().stream();
      }
    }
  };

  private final class ItemCache extends ValueCache<Stream<PineryIUSValue>> {
    private ItemCache(Path fileName) {
      super("pinery " + fileName.toString(), 30, ReplacingRecord::new);
    }

    @Override
    protected Stream<PineryIUSValue> fetch(Instant lastUpdated) throws Exception {
      if (!config.isPresent()) {
        return Stream.empty();
      }
      final PineryConfiguration cfg = config.get();
      try (PineryClient c = new PineryClient(cfg.getUrl(), true)) {
        final String version = cfg.getVersion() == null ? "v1" : cfg.getVersion();
        final Map<String, Integer> badSetCounts = new TreeMap<>();
        final Map<String, String> runDirectories = new HashMap<>();
        final Set<String> completeRuns =
            c.getSequencerRun()
                .all()
                .stream() //
                .filter(
                    run ->
                        run.getState().equals("Completed")
                            && run.getRunDirectory() != null
                            && !run.getRunDirectory().equals("")) //
                .map(RunDto::getName) //
                .collect(Collectors.toSet());
        return Stream.concat( //
                lanes(
                    c,
                    version,
                    cfg.getProvider(),
                    badSetCounts,
                    runDirectories,
                    completeRuns::contains), //
                samples(
                    c,
                    version,
                    cfg.getProvider(),
                    badSetCounts,
                    runDirectories,
                    completeRuns::contains)) //
            .onClose(
                () ->
                    badSetCounts
                        .entrySet()
                        .forEach(
                            e ->
                                badSetMap
                                    .labels(
                                        Stream.concat(
                                                Stream.of(fileName().toString()),
                                                Stream.of(e.getKey().split(":")))
                                            .toArray(String[]::new))
                                    .set(e.getValue())));
      }
    }

    private Stream<PineryIUSValue> lanes(
        PineryClient client,
        String version,
        String provider,
        Map<String, Integer> badSetCounts,
        Map<String, String> runDirectories,
        Predicate<String> goodRun)
        throws HttpResponseException {
      return Utils.stream(client.getLaneProvenance().version(version)) //
          .filter(lp -> goodRun.test(lp.getSequencerRunName())) //
          .filter(lp -> lp.getSkip() == null || !lp.getSkip()) //
          .map(
              lp -> {
                final Set<String> badSetInRecord = new TreeSet<>();
                final String runDirectory =
                    IUSUtils.singleton(
                            lp.getSequencerRunAttributes().get("run_dir"),
                            reason -> badSetInRecord.add("run_dir:" + reason),
                            true)
                        .orElse("");

                runDirectories.put(lp.getSequencerRunName(), runDirectory);
                final PineryIUSValue result =
                    new PineryIUSValue( //
                        Paths.get(runDirectory), //
                        "", //
                        "", //
                        "", //
                        new Tuple(
                            lp.getSequencerRunName(),
                            IUSUtils.parseLaneNumber(lp.getLaneNumber()),
                            "NoIndex"), //
                        "", //
                        "", //
                        "", //
                        "", //
                        "", //
                        "", //
                        "", //
                        "", //
                        0L, //
                        "", //
                        "", //
                        lp.getLastModified() == null
                            ? Instant.EPOCH
                            : lp.getLastModified().toInstant(), //
                        new Tuple(lp.getLaneProvenanceId(), lp.getVersion(), provider), //
                        lp.getCreatedDate() == null
                            ? Instant.EPOCH
                            : lp.getCreatedDate().toInstant(), //
                        false);

                if (badSetInRecord.isEmpty()) {
                  return result;
                } else {
                  badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
                  return null;
                }
              }) //
          .filter(Objects::nonNull);
    }

    private Stream<PineryIUSValue> samples(
        PineryClient client,
        String version,
        String provider,
        Map<String, Integer> badSetCounts,
        Map<String, String> runDirectories,
        Predicate<String> goodRun)
        throws HttpResponseException {
      return Utils.stream(client.getSampleProvenance().version(version)) //
          .filter(sp -> goodRun.test(sp.getSequencerRunName())) //
          .map(
              sp -> {
                final String runDirectory = runDirectories.get(sp.getSequencerRunName());
                if (runDirectory == null) {
                  return null;
                }
                final Set<String> badSetInRecord = new TreeSet<>();
                final PineryIUSValue result =
                    new PineryIUSValue( //
                        Paths.get(runDirectory), //
                        sp.getStudyTitle(), //
                        sp.getSampleName(), //
                        sp.getRootSampleName(), //
                        new Tuple(
                            sp.getSequencerRunName(),
                            IUSUtils.parseLaneNumber(sp.getLaneNumber()),
                            sp.getIusTag()), //
                        limsAttr(sp, "geo_library_source_template_type", badSetInRecord::add, true)
                            .orElse(""), //
                        limsAttr(sp, "geo_tissue_type", badSetInRecord::add, true).orElse(""), //
                        limsAttr(sp, "geo_tissue_origin", badSetInRecord::add, true).orElse(""), //
                        limsAttr(sp, "geo_tissue_preparation", badSetInRecord::add, false)
                            .orElse(""), //
                        limsAttr(sp, "geo_targeted_resequencing", badSetInRecord::add, false)
                            .orElse(""), //
                        limsAttr(sp, "geo_tissue_region", badSetInRecord::add, false)
                            .orElse(""), //
                        limsAttr(sp, "geo_group_id", badSetInRecord::add, false).orElse(""), //
                        limsAttr(sp, "geo_group_id_description", badSetInRecord::add, false)
                            .orElse(""), //
                        limsAttr(sp, "geo_library_size_code", badSetInRecord::add, false)
                            .map(IUSUtils::parseLong)
                            .orElse(0L), //
                        limsAttr(sp, "geo_library_type", badSetInRecord::add, false).orElse(""), //
                        limsAttr(sp, "geo_prep_kit", badSetInRecord::add, false).orElse(""), //
                        sp.getLastModified() == null
                            ? Instant.EPOCH
                            : sp.getLastModified().toInstant(), //
                        new Tuple(sp.getSampleProvenanceId(), sp.getVersion(), provider), //
                        sp.getCreatedDate() == null
                            ? Instant.EPOCH
                            : sp.getCreatedDate().toInstant(), //
                        true);

                if (badSetInRecord.isEmpty()) {
                  return result;
                } else {
                  badSetInRecord.forEach(name -> badSetCounts.merge(name, 1, (a, b) -> a + b));
                  return null;
                }
              }) //
          .filter(Objects::nonNull);
    }

    @ShesmuInputSource
    public Stream<PineryIUSValue> stream() {
      return cache.get();
    }
  }

  private static Optional<String> limsAttr(
      SampleProvenance sp, String key, Consumer<String> isBad, boolean required) {
    return IUSUtils.singleton(
        sp.getSampleAttributes().get(key), reason -> isBad.accept(key + ":" + reason), required);
  }

  private static final Gauge badSetMap =
      Gauge.build(
              "shesmu_pinery_bad_set",
              "The number of provenace records with sets not containing exactly one item.")
          .labelNames("target", "property", "reason")
          .register();

  private Optional<PineryConfiguration> config = Optional.empty();

  private final ProjectCache projects;
  private final ItemCache cache;

  public PinerySource(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, PineryConfiguration.class);
    projects = new ProjectCache(fileName);
    cache = new ItemCache(fileName);
  }

  @ShesmuMethod(
      name = "$_active_projects",
      type = "as",
      description = "Projects marked active from in Pinery defined in {file}.")
  public Set<String> activeProjects() {
    return projects
        .get() //
        .filter(SampleProjectDto::isActive) //
        .map(SampleProjectDto::getName) //
        .collect(Collectors.toSet());
  }

  @ShesmuMethod(
      name = "$_projects",
      type = "as",
      description = "All projects from in Pinery defined in {file}.")
  public Set<String> allProjects() {
    return projects
        .get() //
        .map(SampleProjectDto::getName) //
        .collect(Collectors.toSet());
  }

  @Override
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    final Optional<String> url = config.map(PineryConfiguration::getUrl);
    renderer.link("URL", url.orElse("about:blank"), url.orElse("Unknown"));
    renderer.line("Provider", config.map(PineryConfiguration::getProvider).orElse("Unknown"));
  }

  @Override
  protected Optional<Integer> update(PineryConfiguration value) {
    config = Optional.of(value);
    projects.invalidate();
    cache.invalidate();
    return Optional.empty();
  }
}
