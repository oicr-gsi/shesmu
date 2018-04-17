package ca.on.oicr.gsi.shesmu.actions.jira;

import java.net.URISyntaxException;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;

import ca.on.oicr.gsi.shesmu.ActionState;

public class ResolveTicket extends BaseFileTicket {

	public ResolveTicket(String name, String url, String token, String projectKey) throws URISyntaxException {
		super(name, url, token, projectKey);
	}

	@Override
	protected ActionState perform(SearchResult results) {

		for (final Issue issue : results.getIssues()) {
			if (!issue.getStatus().getName().equalsIgnoreCase("CLOSED")
					&& !issue.getStatus().getName().equalsIgnoreCase("RESOLVED")) {
				updateIssue(issue, 5);
			}
		}
		return ActionState.SUCCEEDED;
	}

}