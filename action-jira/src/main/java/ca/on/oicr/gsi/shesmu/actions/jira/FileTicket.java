package ca.on.oicr.gsi.shesmu.actions.jira;

import java.net.URISyntaxException;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;

import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;

public class FileTicket extends BaseFileTicket {

	@RuntimeInterop
	public String description;

	public FileTicket(String name, String url, String token, String projectKey) throws URISyntaxException {
		super(name, url, token, projectKey);
	}

	private ActionState checkIssue(Issue issue) {
		if (issue.getStatus().getName().equalsIgnoreCase("CLOSED")
				|| issue.getStatus().getName().equalsIgnoreCase("RESOLVED")) {
			updateIssue(issue, new TransitionInput(3));
		}
		return ActionState.SUCCEEDED;
	}

	@Override
	protected ActionState perform(SearchResult results) {
		switch (results.getMaxResults()) {
		case 0:
			return createIssue(description);
		case 1:
			return checkIssue(results.getIssues().iterator().next());
		default:
			return badIssue();
		}
	}

}