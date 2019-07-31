package ca.on.oicr.gsi.shesmu.runtime;

import java.time.Duration;
import java.time.Instant;

public class OliveRunInfo {
  private final Long inputCount;
  private final Instant lastRun;
  private final Duration runtime;
  private final String status;

  public OliveRunInfo(String status, Long inputCount, Instant lastRun) {
    this.status = status;
    this.inputCount = inputCount;
    this.lastRun = lastRun;
    this.runtime = Duration.between(lastRun, Instant.now());
  }

  public Long inputCount() {
    return inputCount;
  }

  public Instant lastRun() {
    return lastRun;
  }

  public Duration runtime() {
    return runtime;
  }

  public String status() {
    return status;
  }
}
