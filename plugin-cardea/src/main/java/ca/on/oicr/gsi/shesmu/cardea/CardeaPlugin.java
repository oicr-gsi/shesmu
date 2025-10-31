package ca.on.oicr.gsi.shesmu.cardea;

import static ca.on.oicr.gsi.shesmu.plugin.Utils.httpGet;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.ErrorableStream;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonListBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class CardeaPlugin extends JsonPluginFile<CardeaConfiguration> {

  private Optional<CardeaConfiguration> config = Optional.empty();
  private final Definer<CardeaPlugin> definer;
  private final CaseDeliverablesCache caseDeliverablesCache;
  private final CaseDetailedSummaryCache caseDetailedSummaryCache;
  private final CaseSequencingTestCache caseSequencingTestCache;
  private final CaseSummaryCache caseSummaryCache;

  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  public CardeaPlugin(Path fileName, String instanceName, Definer<CardeaPlugin> definer) {
    super(fileName, instanceName, MAPPER, CardeaConfiguration.class);
    this.definer = definer;
    caseDeliverablesCache = new CaseDeliverablesCache(fileName);
    caseDetailedSummaryCache = new CaseDetailedSummaryCache(fileName);
    caseSequencingTestCache = new CaseSequencingTestCache(fileName);
    caseSummaryCache = new CaseSummaryCache(fileName);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    config.map(CardeaConfiguration::getUrl).ifPresent(uri -> renderer.link("URL", uri, uri));
  }

  @Override
  protected Optional<Integer> update(CardeaConfiguration value) {
    config = Optional.of(value);
    caseDeliverablesCache.invalidate();
    caseDetailedSummaryCache.invalidate();
    caseSequencingTestCache.invalidate();
    caseSummaryCache.invalidate();
    return Optional.empty();
  }

  private final class CaseDeliverablesCache extends ValueCache<Stream<DeliverableValue>> {
    private CaseDeliverablesCache(Path fileName) {
      super("case_deliverables " + fileName.toString(), 30, ReplacingRecord::new);
    }

    private Stream<DeliverableValue> deliverables(String baseUrl)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              httpGet(
                  baseUrl + "/shesmu-detailed-cases", config.map(CardeaConfiguration::getTimeout)),
              new JsonListBodyHandler<>(MAPPER, CaseDetailedSummaryDto.class))
          .body()
          .get()
          .flatMap(
              cds -> {
                if (cds.getDeliverables() == null || cds.getDeliverables().isEmpty()) {
                  // project's deliverables are not configured
                  return null;
                } else {
                  return cds.getDeliverables().stream()
                      .flatMap(
                          d -> {
                            if (d.getReleases() == null || d.getReleases().isEmpty()) {
                              return null;
                            } else {
                              return d.getReleases().stream()
                                  .map(
                                      r ->
                                          new DeliverableValue(
                                              cds.getCaseIdentifier(),
                                              d.getDeliverableCategory(),
                                              d.isAnalysisReviewSkipped(),
                                              d.getAnalysisReviewQcDate(),
                                              d.getAnalysisReviewQcStatus(),
                                              d.getAnalysisReviewQcUser(),
                                              d.getReleaseApprovalQcDate(),
                                              d.getReleaseApprovalQcStatus(),
                                              d.getReleaseApprovalQcUser(),
                                              r.getDeliverable(),
                                              r.getQcDate(),
                                              r.getQcStatus(),
                                              r.getQcUser()));
                            }
                          });
                }
              })
          .filter(Objects::nonNull);
    }

    @Override
    protected Stream<DeliverableValue> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return new ErrorableStream<>(Stream.empty(), false);
      }
      return deliverables(config.get().getUrl());
    }
  }

  private final class CaseDetailedSummaryCache
      extends ValueCache<Stream<CaseDetailedSummaryValue>> {

    private CaseDetailedSummaryCache(Path fileName) {
      super("case_detailed_summary " + fileName.toString(), 30, ReplacingRecord::new);
    }

    private Stream<CaseDetailedSummaryValue> caseDetailedSummary(String baseUrl)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              httpGet(
                  baseUrl + "/shesmu-detailed-cases", config.map(CardeaConfiguration::getTimeout)),
              new JsonListBodyHandler<>(MAPPER, CaseDetailedSummaryDto.class))
          .body()
          .get()
          .map(
              cds ->
                  new CaseDetailedSummaryValue(
                      cds.getAssayName(),
                      cds.getAssayVersion(),
                      cds.getCaseIdentifier(),
                      cds.getCaseStatus(),
                      cds.getCompletedDate(),
                      cds.getClinicalCompletedDate(),
                      cds.getRequisitionId(),
                      cds.getRequisitionName(),
                      cds.getSequencing().stream(),
                      cds.isStopped(),
                      cds.isPaused()));
    }

    @Override
    protected Stream<CaseDetailedSummaryValue> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return new ErrorableStream<>(Stream.empty(), false);
      }
      return caseDetailedSummary(config.get().getUrl());
    }
  }

  private final class CaseSummaryCache extends ValueCache<Stream<CaseSummaryValue>> {

    private CaseSummaryCache(Path fileName) {
      super("case_summary " + fileName.toString(), 30, ReplacingRecord::new);
    }

    private Stream<CaseSummaryValue> caseSummary(String baseUrl)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              httpGet(baseUrl + "/shesmu-cases", config.map(CardeaConfiguration::getTimeout)),
              new JsonListBodyHandler<>(MAPPER, CaseSummaryDto.class))
          .body()
          .get()
          .map(
              cs ->
                  new CaseSummaryValue(
                      cs.getAssayName(),
                      cs.getAssayVersion(),
                      cs.getCaseIdentifier(),
                      cs.getCaseStatus(),
                      cs.getCompletedDate(),
                      cs.getLimsIds(),
                      cs.getRequisitionId(),
                      cs.getRequisitionName()));
    }

    @Override
    protected Stream<CaseSummaryValue> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return new ErrorableStream<>(Stream.empty(), false);
      }
      return caseSummary(config.get().getUrl());
    }
  }

  private final class CaseSequencingTestCache extends ValueCache<Stream<SequencingTestValue>> {
    private CaseSequencingTestCache(Path fileName) {
      super("case_sequencing_test " + fileName.toString(), 30, ReplacingRecord::new);
    }

    private Stream<SequencingTestValue> sequencingTest(String baseUrl)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              httpGet(
                  baseUrl + "/shesmu-detailed-cases", config.map(CardeaConfiguration::getTimeout)),
              new JsonListBodyHandler<>(MAPPER, CaseDetailedSummaryDto.class))
          .body()
          .get()
          .flatMap(
              cds -> {
                if (cds.getSequencing() == null || cds.getSequencing().isEmpty()) {
                  // no sequencing yet
                  return null;
                } else {
                  return cds.getSequencing().stream()
                      .map(
                          st ->
                              new SequencingTestValue(
                                  cds.getCaseIdentifier(),
                                  st.getTest(),
                                  st.getType(),
                                  st.isComplete(),
                                  st.getLimsIds()));
                }
              });
    }

    @Override
    protected Stream<SequencingTestValue> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return new ErrorableStream<>(Stream.empty(), false);
      }
      return sequencingTest(config.get().getUrl());
    }
  }

  @ShesmuInputSource
  public Stream<DeliverableValue> streamCaseDeliverableValues(boolean readStale) {
    return readStale ? caseDeliverablesCache.getStale() : caseDeliverablesCache.get();
  }

  @ShesmuInputSource
  public Stream<CaseDetailedSummaryValue> streamCaseDetailedSummaryValues(boolean readStale) {
    return readStale ? caseDetailedSummaryCache.getStale() : caseDetailedSummaryCache.get();
  }

  @ShesmuInputSource
  public Stream<SequencingTestValue> streamCaseSequencingTestValues(boolean readStale) {
    return readStale ? caseSequencingTestCache.getStale() : caseSequencingTestCache.get();
  }

  @ShesmuInputSource
  public Stream<CaseSummaryValue> streamCaseSummaryValues(boolean readStale) {
    return readStale ? caseSummaryCache.getStale() : caseSummaryCache.get();
  }
}
