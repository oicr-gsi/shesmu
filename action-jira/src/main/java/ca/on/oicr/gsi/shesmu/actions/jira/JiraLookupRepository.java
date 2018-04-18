package ca.on.oicr.gsi.shesmu.actions.jira;

import java.net.URI;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.atlassian.httpclient.api.Request.Builder;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;
import ca.on.oicr.gsi.shesmu.LookupRepository;
import ca.on.oicr.gsi.shesmu.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.Tuple;

@MetaInfServices(LookupRepository.class)
public final class JiraLookupRepository extends BaseJiraRepository<Lookup> implements LookupRepository {

	private static abstract class JiraLookup implements Lookup {
		private final JiraRestClient client;
		private final Object errorResult;
		private final String name;
		private final String projectKey;
		private final Imyhat returnType;

		public JiraLookup(JiraRestClient client, String projectKey, String name, Imyhat returnType,
				Object errorResult) {
			super();
			this.client = client;
			this.projectKey = projectKey;
			this.name = name;
			this.returnType = returnType;
			this.errorResult = errorResult;
		}

		protected abstract Object process(SearchResult searchResult) throws Exception;

		@Override
		public final Object lookup(Object... parameters) {
			final String jql = (String) parameters[0];
			try {
				return process(client.getSearchClient()
						.searchJql(String.format("(%s) AND project ='%s'", jql, projectKey)).claim());
			} catch (final Exception e) {
				e.printStackTrace();
				return errorResult;
			}

		}

		@Override
		public final String name() {
			return name;
		}

		@Override
		public final Imyhat returnType() {
			return returnType;
		}

		@Override
		public final Stream<Imyhat> types() {
			return Stream.of(Imyhat.STRING);
		}

	}

	private static final Imyhat QUERY_TYPE = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING).asList();

	public JiraLookupRepository() {
		super("JIRA Lookup Repository");
	}

	@Override
	protected Stream<Lookup> create(Configuration config) {
		try {
			final JiraRestClient client = new AsynchronousJiraRestClientFactory().create(new URI(config.getUrl()),
					new AuthenticationHandler() {

						@Override
						public void configure(Builder builder) {
							builder.setHeader("Authorization", "Bearer " + config.getToken());
						}
					});

			return Stream.<Lookup>of(new JiraLookup(client, config.getProjectKey(),
					String.format("count_tickets_%s", config.getName()), Imyhat.INTEGER, -1L) {

				@Override
				protected Object process(SearchResult results) throws Exception {
					return (long) results.getMaxResults();
				}
			}, new JiraLookup(client, config.getProjectKey(), String.format("query_tickets_%s", config.getName()),
					QUERY_TYPE, Collections.emptySet()) {

				@Override
				protected Object process(SearchResult results) throws Exception {
					return RuntimeSupport.stream(results.getIssues())
							.map(issue -> new Tuple(issue.getKey(), issue.getSummary())).collect(Collectors.toSet());
				}
			});
		} catch (final Exception e) {
			e.printStackTrace();
			return Stream.empty();
		}
	}

	@Override
	public Stream<Lookup> queryLookups() {
		return stream();
	}

}