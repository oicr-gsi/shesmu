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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public abstract class BaseJiraRepository<T> implements LoadedConfiguration {

	public final class JiraConfig extends AutoUpdatingJsonFile<Configuration> implements JiraConnection {

		private JiraRestClient client;

		private final List<String> closeActions = Collections.emptyList();

		private final List<String> closedStatuses = Collections.emptyList();

		private final String id = String.format("jira%d", counter++);

		private final String instance;

		private List<Issue> issues = Collections.emptyList();

		private Instant lastFetch = Instant.EPOCH;
		private String passwordFile;

		private String projectKey = "FAKE";

		private final List<String> reopenActions = Collections.emptyList();

		private String url;

		private String user;

		private List<T> value = Collections.emptyList();

		public JiraConfig(Path fileName) {
			super(fileName, Configuration.class);
			clients.put(id, this);
			final String filenamePart = fileName.getFileName().toString();
			instance = filenamePart.substring(0, filenamePart.length() - EXTENSION.length());
		}

		@Override
		public JiraRestClient client() {
			return client;
		}

		@Override
		public Stream<String> closeActions() {
			return closeActions.stream();
		}

		@Override
		public Stream<String> closedStatuses() {
			return closedStatuses.stream();
		}

		public String id() {
			return id;
		}

		@Override
		public String instance() {
			return instance;
		}

		@Override
		public void invalidate() {
			lastFetch = Instant.EPOCH;
		}

		@Override
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
					lastFetchTime.labels(projectKey, purpose()).set(now.getEpochSecond());
					lastFetchSize.labels(projectKey, purpose()).set(buffer.size());
					cacheSize.labels(projectKey, purpose()).set(issues.size());
				} catch (final Exception e) {
					e.printStackTrace();
					fetchErrors.labels(projectKey, purpose()).inc();
				}
			}
			return issues.stream();
		}

		@Override
		public String projectKey() {
			return projectKey;
		}

		@Override
		public Stream<String> reopenActions() {
			return reopenActions.stream();
		}

		public ConfigurationSection status() {
			return new ConfigurationSection(name + ": " + instance) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					renderer.line("Filename", fileName().toString());
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

		public Stream<T> stream() {
			return value.stream();
		}

		@Override
		public Optional<Integer> update(Configuration config) {
			url = config.getUrl();
			try (Scanner passwordScanner = new Scanner(
					fileName().getParent().resolve(Paths.get(config.getPasswordFile())))) {
				client = new AsynchronousJiraRestClientFactory().createWithBasicHttpAuthentication(
						new URI(config.getUrl()), config.getUser(), passwordScanner.nextLine());
				value = create(this, fileName()).collect(Collectors.toList());
				projectKey = config.getProjectKey();
				user = config.getUser();
				passwordFile = config.getPasswordFile();
				issues = Collections.emptyList();
				lastFetch = Instant.EPOCH;
			} catch (final Exception e) {
				e.printStackTrace();
			}
			return Optional.empty();
		}

		@Override
		public String url() {
			return url;
		}
	}

	private static final Gauge cacheSize = Gauge
			.build("shesmu_jira_ticket_cache_size", "The number of tickets currently cached locally.")
			.labelNames("project", "purpose").create();
	private static Map<String, JiraConnection> clients = new HashMap<>();

	private static final String EXTENSION = ".jira";

	private static final Counter fetchErrors = Counter
			.build("shesmu_jira_ticket_fetch_error", "The number of errors refreshing the ticket cache.")
			.labelNames("project", "purpose").create();

	private static final Set<String> FIELDS = Stream
			.of("summary", "issuetype", "created", "updated", "project", "status", "description")
			.collect(Collectors.toSet());

	private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm");

	private static final Gauge lastFetchSize = Gauge
			.build("shesmu_jira_ticket_fetch_size", "The number of tickets retrieved in the last query.")
			.labelNames("project", "purpose").create();
	private static final Gauge lastFetchTime = Gauge
			.build("shesmu_jira_ticket_fetch_time", "The timestamp of the last query.").labelNames("project", "purpose")
			.create();

	public static JiraConnection get(String id) {
		return clients.get(id);
	}

	private final AutoUpdatingDirectory<JiraConfig> configurations;

	private int counter;

	private final String name;

	public BaseJiraRepository(String name) {
		this.name = name;
		configurations = new AutoUpdatingDirectory<>(EXTENSION, JiraConfig::new);
	}

	protected abstract Stream<T> create(JiraConfig config, Path filename);

	@Override
	public final Stream<ConfigurationSection> listConfiguration() {
		return configurations.stream().map(JiraConfig::status);
	}

	protected abstract String purpose();

	protected final Stream<T> stream() {
		return configurations.stream().flatMap(JiraConfig::stream);
	}

}
