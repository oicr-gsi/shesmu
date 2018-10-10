package ca.on.oicr.gsi.shesmu.jira;

import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;

public interface JiraConnection {

	JiraRestClient client();

	Stream<String> closeActions();

	Stream<String> closedStatuses();

	String instance();

	void invalidate();

	Stream<Issue> issues();

	String projectKey();

	Stream<String> reopenActions();

	String url();

}
