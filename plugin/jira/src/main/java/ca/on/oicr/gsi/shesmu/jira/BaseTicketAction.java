package ca.on.oicr.gsi.shesmu.jira;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.Throttler;
import io.prometheus.client.Counter;

public abstract class BaseTicketAction extends Action {
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

	private final JiraConnection config;

	private final Set<String> issues = new TreeSet<>();

	private URI issueUrl;

	@RuntimeInterop
	public String summary;

	public BaseTicketAction(String id, String jsonName) {
		super(jsonName);
		config = BaseJiraRepository.get(id);
	}

	protected final ActionState badIssue() {
		issueBad.labels(config.instance()).inc();
		issueUrl = null;
		return ActionState.FAILED;
	}

	protected final ActionState createIssue(String description) {
		if (Throttler.anyOverloaded("jira", config.projectKey())) {
			return ActionState.THROTTLED;
		}
		issueCreates.labels(config.instance()).inc();

		final Map<String, Object> project = new HashMap<>();
		project.put("key", config.projectKey());
		final Map<String, Object> issueType = new HashMap<>();
		issueType.put("name", "Bug");
		final IssueInput input = IssueInput.createWithFields(
				new FieldInput(IssueFieldId.PROJECT_FIELD, new ComplexIssueInputFieldValue(project)), //
				new FieldInput(IssueFieldId.SUMMARY_FIELD, summary), //
				new FieldInput(IssueFieldId.DESCRIPTION_FIELD, description), //
				new FieldInput(IssueFieldId.ISSUE_TYPE_FIELD, new ComplexIssueInputFieldValue(issueType)));

		final BasicIssue result = config.client().getIssueClient().createIssue(input).claim();
		issueUrl = result.getSelf();
		config.client().getIssueClient()
				.updateIssue(result.getKey(), IssueInput
						.createWithFields(new FieldInput(IssueFieldId.LABELS_FIELD, Arrays.asList("shesmu", "bot"))))
				.claim();
		config.invalidate();
		issues.add(result.getKey());
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
		final BaseTicketAction other = (BaseTicketAction) obj;
		if (issueUrl == null) {
			if (other.issueUrl != null) {
				return false;
			}
		} else if (!issueUrl.equals(other.issueUrl)) {
			return false;
		}
		if (config == null) {
			if (other.config != null) {
				return false;
			}
		} else if (!config.equals(other.config)) {
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
		result = prime * result + (config == null ? 0 : config.hashCode());
		result = prime * result + (summary == null ? 0 : summary.hashCode());
		return result;
	}

	@Override
	public final ActionState perform() {
		if (config == null) {
			return ActionState.FAILED;
		}
		requests.labels(config.instance()).inc();
		try {
			return perform(config.issues()//
					.filter(issue -> issue.getSummary().equals(summary))//
					.peek(issue -> issues.add(issue.getKey())));
		} catch (final Exception e) {
			failure.labels(config.instance()).inc();
			e.printStackTrace();
			return ActionState.UNKNOWN;
		}
	}

	protected abstract ActionState perform(Stream<Issue> results);

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
		node.put("instanceName", config.instance());
		node.put("projectKey", config.projectKey());
		node.put("summary", summary);
		node.put("url", issueUrl == null ? null : issueUrl.toString());
		issues.forEach(node.putArray("issues")::add);
		return node;
	}

	protected final ActionState updateIssue(Issue issue, TransitionInput transition) {
		if (Throttler.anyOverloaded("jira", config.projectKey())) {
			return ActionState.THROTTLED;
		}
		issueUpdates.labels(config.instance()).inc();
		issueUrl = issue.getSelf();
		config.client().getIssueClient().transition(issue, transition).claim();
		config.invalidate();
		issues.add(issue.getKey());
		return ActionState.SUCCEEDED;
	}

}