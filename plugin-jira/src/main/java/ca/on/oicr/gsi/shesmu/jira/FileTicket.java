package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionServices;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import com.atlassian.jira.rest.client.api.domain.Issue;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class FileTicket extends BaseTicketAction {

  @ActionParameter(required = false)
  public String assignee;

  @ActionParameter public String description;

  public FileTicket(Supplier<JiraConnection> connection) {
    super(connection, "jira-open-ticket", Optional.empty());
  }

  @Override
  protected boolean isInTargetState(Stream<String> closedStates, Predicate<String> matchesIssue) {
    return closedStates.noneMatch(matchesIssue);
  }

  @Override
  protected ActionState perform(ActionServices services, Stream<Issue> results) {
    final var matches = results.collect(Collectors.toList());
    if (matches.isEmpty()) {
      return createIssue(services, description, assignee);
    }
    return transitionIssues(services, matches.stream(), "Still not fixed:\n" + description);
  }

  /**
   * When reopening an issue, only one issue need be opened, so only attempt to reopen an existing
   * issue if the previous one could not be opened/there was no previous one.
   */
  @Override
  protected Optional<ActionState> processTransition(
      Optional<ActionState> accumulator, Supplier<Optional<ActionState>> transitionIssue) {
    return accumulator.isPresent() ? accumulator : transitionIssue.get();
  }

  @Override
  public boolean search(Pattern query) {
    return query.matcher(summary).matches() || query.matcher(description).matches();
  }

  @Override
  protected Stream<String> transitionActions(JiraConnection connection) {
    return connection.reopenActions();
  }
}
