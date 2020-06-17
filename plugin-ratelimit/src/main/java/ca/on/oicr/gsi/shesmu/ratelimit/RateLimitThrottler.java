package ca.on.oicr.gsi.shesmu.ratelimit;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.PluginFileType;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.prometheus.client.Gauge;
import java.lang.invoke.MethodHandles;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public class RateLimitThrottler extends PluginFileType<RateLimitThrottler.TokenBucket> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  class TokenBucket extends JsonPluginFile<RateLimitConfiguration> {

    private int capacity;

    private int delay = 10_000;
    private Instant lastTime = Instant.now();
    private long tokens;

    public TokenBucket(Path fileName, String instanceName) {
      super(fileName, instanceName, MAPPER, RateLimitConfiguration.class);
    }

    public void configuration(SectionRenderer renderer) throws XMLStreamException {
      renderer.line("Capacity", Integer.toString(capacity));
      renderer.line("Regeneration delay (ms)", Integer.toString(delay));
    }

    @Override
    public synchronized Stream<String> isOverloaded(Set<String> services) {
      if (!services.contains(name())) {
        return Stream.empty();
      }
      final long newTokens = Duration.between(lastTime, Instant.now()).toMillis() / delay;
      lastTime = lastTime.plusMillis(delay * newTokens);
      tokens = Math.min(tokens + newTokens, capacity);
      tokenCount.labels(name()).set(tokens);
      if (tokens < 1) return Stream.of(name());
      tokens--;
      return Stream.empty();
    }

    @Override
    protected synchronized Optional<Integer> update(RateLimitConfiguration value) {
      capacity = Math.max(0, value.getCapacity());
      delay = Math.max(1, value.getDelay());
      tokens = Math.min(tokens, capacity);
      bucketCapacity.labels(name()).set(capacity);
      rechargeDelay.labels(name()).set(delay);
      return Optional.empty();
    }
  }

  private static final Gauge bucketCapacity =
      Gauge.build(
              "shesmu_throttler_ratelimit_capacity", "The maximum number of tokens in the bucket.")
          .labelNames("service")
          .register();
  private static final String EXTENSION = ".ratelimit";
  private static final Gauge rechargeDelay =
      Gauge.build(
              "shesmu_throttler_ratelimit_recharge_delay",
              "The number of milliseconds to generate a new token.")
          .labelNames("service")
          .register();

  private static final Gauge tokenCount =
      Gauge.build("shesmu_throttler_ratelimit_tokens", "The number of tokens in the bucket.")
          .labelNames("service")
          .register();

  public RateLimitThrottler() {
    super(MethodHandles.lookup(), TokenBucket.class, EXTENSION, "ratelimit");
  }

  @Override
  public TokenBucket create(Path filePath, String instanceName, Definer<TokenBucket> definer) {
    return new TokenBucket(filePath, instanceName);
  }
}
