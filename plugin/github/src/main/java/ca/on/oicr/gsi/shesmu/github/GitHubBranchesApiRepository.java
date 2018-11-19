package ca.on.oicr.gsi.shesmu.github;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamException;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.MetaInfServices;

import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingDirectory;
import ca.on.oicr.gsi.shesmu.util.AutoUpdatingJsonFile;
import ca.on.oicr.gsi.shesmu.util.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.util.cache.ValueCache;
import ca.on.oicr.gsi.status.ConfigurationSection;
import ca.on.oicr.gsi.status.SectionRenderer;

@MetaInfServices
public class GitHubBranchesApiRepository implements GithubBranchesRepository {
	private class GitHubRemote extends AutoUpdatingJsonFile<Configuration> {
		private class BranchCache extends ValueCache<Stream<GithubBranchValue>> {

			public BranchCache(Path fileName) {
				super("github-branches " + fileName.toString(), 10, ReplacingRecord::new);
			}

			@Override
			protected Stream<GithubBranchValue> fetch(Instant lastUpdated) throws Exception {
				if (!configuration.isPresent())
					return Stream.empty();
				final Configuration c = configuration.get();
				try (CloseableHttpResponse response = HTTP_CLIENT.execute(new HttpGet(
						String.format("https://api.github.com/repos/%s/%s/branches", c.getOwner(), c.getRepo())))) {
					return Stream
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
							});
				}
			}
		}

		private final BranchCache cache;
		private Optional<Configuration> configuration = Optional.empty();

		public GitHubRemote(Path fileName) {
			super(fileName, Configuration.class);
			cache = new BranchCache(fileName);
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
			return cache.get();
		}

		@Override
		public Optional<Integer> update(Configuration value) {
			configuration = Optional.of(value);
			cache.invalidate();
			return Optional.empty();
		}
	}

	public static final String EXTENSION = ".github";

	private static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();

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
