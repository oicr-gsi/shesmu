package ca.on.oicr.gsi.shesmu.github;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.prometheus.LatencyHistogram;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;

@MetaInfServices
public class GitHubBranchesApiRepository implements GithubBranchesRepository {
	private class GitHubRemote extends AutoUpdatingJsonFile<Configuration> {
		private List<GithubBranchValue> cache = Collections.emptyList();
		private Optional<Configuration> configuration = Optional.empty();
		private Instant lastUpdated = Instant.EPOCH;

		public GitHubRemote(Path fileName) {
			super(fileName, Configuration.class);
		}

		public ConfigurationSection configuration() {
			return new ConfigurationSection(fileName().toString()) {

				@Override
				public void emit(SectionRenderer renderer) throws XMLStreamException {
					configuration.ifPresent(c -> {
						renderer.line("Owner", c.getOwner());
						renderer.line("Repository", c.getRepo());
					});
				}
			};
		}

		public Stream<GithubBranchValue> stream() {
			if (Duration.between(lastUpdated, Instant.now()).get(ChronoUnit.SECONDS) > 900) {
				configuration.ifPresent(c -> {
					try (AutoCloseable timer = fetchLatency.start();
							CloseableHttpResponse response = HTTP_CLIENT
									.execute(new HttpGet(String.format("https://api.github.com/repos/%s/%s/branches",
											c.getOwner(), c.getRepo())))) {
						cache = Stream
								.of(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(),
										BranchResponse[].class))//
								.map(r -> new GithubBranchValue() {

									@Override
									public String branch() {
										return r.getName();
									}

									@Override
									public String commit() {
										return r.getCommit().getSha();
									}

									@Override
									public String owner() {
										return c.getOwner();
									}

									@Override
									public String repository() {
										return c.getRepo();
									}
								})//
								.collect(Collectors.toList());
						count.labels(fileName().toString()).set(cache.size());
						lastUpdated = Instant.now();
						lastFetchTime.labels(fileName().toString()).setToCurrentTime();
					} catch (final Exception e) {
						e.printStackTrace();
						provenanceError.labels(fileName().toString()).inc();
					}
				});
			}
			return cache.stream();
		}

		@Override
		public Optional<Integer> update(Configuration value) {
			configuration = Optional.of(value);
			return Optional.empty();
		}
	}

	private static final Gauge count = Gauge.build("shesmu_github_last_count", "The number of branches from GitHub.")
			.labelNames("target").register();

	public static final String EXTENSION = ".github";

	private static final LatencyHistogram fetchLatency = new LatencyHistogram("shesmu_github_request_time",
			"The time to fetch data from GitHub.");

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

	private static final Gauge lastFetchTime = Gauge
			.build("shesmu_github_last_fetch_time",
					"The time, in seconds since the epoch, when the last fetch from GitHub fetch occured.")
			.labelNames("target").register();

	private static final Counter provenanceError = Counter
			.build("shesmu_github_error", "The number of times calling out to GitHub has failed.").labelNames("target")
			.register();

	private final AutoUpdatingDirectory<GitHubRemote> sources;

	public GitHubBranchesApiRepository() {
		sources = new AutoUpdatingDirectory<>(EXTENSION, GitHubRemote::new);
	}

	@Override
	public Stream<ConfigurationSection> listConfiguration() {
		return sources.stream().map(GitHubRemote::configuration);
	}

	@Override
	public Stream<GithubBranchValue> stream() {
		return sources.stream().flatMap(GitHubRemote::stream);
	}

}
