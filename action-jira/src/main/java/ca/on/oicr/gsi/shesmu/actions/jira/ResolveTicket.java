package ca.on.oicr.gsi.shesmu.actions.jira;

import java.net.URISyntaxException;

import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;

import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;

public class ResolveTicket extends BaseFileTicket {

	public ResolveTicket(String name, String url, String token, String projectKey) throws URISyntaxException {
		super(name, url, token, projectKey);
	}

	@RuntimeInterop
	public String comment;

	@Override
	protected ActionState perform(SearchResult results) {

		for (final Issue issue : results.getIssues()) {
			if (!issue.getStatus().getName().equalsIgnoreCase("CLOSED")
					&& !issue.getStatus().getName().equalsIgnoreCase("RESOLVED")) {
				TransitionInput transition = new TransitionInput(5, comment == null ? null : Comment.valueOf(comment));
				updateIssue(issue, transition);
			}
		}
		return ActionState.SUCCEEDED;
	}

}