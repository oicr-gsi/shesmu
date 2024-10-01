package ca.on.oicr.gsi.shesmu.nabu;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.ErrorableStream;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuInputSource;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonListBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

public class NabuPlugin extends JsonPluginFile<NabuConfiguration> {

  private final class CaseArchiveCache extends ValueCache<Stream<NabuCaseArchiveValue>> {

    private CaseArchiveCache(Path fileName) {
      super("case_archive " + fileName.toString(), 30, ReplacingRecord::new);
    }

    private Stream<NabuCaseArchiveValue> caseArchives(String baseUrl)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              HttpRequest.newBuilder(URI.create(baseUrl + "/cases")).GET().build(),
              new JsonListBodyHandler<>(MAPPER, NabuCaseArchiveDto.class))
          .body()
          .get()
          .map(
              ca ->
                  new NabuCaseArchiveValue(
                      ca.getCaseFilesUnloaded() == null
                          ? Optional.empty()
                          : Optional.of(Instant.parse(ca.getCaseFilesUnloaded())),
                      ca.getCaseIdentifier(),
                      Optional.ofNullable(ca.getCommvaultBackupJobId()),
                      Instant.parse(ca.getCreated()),
                      ca.getFilesCopiedToOffsiteArchiveStagingDir() == null
                          ? Optional.empty()
                          : Optional.of(
                              Instant.parse(ca.getFilesCopiedToOffsiteArchiveStagingDir())),
                      ca.getFilesLoadedIntoVidarrArchival() == null
                          ? Optional.empty()
                          : Optional.of(Instant.parse(ca.getFilesLoadedIntoVidarrArchival())),
                      ca.getLimsIds(),
                      Instant.parse(ca.getModified()),
                      ca.getRequisitionId(),
                      ca.getWorkflowRunIdsForOffsiteArchive(),
                      ca.getWorkflowRunIdsForVidarrArchival() == null
                          ? Optional.empty()
                          : Optional.of(ca.getWorkflowRunIdsForVidarrArchival())));
    }

    @Override
    protected Stream<NabuCaseArchiveValue> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return new ErrorableStream<>(Stream.empty(), false);
      }
      return caseArchives(config.get().getUrl());
    }
  }

  private final class FileQcCache extends ValueCache<Stream<NabuFileQcValue>> {

    private FileQcCache(Path fileName) {
      super("file_qc " + fileName.toString(), 30, ReplacingRecord::new);
    }

    @Override
    protected Stream<NabuFileQcValue> fetch(Instant lastUpdated) throws Exception {
      if (config.isEmpty()) {
        return new ErrorableStream<>(Stream.empty(), false);
      }
      return fileQcs(config.get().getUrl());
    }

    private Stream<NabuFileQcValue> fileQcs(String baseUrl)
        throws IOException, InterruptedException {
      return HTTP_CLIENT
          .send(
              HttpRequest.newBuilder(URI.create(baseUrl + "/fileqcs-only")).GET().build(),
              new JsonListBodyHandler<>(MAPPER, NabuFileQcDto.class))
          .body()
          .get()
          .map(
              fqc -> {
                return new NabuFileQcValue(
                    fqc.getFileQcId(),
                    Optional.ofNullable(fqc.getComment()),
                    fqc.getFileId(),
                    fqc.getFilePath(),
                    Optional.ofNullable(fqc.getFileSwid()),
                    fqc.getProject(),
                    Instant.parse(fqc.getQcDate()),
                    Optional.ofNullable(fqc.getQcPassed()),
                    fqc.getWorkflow(),
                    fqc.getUsername());
              });
    }
  }

  static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
  static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  private final CaseArchiveCache caseArchiveCache;
  private Optional<NabuConfiguration> config = Optional.empty();
  private final Definer<NabuPlugin> definer;
  private final FileQcCache fileQcCache;
  private Optional<URI> url = Optional.empty();

  public NabuPlugin(Path fileName, String instanceName, Definer<NabuPlugin> definer) {
    super(fileName, instanceName, MAPPER, NabuConfiguration.class);
    this.definer = definer;
    fileQcCache = new FileQcCache(fileName);
    caseArchiveCache = new CaseArchiveCache(fileName);
  }

  @ShesmuAction(
      description = "send archiving info for case to Nabu (completes when files archived)")
  public ArchiveCaseAction archive() {
    return new ArchiveCaseAction(definer);
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    final Optional<String> u = config.map(NabuConfiguration::getUrl);
    u.ifPresent(uri -> renderer.link("URL", uri.toString(), uri.toString()));
  }

  @ShesmuInputSource
  public Stream<NabuCaseArchiveValue> streamCaseArchives(boolean readStale) {
    return readStale ? caseArchiveCache.getStale() : caseArchiveCache.get();
  }

  @ShesmuInputSource
  public Stream<NabuFileQcValue> streamFileQcs(boolean readStale) {
    return readStale ? fileQcCache.getStale() : fileQcCache.get();
  }

  @Override
  protected Optional<Integer> update(NabuConfiguration value) {
    config = Optional.of(value);
    fileQcCache.invalidate();
    caseArchiveCache.invalidate();
    return Optional.empty();
  }

  public String NabuUrl() {
    return config.get().getUrl();
  }

  public String NabuToken() throws RuntimeException {
    try {
      return config.get().getAuthentication().prepareAuthentication();
    } catch (IOException e) {
      throw new RuntimeException("Unable to get authentication method");
    }
  }
}
