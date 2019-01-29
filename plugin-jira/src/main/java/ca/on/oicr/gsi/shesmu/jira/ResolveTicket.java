package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ResolveTicket extends BaseTicketAction {

  @ActionParameter public String comment;

  public ResolveTicket(JiraConnection connection) {
    super(connection, "jira-close-ticket", Optional.of(ActionState.SUCCEEDED));
  }

  @Override
  protected Comment comment() {
    return comment == null ? null : Comment.valueOf(comment);
  }

  @Override
  protected boolean isInTargetState(Stream<String> closedStates, Predicate<String> matchesIssue) {
    return closedStates.anyMatch(matchesIssue);
  }

  @Override
  protected ActionState perform(ActionServices services, Stream<Issue> issues) {
    return transitionIssues(services, issues);
  }

  /**
   * When closing, all issues must be closed; therefore, attempt to close an issue and choose the
   * most sad status.
   */
  @Override
  protected Optional<ActionState> processTransition(
      Optional<ActionState> accumulator, Supplier<Optional<ActionState>> transitionIssue) {
    return Utils.merge(
        accumulator, transitionIssue.get(), (a, b) -> a.sortPriority() > b.sortPriority() ? a : b);
  }

  @Override
  protected Stream<String> transitionActions(JiraConnection connection) {
    return connection.closeActions();
  }
}
