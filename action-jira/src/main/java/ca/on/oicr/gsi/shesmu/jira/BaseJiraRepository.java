package ca.on.oicr.gsi.shesmu.jira;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.nio.file.Path;
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
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import ca.on.oicr.gsi.shesmu.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.LoadedConfiguration;
import ca.on.oicr.gsi.shesmu.Pair;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

public abstract class BaseJiraRepository<T> implements LoadedConfiguration {

	public final class JiraConfig extends AutoUpdatingJsonFile<Configuration> implements JiraConnection {

		private JiraRestClient client;

		private final String id = String.format("jira%d", counter++);

		private final String instance;

		private List<Issue> issues = Collections.emptyList();

		private Instant lastFetch = Instant.EPOCH;
		private String projectKey = "FAKE";

		private final Map<String, String> properties = new TreeMap<>();

		private final Pair<String, Map<String, String>> status;

		private List<T> value;

		public JiraConfig(Path fileName) {
			super(fileName, Configuration.class);
			properties.put("filename", fileName.toString());
			status = new Pair<>(name, properties);
			clients.put(id, this);
			final String filenamePart = fileName.getFileName().toString();
			instance = filenamePart.substring(0, filenamePart.length() - EXTENSION.length());
		}

		@Override
		public JiraRestClient client() {
			return client;
		}

		public String id() {
			return id;
		}

		@Override
		public String instance() {
			return instance;
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

		public Pair<String, Map<String, String>> status() {
			return status;
		}

		public Stream<T> stream() {
			return value.stream();
		}

		@Override
		protected void update(Configuration config) {
			value = create(this, fileName()).collect(Collectors.toList());
			properties.put("project", config.getProjectKey());
			properties.put("url", config.getUrl());
			try {
				client = new AsynchronousJiraRestClientFactory()
						.createWithBasicHttpAuthentication(new URI(config.getUrl()), ACCOUNT_NAME, ACCOUNT_PASSWORD);
				projectKey = config.getProjectKey();
				issues = Collections.emptyList();
				lastFetch = Instant.EPOCH;
			} catch (final Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static final String ACCOUNT_NAME;
	private static final String ACCOUNT_PASSWORD;
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

	static {
		String accountName;
		String accountPassword;
		try {
			final Properties properties = new Properties();
			properties.load(new FileInputStream(new File(System.getProperty("user.home"), ".jira")));
			accountName = properties.getProperty("user");
			accountPassword = properties.getProperty("password");
		} catch (final Exception e) {
			e.printStackTrace();
			accountName = "jira";
			accountPassword = "jira";
		}
		ACCOUNT_NAME = accountName;
		ACCOUNT_PASSWORD = accountPassword;
	}

	public static JiraConnection get(String id) {
		return clients.get(id);
	}

	private final List<JiraConfig> configurations;

	private int counter;

	private final String name;

	public BaseJiraRepository(String name) {
		this.name = name;
		configurations = RuntimeSupport.dataFiles(EXTENSION).map(JiraConfig::new).peek(JiraConfig::start)
				.collect(Collectors.toList());
	}

	protected abstract Stream<T> create(JiraConfig config, Path filename);

	@Override
	public final Stream<Pair<String, Map<String, String>>> listConfiguration() {
		return configurations.stream().map(JiraConfig::status);
	}

	protected abstract String purpose();

	protected final Stream<T> stream() {
		return configurations.stream().flatMap(JiraConfig::stream);
	}

}