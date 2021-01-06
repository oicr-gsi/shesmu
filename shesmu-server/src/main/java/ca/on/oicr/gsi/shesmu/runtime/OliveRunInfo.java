package ca.on.oicr.gsi.shesmu.runtime;

import java.time.Duration;
import java.time.Instant;

public class OliveRunInfo {
  private final Long inputCount;
  private final Instant lastRun;
  private final boolean ok;
  private final Duration runtime;
  private final Duration cpuTime;
  private final String status;

  public OliveRunInfo(
      boolean ok, String status, Long inputCount, Instant lastRun, Duration cpuTime) {
    this.ok = ok;
    this.status = status;
    this.inputCount = inputCount;
    this.lastRun = lastRun;
    this.runtime = Duration.between(lastRun, Instant.now());
    this.cpuTime = cpuTime;
  }

  public Duration cpuTime() {
    return cpuTime;
  }

  public Long inputCount() {
    return inputCount;
  }

  public boolean isOk() {
    return ok;
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
