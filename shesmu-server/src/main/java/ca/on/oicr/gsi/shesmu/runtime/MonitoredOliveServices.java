package ca.on.oicr.gsi.shesmu.runtime;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.dumper.Dumper;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import io.prometheus.client.Gauge;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

public final class MonitoredOliveServices implements OliveServices, AutoCloseable {
  private static class AlertInfo {
    final List<String> annotations = new ArrayList<>();
    final Set<SourceLocation> locations = new HashSet<>();
    long ttl;
  }

  private static final Gauge actionCount =
      Gauge.build(
              "shesmu_olive_action_count",
              "The number of unique actions produced during the last run of a script.")
          .labelNames("filename")
          .register();
  private static final Gauge alertCount =
      Gauge.build(
              "shesmu_olive_alert_count",
              "The number of unique alerts produced during the last run of a script.")
          .labelNames("filename")
          .register();
  private static final Gauge newActionCount =
      Gauge.build(
              "shesmu_olive_new_action_count",
              "The number of unique actions produced during the last run of a script that were previously unknown to the scheduler.")
          .labelNames("filename")
          .register();
  private static final Gauge newAlertCount =
      Gauge.build(
              "shesmu_olive_new_alert_count",
              "The number of unique alerts produced during the last run of a script that were previously unknown to the scheduler.")
          .labelNames("filename")
          .register();
  private final Map<Action, Pair<Set<String>, Set<SourceLocation>>> actions = new HashMap<>();
  private final Map<List<String>, AlertInfo> alerts = new HashMap<>();
  private final OliveServices backing;
  private final String filename;

  public MonitoredOliveServices(OliveServices backing, String filename) {
    this.backing = backing;
    this.filename = filename;
  }

  @Override
  public boolean accept(
      Action action, String filename, int line, int column, String hash, String[] tags) {
    final var pair =
        actions.computeIfAbsent(action, k -> new Pair<>(new TreeSet<>(), new HashSet<>()));
    pair.first().addAll(List.of(tags));
    return pair.second().add(new SourceLocation(filename, line, column, hash));
  }

  @Override
  public boolean accept(
      String[] labels,
      String[] annotation,
      long ttl,
      String filename,
      int line,
      int column,
      String hash) {
    final var alert = alerts.computeIfAbsent(List.of(labels), k -> new AlertInfo());
    // This is going to massively duplicate the annotations, but the action processor will
    // de-duplicate them and resolve any conflicts
    alert.annotations.addAll(List.of(annotation));
    alert.ttl = Math.max(alert.ttl, ttl);
    return alert.locations.add(new SourceLocation(filename, line, column, hash));
  }

  public void close() throws Exception {
    var newActions = 0;
    for (final var entry : actions.entrySet()) {
      final var tags = entry.getValue().first().toArray(String[]::new);
      for (final var location : entry.getValue().second()) {
        if (!backing.accept(
            entry.getKey(),
            location.fileName(),
            location.line(),
            location.column(),
            location.hash(),
            tags)) {
          newActions++;
        }
      }
    }

    var newAlerts = 0;
    for (final var entry : alerts.entrySet()) {
      final var labels = entry.getKey().toArray(String[]::new);
      final var annotations = entry.getValue().annotations.toArray(String[]::new);
      for (final var location : entry.getValue().locations) {
        if (!backing.accept(
            labels,
            annotations,
            entry.getValue().ttl,
            location.fileName(),
            location.line(),
            location.column(),
            location.hash())) {
          newAlerts++;
        }
      }
    }

    actionCount.labels(filename).set(actions.size());
    newActionCount.labels(filename).set(newActions);
    alertCount.labels(filename).set(alerts.size());
    newAlertCount.labels(filename).set(newAlerts);
  }

  @Override
  public Dumper findDumper(String name, String[] columns, Imyhat... types) {
    return backing.findDumper(name, columns, types);
  }

  @Override
  public boolean isOverloaded(String... services) {
    return backing.isOverloaded(services);
  }

  @Override
  public <T> Stream<T> measureFlow(
      Stream<T> input,
      String filename,
      int line,
      int column,
      String hash,
      String oliveFile,
      int oliveLine,
      int oliveColumn,
      String oliveHash) {
    return backing.measureFlow(
        input, filename, line, column, hash, oliveFile, oliveLine, oliveColumn, oliveHash);
  }

  @Override
  public void oliveRuntime(String filename, int line, int column, long timeInNs) {
    backing.oliveRuntime(filename, line, column, timeInNs);
  }
}
