package ca.on.oicr.gsi.shesmu.runscanner;

import ca.on.oicr.gsi.runscanner.dto.IlluminaNotificationDto;
import ca.on.oicr.gsi.runscanner.dto.NotificationDto;
import ca.on.oicr.gsi.shesmu.plugin.cache.InitialCachePopulationException;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.SimpleRecord;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import javax.xml.stream.XMLStreamException;
import org.apache.commons.codec.net.URLCodec;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

public final class RunScannerClient extends JsonPluginFile<Configuration> {
  static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    MAPPER.registerModule(new JavaTimeModule());
  }

  private class RunCache extends KeyValueCache<String, Optional<NotificationDto>> {

    public RunCache(Path fileName) {
      super("runscanner " + fileName.toString(), 30, SimpleRecord::new);
    }

    @Override
    protected Optional<NotificationDto> fetch(String runName, Instant lastUpdated)
        throws Exception {
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
        run.setMetrics(null); // Discard metrics to save memory
        return Optional.of(run);
      }
    }
  }

  private final RunCache runCache;
  private Optional<String> url = Optional.empty();

  public RunScannerClient(Path fileName, String instanceName) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    runCache = new RunCache(fileName);
  }

  @ShesmuMethod(
      description =
          "Get the serial number of the flowcell detected by the Run Scanner defined in {file}.")
  public String $_flowcell(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache.get(runName).map(NotificationDto::getContainerSerialNumber).orElse("");
    } catch (InitialCachePopulationException e) {
      return "";
    }
  }

  @ShesmuMethod(
      description = "Get the number of lanes detected by the Run Scanner defined in {file}.")
  public long $_lane_count(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache.get(runName).map(r -> (long) r.getLaneCount()).orElse(-1L);
    } catch (InitialCachePopulationException e) {
      return -1;
    }
  }

  @ShesmuMethod(
      description = "Get the number of reads detected by the Run Scanner defined in {file}.")
  public long $_read_ends(@ShesmuParameter(description = "name of run") String runName) {
    try {
      return runCache
          .get(runName)
          .filter(IlluminaNotificationDto.class::isInstance)
          .map(r -> (long) ((IlluminaNotificationDto) r).getNumReads())
          .orElse(-1L);
    } catch (InitialCachePopulationException e) {
      return -1;
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
