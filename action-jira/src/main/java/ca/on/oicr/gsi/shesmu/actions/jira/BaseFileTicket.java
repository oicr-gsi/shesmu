package ca.on.oicr.gsi.shesmu.actions.jira;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import com.atlassian.httpclient.api.Request.Builder;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import io.prometheus.client.Counter;

public abstract class BaseFileTicket extends Action {
	private static final Counter failure = Counter
			.build("shesmu_jira_client_failures", "Number of failed requests to the JIRA web service.")
			.labelNames("name").register();
	private static final Counter issueBad = Counter
			.build("shesmu_jira_client_issue_bad", "Number of bad issues found in JIRA.").labelNames("name").register();
	private static final Counter issueCreates = Counter
			.build("shesmu_jira_client_issue_creates", "Number of new issues added to JIRA.").labelNames("name")
			.register();
	private static final Counter issueUpdates = Counter
			.build("shesmu_jira_client_issue_updates", "Number of changes to issues found in JIRA.").labelNames("name")
			.register();
	private static final Counter requests = Counter
			.build("shesmu_jira_client_requests", "Number of requests to the JIRA web service.").labelNames("v")
			.register();

	private final JiraRestClient client;

	private URI issueUrl;

	private final String name;

	private final String projectKey;

	@RuntimeInterop
	public String summary;

	public BaseFileTicket(String name, String url, String token, String projectKey) throws URISyntaxException {
		this.name = name;
		this.projectKey = projectKey;
		client = new AsynchronousJiraRestClientFactory().create(new URI(url), new AuthenticationHandler() {

			@Override
			public void configure(Builder builder) {
				builder.setHeader("Authorization", "Bearer " + token);
			}
		});

	}

	protected final ActionState badIssue() {
		issueBad.labels(name).inc();
		issueUrl = null;
		return ActionState.FAILED;
	}

	protected final ActionState createIssue(String description) {
		issueCreates.labels(name).inc();

		final Map<String, Object> project = new HashMap<>();
		project.put("key", projectKey);
		final Map<String, Object> issueType = new HashMap<>();
		issueType.put("name", "Bug");
		final IssueInput input = IssueInput.createWithFields(
				new FieldInput("project", new ComplexIssueInputFieldValue(project)), new FieldInput("summary", summary),
				new FieldInput("description", description),
				new FieldInput("issuetype", new ComplexIssueInputFieldValue(issueType)));

		issueUrl = client.getIssueClient().createIssue(input).claim().getSelf();
		return ActionState.SUCCEEDED;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		final BaseFileTicket other = (BaseFileTicket) obj;
		if (issueUrl == null) {
			if (other.issueUrl != null) {
				return false;
			}
		} else if (!issueUrl.equals(other.issueUrl)) {
			return false;
		}
		if (name == null) {
			if (other.name != null) {
				return false;
			}
		} else if (!name.equals(other.name)) {
			return false;
		}
		if (projectKey == null) {
			if (other.projectKey != null) {
				return false;
			}
		} else if (!projectKey.equals(other.projectKey)) {
			return false;
		}
		if (summary == null) {
			if (other.summary != null) {
				return false;
			}
		} else if (!summary.equals(other.summary)) {
			return false;
		}
		return true;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (issueUrl == null ? 0 : issueUrl.hashCode());
		result = prime * result + (name == null ? 0 : name.hashCode());
		result = prime * result + (projectKey == null ? 0 : projectKey.hashCode());
		result = prime * result + (summary == null ? 0 : summary.hashCode());
		return result;
	}

	@Override
	public ActionState perform() {
		requests.labels(name).inc();
		try {
			final SearchResult results = client.getSearchClient()
					.searchJql(String.format("summary = '%s'", summary.replace("'", "\\'")), 2, 0, null).claim();
			return perform(results);
		} catch (final Exception e) {
			failure.labels(name).inc();
			return ActionState.UNKNOWN;
		}
	}

	protected abstract ActionState perform(SearchResult results);

	@Override
	public int priority() {
		return 1000;
	}

	@Override
	public long retryMinutes() {
		return 10;
	}

	@Override
	public ObjectNode toJson(ObjectMapper mapper) {
		final ObjectNode node = mapper.createObjectNode();
		node.put("action", "jira-issue");
		node.put("instanceName", name);
		node.put("summary", summary);
		node.put("url", issueUrl == null ? null : issueUrl.toString());
		return node;
	}

	protected final ActionState updateIssue(Issue issue, int transition) {
		issueUpdates.labels(name).inc();
		issueUrl = issue.getSelf();
		client.getIssueClient().transition(issue, new TransitionInput(transition)).claim();
		return ActionState.SUCCEEDED;
	}

}