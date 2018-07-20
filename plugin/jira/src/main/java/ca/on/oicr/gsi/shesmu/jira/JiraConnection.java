package ca.on.oicr.gsi.shesmu.jira;

import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;

public interface JiraConnection {

	JiraRestClient client();

	String instance();

	Stream<Issue> issues();

	String projectKey();

	void invalidate();

}
