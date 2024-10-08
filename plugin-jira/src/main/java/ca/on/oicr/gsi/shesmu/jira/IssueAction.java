package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.jira.IssueVerb.Close;
import ca.on.oicr.gsi.shesmu.jira.IssueVerb.Open;
import ca.on.oicr.gsi.shesmu.plugin.AlgebraicValue;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.LogLevel;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IssueAction extends Action {

  static final List<String> STANDARD_LABELS = List.of("shesmu", "bot");
  private static final Counter failure =
      Counter.build(
              "shesmu_jira_client_failures", "Number of failed requests to the JIRA web service.")
          .labelNames("url", "project")
          .register();
  static final Counter issueCreates =
      Counter.build("shesmu_jira_client_issue_creates", "Number of new issues added to JIRA.")
          .labelNames("url", "project")
          .register();
  static final Counter issueUpdates =
      Counter.build(
              "shesmu_jira_client_issue_updates", "Number of changes to issues found in JIRA.")
          .labelNames("url", "project")
          .register();
  private static final Counter requests =
      Counter.build("shesmu_jira_client_requests", "Number of requests to the JIRA web service.")
          .labelNames("url", "project")
          .register();

  private static String asciiOnly(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^\\x00-\\x7F]", "");
  }

  private final Supplier<JiraConnection> connection;
  private Optional<Instant> issueLastModified = Optional.empty();
  private String issueUrl;
  private Set<String> issues = Set.of();
  public Set<String> labels = Set.of();
  public String summary;
  public String type = "Task";
  private IssueVerb verb;

  public IssueAction(Supplier<JiraConnection> connection) {
    super("jira-issue");
    this.connection = connection;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final var other = (IssueAction) obj;
    if (connection == null) {
      if (other.connection != null) {
        return false;
      }
    } else if (!connection.equals(other.connection)) {
      return false;
    }
    if (summary == null) {
      if (other.summary != null) {
        return false;
      }
    } else if (!summary.equals(other.summary)) {
      return false;
    }
    if (verb == null) {
      return other.verb == null;
    } else return verb.verb().equals(other.verb.verb());
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return issueLastModified;
  }

  @Override
  public void generateUUID(Consumer<byte[]> digest) {
    digest.accept(verb.verb().getBytes(StandardCharsets.UTF_8));
    digest.accept(new byte[] {0});
    digest.accept(summary.getBytes(StandardCharsets.UTF_8));
    digest.accept(new byte[] {0});
    digest.accept(connection.get().projectKey().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public int hashCode() {
    return Objects.hash(connection, summary, verb);
  }

  @ActionParameter(required = false)
  public void labels(Set<String> labels) {
    this.labels = labels.stream().map(IssueAction::asciiOnly).collect(Collectors.toSet());
  }

  @Override
  public ActionState perform(
      ActionServices services, Duration lastGeneratedByOlive, boolean isOliveLive) {
    if (connection == null) {
      // 'connection' is a bit of a misnomer - it's the Definer supplied by the PluginManager.
      // It should never be null. Very bad things have happened if it's null
      // We also can't log through the Definer if we have no Definer :(
      System.err.println("JIRA Connection Definer for " + issueUrl + " is null.");
      return ActionState.FAILED;
    }
    final var current = connection.get();
    ((Definer<JiraConnection>) connection)
        .log(
            new StringBuilder("Performing jira updates with ").append(current).toString(),
            LogLevel.DEBUG,
            new TreeMap<>());
    requests.labels(current.url(), current.projectKey()).inc();
    try {
      // summary needs to be wrapped in escaped quotes as an exact-search workaround
      // https://community.atlassian.com/t5/Jira-questions/How-to-query-Summary-for-EXACT-match/qaq-p/588482
      // This unfortunately still matches on supersets, so if you have 'My Ticket' it won't match
      // 'Ny Ticket'
      // but it will match 'My Ticket 2'. We address that later.
      var searchedIssues =
          current.search(
              String.format(
                  "summary ~ \"%s\" and project = %s and issuetype = %s",
                  JiraConnection.MAPPER
                      .writeValueAsString(summary)
                      .replace("\"", "\\\""), // workaround
                  current.projectKey(),
                  JiraConnection.MAPPER.writeValueAsString(type)),
              Set.of(
                  Issue.LABELS.name(),
                  Issue.STATUS.name(),
                  Issue.TYPE.name(),
                  Issue.UPDATED.name(),
                  Issue.SUMMARY.name()));
      // Filter again by summary title for exact matching
      searchedIssues =
          searchedIssues.stream()
              .filter(i -> i.getFields().get(Issue.SUMMARY.name()).asText().equals(summary))
              .toList();
      this.issues = searchedIssues.stream().map(Issue::getKey).collect(Collectors.toSet());
      ((Definer<JiraConnection>) connection)
          .log(
              new StringBuilder("Got ")
                  .append(searchedIssues.isEmpty() ? "nothing" : searchedIssues)
                  .toString(),
              LogLevel.DEBUG,
              new TreeMap<>());
      final var missingLabels = new TreeSet<String>();
      final var result =
          verb.perform(
              connection,
              searchedIssues,
              summary,
              labels,
              type,
              best -> {
                issueUrl = String.format("%s/browse/%s", current.url(), best.getKey());
                issueLastModified = best.extract(Issue.UPDATED).map(Date::toInstant);
                final var currentLabels = best.extract(Issue.LABELS).map(Set::of).orElse(Set.of());
                missingLabels.clear();
                Stream.of(STANDARD_LABELS, labels)
                    .flatMap(Collection::stream)
                    .filter(label -> !currentLabels.contains(label))
                    .forEach(missingLabels::add);
              });
      if (!missingLabels.isEmpty() && issueUrl != null) {
        current.addLabels(issueUrl, missingLabels);
      }
      return result;
    } catch (final Exception e) {
      failure.labels(connection.get().url(), connection.get().projectKey()).inc();
      ((Definer<JiraConnection>) connection).log(e.toString(), LogLevel.ERROR, new TreeMap<>());
      return ActionState.UNKNOWN;
    }
  }

  @Override
  public int priority() {
    return 1000;
  }

  @Override
  public long retryMinutes() {
    return 10;
  }

  @Override
  public boolean search(Pattern query) {
    return query.matcher(summary).matches() || verb.search(query);
  }

  @ActionParameter
  public void summary(String summary) {
    this.summary = asciiOnly(summary);
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.put("projectKey", connection.get().projectKey());
    node.put("summary", summary);
    node.put("instanceUrl", connection.get().url());
    node.put("verb", verb.verb());
    node.put("url", issueUrl);
    issues.forEach(node.putArray("issues")::add);
    return node;
  }

  @ActionParameter(required = false)
  public void type(String type) {
    this.type = asciiOnly(type);
  }

  @ActionParameter(
      name = "is",
      type = "u2CLOSED$o1comment$sOPEN$o3assignee$qsdescription$sreopen_comment$s")
  public void verb(AlgebraicValue value) {
    verb =
        switch (value.name()) {
          case "CLOSED" -> new Close(asciiOnly((String) value.get(0)));
          case "OPEN" -> new Open(
              ((Optional<?>) value.get(0)).map(String.class::cast).map(IssueAction::asciiOnly),
              asciiOnly((String) value.get(2)),
              asciiOnly((String) value.get(1)));
          default -> throw new IllegalArgumentException();
        };
  }

  @Override
  public Stream<String> tags() {
    return Stream.of("jira-verb:" + verb.verb());
  }
}
