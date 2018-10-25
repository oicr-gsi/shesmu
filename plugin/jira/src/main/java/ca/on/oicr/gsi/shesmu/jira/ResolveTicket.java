package ca.on.oicr.gsi.shesmu.jira;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;

import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.definitions.ActionParameter;

public final class ResolveTicket extends BaseTicketAction {

	@ActionParameter
	public String comment;

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
	protected ActionState perform(Stream<Issue> issues) {
		return transitionIssues(issues);
	}

	/**
	 * When closing, all issues must be closed; therefore, attempt to close an issue
	 * and choose the most sad status.
	 */
	@Override
	protected Optional<ActionState> processTransition(Optional<ActionState> accumulator,
			Supplier<Optional<ActionState>> transitionIssue) {
		return RuntimeSupport.merge(accumulator, transitionIssue.get(),
				(a, b) -> a.sortPriority() > b.sortPriority() ? a : b);
	}

	@Override
	protected Stream<String> transitionActions(JiraConnection connection) {
		return connection.closeActions();
	}

}