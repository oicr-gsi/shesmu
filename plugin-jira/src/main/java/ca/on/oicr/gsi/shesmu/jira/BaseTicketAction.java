package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.Action;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.atlassian.jira.rest.client.api.domain.*;
import com.atlassian.jira.rest.client.api.domain.Transition.Field;
import com.atlassian.jira.rest.client.api.domain.input.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.prometheus.client.Counter;
import java.net.URI;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class BaseTicketAction extends Action {
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

  private final JiraConnection connection;

  private final Optional<ActionState> emptyTransitionState;

  private final Set<String> issues = new TreeSet<>();

  private URI issueUrl;

  @ActionParameter public String summary;

  @ActionParameter(required = false)
  public String type = "Task";

  public BaseTicketAction(
      JiraConnection connection, String jsonName, Optional<ActionState> emptyTransitionState) {
    super(jsonName);
    this.emptyTransitionState = emptyTransitionState;
    this.connection = connection;
  }

  protected final ActionState createIssue(
      ActionServices services, String description, String assignee) {
    if (services.isOverloaded("jira", connection.projectKey())) {
      return ActionState.THROTTLED;
    }
    issueCreates.labels(connection.url(), connection.projectKey()).inc();

    final IssueInputBuilder inputBuilder = new IssueInputBuilder();
    for (IssueType issueType : connection.client().getMetadataClient().getIssueTypes().claim()) {
      if (issueType.getName().equals(type)) {
        inputBuilder.setIssueType(issueType);
        break;
      }
    }
    inputBuilder.setProjectKey(connection.projectKey());
    inputBuilder.setSummary(asciiOnly(summary));
    inputBuilder.setDescription(asciiOnly(description));
    if (assignee != null && !assignee.isEmpty()) {
      inputBuilder.setAssigneeName(assignee);
    }

    final BasicIssue result =
        connection.client().getIssueClient().createIssue(inputBuilder.build()).claim();
    issueUrl = result.getSelf();
    connection
        .client()
        .getIssueClient()
        .updateIssue(
            result.getKey(),
            IssueInput.createWithFields(
                new FieldInput(IssueFieldId.LABELS_FIELD, Arrays.asList("shesmu", "bot"))))
        .claim();
    connection.invalidate();
    issues.add(result.getKey());
    return ActionState.SUCCEEDED;
  }

  private String asciiOnly(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("[^\\x00-\\x7F]", "");
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
    final BaseTicketAction other = (BaseTicketAction) obj;
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
    return true;
  }

  @Override
  public final int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (connection == null ? 0 : connection.hashCode());
    result = prime * result + (summary == null ? 0 : summary.hashCode());
    return result;
  }

  private boolean isInTargetState(Issue issue) {
    return isInTargetState(
        connection.closedStatuses(), issue.getStatus().getName()::equalsIgnoreCase);
  }

  protected abstract boolean isInTargetState(
      Stream<String> closedStates, Predicate<String> matchesIssue);

  @Override
  public final ActionState perform(ActionServices services) {
    if (connection == null) {
      return ActionState.FAILED;
    }
    requests.labels(connection.url(), connection.projectKey()).inc();
    try {
      return perform(
          services,
          connection
              .issues() //
              .filter(issue -> issue.getSummary().equals(summary)) //
              .peek(issue -> issues.add(issue.getKey())));
    } catch (final Exception e) {
      failure.labels(connection.url(), connection.projectKey()).inc();
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
    final ObjectNode node = mapper.createObjectNode();
    node.put("projectKey", connection.projectKey());
    node.put("summary", summary);
    node.put("instanceUrl", connection.url());
    node.put("url", issueUrl == null ? null : issueUrl.toString());
    issues.forEach(node.putArray("issues")::add);
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
  private final Optional<ActionState> transitionIssue(
      ActionServices services, Issue issue, Comment comment) {
    if (isInTargetState(issue)) {
      return Optional.of(ActionState.SUCCEEDED);
    }
    return Utils.stream(connection.client().getIssueClient().getTransitions(issue).claim()) //
        .filter(
            t ->
                transitionActions(connection).anyMatch(t.getName()::equalsIgnoreCase)
                    && Utils.stream(t.getFields()).noneMatch(Field::isRequired)) //
        .findAny() //
        .map(
            t -> {
              if (services.isOverloaded("jira", connection.projectKey())) {
                return ActionState.THROTTLED;
              }
              issueUpdates.labels(connection.url(), connection.projectKey()).inc();
              issueUrl = issue.getSelf();
              connection
                  .client()
                  .getIssueClient()
                  .transition(issue, new TransitionInput(t.getId(), comment))
                  .claim();
              connection.invalidate();
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
    final Comment comment = commentText == null ? null : Comment.valueOf(asciiOnly(commentText));
    return issues
        .sorted(Comparator.comparingInt(issue -> isInTargetState(issue) ? 0 : 1))
        .<Optional<ActionState>>reduce(
            emptyTransitionState,
            (acc, issue) -> processTransition(acc, () -> transitionIssue(services, issue, comment)),
            (a, b) ->
                Utils.merge(a, b, (aa, bb) -> aa.sortPriority() > bb.sortPriority() ? aa : bb))
        .orElse(ActionState.FAILED);
  }
}
