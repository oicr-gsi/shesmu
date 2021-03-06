package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.api.domain.input.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BaseTicketAction extends Action {
  private static final List<String> STANDARD_LABELS = List.of("shesmu", "bot");
  private static final Counter failure =
      Counter.build(
              "shesmu_jira_client_failures", "Number of failed requests to the JIRA web service.")
          .labelNames("url", "project")
          .register();
  private static final Counter issueBad =
      Counter.build("shesmu_jira_client_issue_bad", "Number of bad issues found in JIRA.")
          .labelNames("url", "project")
          .register();
  private static final Counter issueCreates =
      Counter.build("shesmu_jira_client_issue_creates", "Number of new issues added to JIRA.")
          .labelNames("url", "project")
          .register();
  private static final Counter issueUpdates =
      Counter.build(
              "shesmu_jira_client_issue_updates", "Number of changes to issues found in JIRA.")
          .labelNames("url", "project")
          .register();
  private static final Counter requests =
      Counter.build("shesmu_jira_client_requests", "Number of requests to the JIRA web service.")
          .labelNames("url", "project")
          .register();
  private final Supplier<JiraConnection> connection;

  private final Optional<ActionState> emptyTransitionState;
  private List<String> errors = List.of();
  private Optional<Instant> issueLastModified = Optional.empty();
  private URI issueUrl;
  private final Set<String> issues = new TreeSet<>();

  @ActionParameter(required = false)
  public Set<String> labels = Set.of();

  @ActionParameter public String summary;

  @ActionParameter(required = false)
  public String type = "Task";

  public BaseTicketAction(
      Supplier<JiraConnection> connection,
      String jsonName,
      Optional<ActionState> emptyTransitionState) {
    super(jsonName);
    this.emptyTransitionState = emptyTransitionState;
    this.connection = connection;
  }

  private String asciiOnly(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^\\x00-\\x7F]", "");
  }

  protected final ActionState createIssue(
      ActionServices services, String description, String assignee) {
    final var overloadedServices = services.isOverloaded("jira", connection.get().projectKey());
    if (!overloadedServices.isEmpty()) {
      errors = List.of("Overloaded services: " + String.join(", ", overloadedServices));
      return ActionState.THROTTLED;
    }
    issueCreates.labels(connection.get().url(), connection.get().projectKey()).inc();

    final var inputBuilder = new IssueInputBuilder();
    for (var issueType : connection.get().client().getMetadataClient().getIssueTypes().claim()) {
      if (issueType.getName().equals(type)) {
        inputBuilder.setIssueType(issueType);
        break;
      }
    }
    inputBuilder.setProjectKey(connection.get().projectKey());
    inputBuilder.setSummary(asciiOnly(summary));
    inputBuilder.setDescription(asciiOnly(description));
    if (assignee != null && !assignee.isEmpty()) {
      inputBuilder.setAssigneeName(assignee);
    }

    final var result =
        connection.get().client().getIssueClient().createIssue(inputBuilder.build()).claim();
    issueUrl = result.getSelf();
    connection
        .get()
        .client()
        .getIssueClient()
        .updateIssue(
            result.getKey(),
            IssueInput.createWithFields(
                new FieldInput(
                    IssueFieldId.LABELS_FIELD,
                    Stream.of(STANDARD_LABELS, labels)
                        .flatMap(Collection::stream)
                        .distinct()
                        .collect(Collectors.toList()))))
        .claim();
    connection.get().invalidate();
    issues.add(result.getKey());
    issueLastModified = Optional.of(Instant.now());
    return ActionState.SUCCEEDED;
  }

  @Override
  public final boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final var other = (BaseTicketAction) obj;
    if (connection == null) {
      if (other.connection != null) {
        return false;
      }
    } else if (!connection.equals(other.connection)) {
      return false;
    }
    if (summary == null) {
      return other.summary == null;
    } else return summary.equals(other.summary);
  }

  @Override
  public Optional<Instant> externalTimestamp() {
    return issueLastModified;
  }

  @Override
  public final void generateUUID(Consumer<byte[]> digest) {
    digest.accept(summary.getBytes(StandardCharsets.UTF_8));
    digest.accept(new byte[] {0});
    digest.accept(connection.get().projectKey().getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public final int hashCode() {
    final var prime = 31;
    var result = 1;
    result = prime * result + (connection == null ? 0 : connection.hashCode());
    result = prime * result + (summary == null ? 0 : summary.hashCode());
    return result;
  }

  private boolean isInTargetState(Issue issue) {
    return isInTargetState(
        connection.get().closedStatuses(), issue.getStatus().getName()::equalsIgnoreCase);
  }

  protected abstract boolean isInTargetState(
      Stream<String> closedStates, Predicate<String> matchesIssue);

  @Override
  public final ActionState perform(ActionServices services) {
    if (connection == null) {
      return ActionState.FAILED;
    }
    requests.labels(connection.get().url(), connection.get().projectKey()).inc();
    try {
      return perform(
          services,
          connection
              .get()
              .issues() //
              .filter(issue -> issue.getSummary().equals(summary)) //
              .peek(issue -> issues.add(issue.getKey())));
    } catch (final Exception e) {
      failure.labels(connection.get().url(), connection.get().projectKey()).inc();
      e.printStackTrace();
      return ActionState.UNKNOWN;
    }
  }

  protected abstract ActionState perform(ActionServices services, Stream<Issue> results);

  @Override
  public final int priority() {
    return 1000;
  }

  /**
   * Process an issue and return a new action state
   *
   * @param accumulator the state from the previously processed issue
   * @param transitionIssue change the current issue; if not invoked, the current issue is left
   *     unchanged.
   */
  protected abstract Optional<ActionState> processTransition(
      Optional<ActionState> accumulator, Supplier<Optional<ActionState>> transitionIssue);

  @Override
  public final long retryMinutes() {
    return 10;
  }

  @Override
  public ObjectNode toJson(ObjectMapper mapper) {
    final var node = mapper.createObjectNode();
    node.put("projectKey", connection.get().projectKey());
    node.put("summary", summary);
    node.put("instanceUrl", connection.get().url());
    node.put("url", issueUrl == null ? null : issueUrl.toString());
    issues.forEach(node.putArray("issues")::add);
    errors.forEach(node.putArray("errors")::add);
    return node;
  }

  /** Get the list of the names of the actions that will put an issue in the right state */
  protected abstract Stream<String> transitionActions(JiraConnection connection);

  /**
   * Change an issue's status using one of JIRA's transitions
   *
   * <p>This attempts to find a transition that is from the approved list of transition names that
   * is allowed for the issue and doesn't require any input. It then attempts to change the issue in
   * JIRA.
   */
  private Optional<ActionState> transitionIssue(
      ActionServices services, Issue issue, Comment comment) {
    if (isInTargetState(issue)) {
      return Optional.of(ActionState.SUCCEEDED);
    }
    return Utils.stream(connection.get().client().getIssueClient().getTransitions(issue).claim())
        .filter(
            t ->
                transitionActions(connection.get()).anyMatch(t.getName()::equalsIgnoreCase)
                    && Utils.stream(t.getFields())
                        .noneMatch(
                            f ->
                                f.isRequired()
                                    && connection.get().defaultFieldValues(f.getId()) == null))
        .findAny()
        .map(
            t -> {
              final var overloadedServices =
                  services.isOverloaded("jira", connection.get().projectKey());
              if (!overloadedServices.isEmpty()) {
                errors =
                    Collections.singletonList(
                        "Overloaded services: " + String.join(", ", overloadedServices));
                return ActionState.THROTTLED;
              }
              issueUpdates.labels(connection.get().url(), connection.get().projectKey()).inc();
              issueUrl = issue.getSelf();
              issueLastModified =
                  Stream.concat(
                          issueLastModified.stream(),
                          Stream.of(
                              Instant.ofEpochMilli(issue.getUpdateDate().toInstant().getMillis())))
                      .max(Comparator.naturalOrder());
              final List<FieldInput> fields = new ArrayList<>();
              for (final var field : t.getFields()) {
                if (field.getId().equals(IssueFieldId.LABELS_FIELD.id)) {
                  fields.add(
                      new FieldInput(
                          IssueFieldId.LABELS_FIELD,
                          Stream.of(issue.getLabels(), STANDARD_LABELS, labels)
                              .flatMap(Collection::stream)
                              .distinct()
                              .collect(Collectors.toList())));
                } else if (field.isRequired()) {
                  final var value = connection.get().defaultFieldValues(field.getId());
                  fields.add(
                      new FieldInput(
                          field.getId(), ComplexIssueInputFieldValue.with("name", value)));
                }
              }

              connection
                  .get()
                  .client()
                  .getIssueClient()
                  .transition(issue, new TransitionInput(t.getId(), fields, comment))
                  .claim();
              connection.get().invalidate();
              issues.add(issue.getKey());
              return ActionState.SUCCEEDED;
            });
  }

  /**
   * Transitions a stream of issues as dictated by {@link #processTransition(Optional, Supplier)}
   *
   * <p>This works over the list of actions and does a reduce to come up with a final action state.
   * The {@link #processTransition(Optional, Supplier)} decides how to proceed at each step allow
   * all of the actions to be processed; or only some.
   */
  protected final ActionState transitionIssues(
      ActionServices services, Stream<Issue> issues, String commentText) {
    final var comment = commentText == null ? null : Comment.valueOf(asciiOnly(commentText));
    return issues
        .sorted(Comparator.comparingInt(issue -> isInTargetState(issue) ? 0 : 1))
        .reduce(
            emptyTransitionState,
            (acc, issue) -> processTransition(acc, () -> transitionIssue(services, issue, comment)),
            (a, b) ->
                Utils.merge(a, b, (aa, bb) -> aa.sortPriority() > bb.sortPriority() ? aa : bb))
        .orElse(ActionState.FAILED);
  }
}
