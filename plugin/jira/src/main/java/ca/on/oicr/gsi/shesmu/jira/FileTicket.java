package ca.on.oicr.gsi.shesmu.jira;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;

import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;

public class FileTicket extends BaseTicketAction {

	@RuntimeInterop
	public String description;

	public FileTicket(String id) {
		super(id, "jira-open-ticket");
	}

	private ActionState checkIssue(Issue issue) {
		if (issue.getStatus().getName().equalsIgnoreCase("CLOSED")
				|| issue.getStatus().getName().equalsIgnoreCase("RESOLVED")) {
			return updateIssue(issue, new TransitionInput(3));
		}
		return ActionState.SUCCEEDED;
	}

	@Override
	protected ActionState perform(Stream<Issue> results) {
		final List<Issue> matches = results.collect(Collectors.toList());
		if (matches.isEmpty()) {
			return createIssue(description);
		}
		if (matches.stream().anyMatch(issue -> !issue.getStatus().getName().equalsIgnoreCase("CLOSED")
				&& !issue.getStatus().getName().equalsIgnoreCase("RESOLVED")))
			return ActionState.SUCCEEDED;
		return checkIssue(matches.get(0));
	}

}