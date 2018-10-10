package ca.on.oicr.gsi.shesmu.jira;

import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.domain.BasicIssue;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.Transition.Field;
import com.atlassian.jira.rest.client.api.domain.input.ComplexIssueInputFieldValue;
import com.atlassian.jira.rest.client.api.domain.input.FieldInput;
import com.atlassian.jira.rest.client.api.domain.input.IssueInput;
import com.atlassian.jira.rest.client.api.domain.input.TransitionInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import ca.on.oicr.gsi.shesmu.Action;
import ca.on.oicr.gsi.shesmu.ActionState;
import ca.on.oicr.gsi.shesmu.Throttler;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
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

	private final Optional<ActionState> emptyTransitionState;

	private final Set<String> issues = new TreeSet<>();

	private URI issueUrl;

	@RuntimeInterop
	public String summary;
	@RuntimeInterop
	public String type = "Task";

	public BaseTicketAction(String id, String jsonName, Optional<ActionState> emptyTransitionState) {
		super(jsonName);
		this.emptyTransitionState = emptyTransitionState;
		config = BaseJiraRepository.get(id);
	}

	protected final ActionState badIssue() {
		issueBad.labels(config.instance()).inc();
		issueUrl = null;
		return ActionState.FAILED;
	}

	protected abstract Comment comment();

	protected final ActionState createIssue(String description) {
		if (Throttler.anyOverloaded("jira", config.projectKey())) {
			return ActionState.THROTTLED;
		}
		issueCreates.labels(config.instance()).inc();

		final Map<String, Object> project = new HashMap<>();
		project.put("key", config.projectKey());
		final Map<String, Object> issueType = new HashMap<>();
		issueType.put("name", type);
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
	public final boolean equals(Object obj) {
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
	public final int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (config == null ? 0 : config.hashCode());
		result = prime * result + (summary == null ? 0 : summary.hashCode());
		return result;
	}

	private boolean isInTargetState(Issue issue) {
		return isInTargetState(config.closedStatuses(), issue.getStatus().getName()::equalsIgnoreCase);
	}

	protected abstract boolean isInTargetState(Stream<String> closedStates, Predicate<String> matchesIssue);

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
	public final int priority() {
		return 1000;
	}

	/**
	 * Process an issue and return a new action state
	 *
	 * @param accumulator
	 *            the state from the previously processed issue
	 * @param transitionIssue
	 *            change the current issue; if not invoked, the current issue is
	 *            left unchanged.
	 */
	protected abstract Optional<ActionState> processTransition(Optional<ActionState> accumulator,
			Supplier<Optional<ActionState>> transitionIssue);

	@Override
	public final long retryMinutes() {
		return 10;
	}

	@Override
	public ObjectNode toJson(ObjectMapper mapper) {
		final ObjectNode node = mapper.createObjectNode();
		node.put("instanceName", config.instance());
		node.put("projectKey", config.projectKey());
		node.put("summary", summary);
		node.put("instanceUrl", config.url());
		node.put("url", issueUrl == null ? null : issueUrl.toString());
		issues.forEach(node.putArray("issues")::add);
		return node;
	}

	/**
	 * Get the list of the names of the actions that will put an issue in the right
	 * state
	 */
	protected abstract Stream<String> transitionActions(JiraConnection connection);

	/**
	 * Change an issue's status using one of JIRA's transitions
	 *
	 * This attempts to find a transition that is from the approved list of
	 * transition names that is allowed for the issue and doesn't require any input.
	 * It then attempts to change the issue in JIRA.
	 */
	private final Optional<ActionState> transitionIssue(Issue issue) {
		if (isInTargetState(issue)) {
			return Optional.of(ActionState.SUCCEEDED);
		}
		return RuntimeSupport.stream(config.client().getIssueClient().getTransitions(issue).claim())//
				.filter(t -> transitionActions(config).anyMatch(t.getName()::equalsIgnoreCase)
						&& RuntimeSupport.stream(t.getFields()).noneMatch(Field::isRequired))//
				.findAny()//
				.map(t -> {
					if (Throttler.anyOverloaded("jira", config.projectKey())) {
						return ActionState.THROTTLED;
					}
					issueUpdates.labels(config.instance()).inc();
					issueUrl = issue.getSelf();
					config.client().getIssueClient().transition(issue, new TransitionInput(t.getId(), comment()))
							.claim();
					config.invalidate();
					issues.add(issue.getKey());
					return ActionState.SUCCEEDED;
				});
	}

	/**
	 * Transitions a stream of issues as dictated by
	 * {@link #processTransition(Optional, Supplier)}
	 *
	 * This works over the list of actions and does a reduce to come up with a final
	 * action state. The {@link #processTransition(Optional, Supplier)} decides how
	 * to proceed at each step allow all of the actions to be processed; or only
	 * some.
	 */
	protected final ActionState transitionIssues(Stream<Issue> issues) {
		return issues//
				.sorted(Comparator.comparingInt(issue -> isInTargetState(issue) ? 0 : 1))
				.<Optional<ActionState>>reduce(emptyTransitionState, //
						(acc, issue) -> processTransition(acc, () -> transitionIssue(issue)), //
						(a, b) -> RuntimeSupport.merge(a, b,
								(aa, bb) -> aa.sortPriority() > bb.sortPriority() ? aa : bb))//
				.orElse(ActionState.FAILED);
	}
}