package ca.on.oicr.gsi.shesmu.jira;

import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;

import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;

public class ResolveTicket extends BaseTicketAction {

	@RuntimeInterop
	public String comment;

	public ResolveTicket(String id) {
		super(id, "jira-close-ticket");
	}

	@Override
	protected ActionState perform(Stream<Issue> results) {

		return results.reduce(ActionState.SUCCEEDED, (state, issue) -> {
			if (issue.getStatus().getName().equalsIgnoreCase("CLOSED")
					|| issue.getStatus().getName().equalsIgnoreCase("RESOLVED")) {
				return ActionState.SUCCEEDED;
			}
			final TransitionInput transition = new TransitionInput(5,
					comment == null ? null : Comment.valueOf(comment));
			final ActionState updated = updateIssue(issue, transition);
			return state == ActionState.SUCCEEDED ? updated : state;

		}, (a, b) -> a == ActionState.SUCCEEDED ? b : a);
	}

}