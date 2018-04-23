package ca.on.oicr.gsi.shesmu.actions.jira;

import java.net.URI;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.atlassian.httpclient.api.Request.Builder;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;
import ca.on.oicr.gsi.shesmu.LookupRepository;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.lookup.LookupForInstance;

@MetaInfServices(LookupRepository.class)
public final class JiraLookupRepository extends BaseJiraRepository<LookupDefinition> implements LookupRepository {

	private static abstract class JiraLookup extends LookupForInstance {
		private final JiraRestClient client;
		private final String projectKey;

		public JiraLookup(JiraRestClient client, String projectKey, String name, Imyhat returnType)
				throws NoSuchMethodException, IllegalAccessException {
			super("lookup", name, returnType, Imyhat.STRING);
			this.client = client;
			this.projectKey = projectKey;
		}

		protected final Optional<SearchResult> search(String jql) {
			try {
				return Optional.of(client.getSearchClient()
						.searchJql(String.format("(%s) AND project ='%s'", jql, projectKey)).claim());
			} catch (final Exception e) {
				e.printStackTrace();
				return Optional.empty();
			}

		}
	}

	private static final Imyhat QUERY_TYPE = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING).asList();

	public JiraLookupRepository() {
		super("JIRA Lookup Repository");
	}

	@Override
	protected Stream<LookupDefinition> create(Configuration config) {
		try {
			final JiraRestClient client = new AsynchronousJiraRestClientFactory().create(new URI(config.getUrl()),
					new AuthenticationHandler() {

						@Override
						public void configure(Builder builder) {
							builder.setHeader("Authorization", "Bearer " + config.getToken());
						}
					});

			return Stream.<LookupDefinition>of(new JiraLookup(client, config.getProjectKey(),
					String.format("count_tickets_%s", config.getName()), Imyhat.INTEGER) {

				@RuntimeInterop
				public long lookup(String jql) {
					return search(jql).map(x -> (long) x.getMaxResults()).orElse(-1L);
				}
			}, new JiraLookup(client, config.getProjectKey(), String.format("query_tickets_%s", config.getName()),
					QUERY_TYPE) {

				public Set<Tuple> convertResults(SearchResult results) {
					return RuntimeSupport.stream(results.getIssues())
							.map(issue -> new Tuple(issue.getKey(), issue.getSummary())).collect(Collectors.toSet());
				}

				@RuntimeInterop
				public Set<Tuple> lookup(String jql) {
					return search(jql).map(this::convertResults).orElseGet(Collections::emptySet);
				}
			});
		} catch (final Exception e) {
			e.printStackTrace();
			return Stream.empty();
		}
	}

	@Override
	public Stream<LookupDefinition> queryLookups() {
		return stream();
	}

}