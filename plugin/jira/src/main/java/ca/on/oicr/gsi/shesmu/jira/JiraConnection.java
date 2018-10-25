package ca.on.oicr.gsi.shesmu.jira;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.definitions.FileBackedConfiguration;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuAction;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.util.definitions.ShesmuParameter;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public class JiraConnection extends AutoUpdatingJsonFile<Configuration> implements FileBackedConfiguration {

	private class IssueFilter implements Predicate<Issue> {
		private final String keyword;
		private final boolean open;

		public IssueFilter(String keyword, boolean open) {
			super();
			this.keyword = keyword;
			this.open = open;
		}

		@Override
		public boolean test(Issue issue) {
			return closedStatuses().anyMatch(issue.getStatus().getName()::equals) != open
					&& (issue.getSummary() != null && issue.getSummary().contains(keyword)
							|| issue.getDescription() != null && issue.getDescription().contains(keyword));
		}

	}

	private static final Gauge cacheSize = Gauge
			.build("shesmu_jira_ticket_cache_size", "The number of tickets currently cached locally.")
			.labelNames("project", "url").create();

	protected static final String EXTENSION = ".jira";

	private static final Counter fetchErrors = Counter
			.build("shesmu_jira_ticket_fetch_error", "The number of errors refreshing the ticket cache.")
			.labelNames("project", "url").create();

	private static final Set<String> FIELDS = Stream
			.of("summary", "issuetype", "created", "updated", "project", "status", "description")
			.collect(Collectors.toSet());

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm");
	private static final Gauge lastFetchSize = Gauge
			.build("shesmu_jira_ticket_fetch_size", "The number of tickets retrieved in the last query.")
			.labelNames("project", "rul").create();

	private static final Gauge lastFetchTime = Gauge
			.build("shesmu_jira_ticket_fetch_time", "The timestamp of the last query.").labelNames("project", "url")
			.create();

	private JiraRestClient client;

	private List<String> closeActions = Collections.emptyList();

	private List<String> closedStatuses = Collections.emptyList();

	private List<Issue> issues = Collections.emptyList();

	private Instant lastFetch = Instant.EPOCH;

	private String passwordFile;

	private String projectKey = "FAKE";

	private List<String> reopenActions = Collections.emptyList();

	private String url;

	private String user;

	public JiraConnection(Path fileName) {
		super(fileName, Configuration.class);
	}

	public JiraRestClient client() {
		return client;
	}

	public Stream<String> closeActions() {
		return closeActions.stream();
	}

	public Stream<String> closedStatuses() {
		return closedStatuses.stream();
	}

	@Override

	public ConfigurationSection configuration() {
		return new ConfigurationSection(fileName().toString()) {

			@Override
			public void emit(SectionRenderer renderer) throws XMLStreamException {
				if (url != null) {
					renderer.link("URL", url, url);
				}
				renderer.line("Project", projectKey);
				renderer.line("Cache Size", issues.size());
				renderer.lineSpan("Last Cache Update", lastFetch);
				if (user != null) {
					renderer.line("User", user);
				}
				if (passwordFile != null) {
					renderer.line("Password File", passwordFile);
				}
				renderer.line("Reopen Actions", reopenActions.stream().collect(Collectors.joining(" | ")));
				renderer.line("Close Actions", closeActions.stream().collect(Collectors.joining(" | ")));
				renderer.line("Closed Statuses", closedStatuses.stream().collect(Collectors.joining(" | ")));
			}
		};
	}

	@ShesmuMethod(description = "Count the number of open or closed tickets from the JIRA project defined in {file}.")
	public long count_tickets_$(@ShesmuParameter(description = "keyword") String keyword,
			@ShesmuParameter(description = "is ticket open") boolean open) {
		return issues().filter(new IssueFilter(keyword, open)).count();
	}

	public void invalidate() {
		lastFetch = Instant.EPOCH;
	}

	public Stream<Issue> issues() {
		final Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
		if (Duration.between(lastFetch, now).toMinutes() > 15) {
			try {
				final String jql = String.format("updated >= '%s' AND project = %s",
						FORMAT.format(lastFetch.atZone(ZoneId.systemDefault())), projectKey);
				final List<Issue> buffer = new ArrayList<>();
				for (int page = 0; true; page++) {
					final SearchResult results = client.getSearchClient().searchJql(jql, 500, 500 * page, FIELDS)
							.claim();
					for (final Issue issue : results.getIssues()) {
						buffer.add(issue);
					}
					if (buffer.size() >= results.getTotal()) {
						break;
					}
				}

				final Set<Long> newIds = buffer.stream().map(Issue::getId).collect(Collectors.toSet());
				issues = Stream
						.concat(issues.stream().filter(issue -> !newIds.contains(issue.getId())), buffer.stream())
						.collect(Collectors.toList());
				lastFetch = now;
				lastFetchTime.labels(projectKey, url()).set(now.getEpochSecond());
				lastFetchSize.labels(projectKey, url()).set(buffer.size());
				cacheSize.labels(projectKey, url()).set(issues.size());
			} catch (final Exception e) {
				e.printStackTrace();
				fetchErrors.labels(projectKey, url()).inc();
			}
		}
		return issues.stream();
	}

	public String projectKey() {
		return projectKey;
	}

	@ShesmuMethod(type = "at2ss", description = "Get the ticket summary and descriptions from the JIRA project defined in {file}.")
	public Set<Tuple> query_tickets_$(@ShesmuParameter(description = "keyword") String keyword,
			@ShesmuParameter(description = "is ticket open") boolean open) {
		return issues().filter(new IssueFilter(keyword, open))
				.map(issue -> new Tuple(issue.getKey(), issue.getSummary())).collect(Collectors.toSet());
	}

	public Stream<String> reopenActions() {
		return reopenActions.stream();
	}

	@ShesmuAction(description = "Closes any JIRA tickets with a matching summary. Defined in {file}.")
	public ResolveTicket resolve_ticket_$() {
		return new ResolveTicket(this);
	}

	@ShesmuAction(description = "Opens (or re-opens) a JIRA ticket. Defined in {file}.")
	public FileTicket ticket_$() {
		return new FileTicket(this);
	}

	@Override
	public Optional<Integer> update(Configuration config) {
		url = config.getUrl();
		try (Scanner passwordScanner = new Scanner(
				fileName().getParent().resolve(Paths.get(config.getPasswordFile())))) {
			client = new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(new URI(config.getUrl()),
					config.getUser(), passwordScanner.nextLine());
			projectKey = config.getProjectKey();
			user = config.getUser();
			passwordFile = config.getPasswordFile();
			issues = Collections.emptyList();
			closedStatuses = config.getClosedStatuses();
			closeActions = config.getCloseActions();
			reopenActions = config.getReopenActions();
			lastFetch = Instant.EPOCH;
		} catch (final Exception e) {
			e.printStackTrace();
		}
		return Optional.empty();
	}

	public String url() {
		return url;
	}

}
