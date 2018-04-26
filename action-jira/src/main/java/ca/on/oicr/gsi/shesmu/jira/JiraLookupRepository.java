package ca.on.oicr.gsi.shesmu.jira;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.atlassian.jira.rest.client.api.domain.Issue;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.LookupDefinition;
import ca.on.oicr.gsi.shesmu.LookupRepository;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.lookup.LookupForInstance;

@MetaInfServices(LookupRepository.class)
public final class JiraLookupRepository extends BaseJiraRepository<LookupDefinition> implements LookupRepository {

	private static class IssueFilter implements Predicate<Issue> {
		private final String keyword;
		private final boolean open;

		public IssueFilter(String keyword, boolean open) {
			super();
			this.keyword = keyword;
			this.open = open;
		}

		@Override
		public boolean test(Issue issue) {
			return (issue.getStatus().getName().equals("CLOSED")
					|| issue.getStatus().getName().equals("RESOLVED")) != open
					&& (issue.getSummary().contains(keyword) || issue.getDescription().contains(keyword));
		}

	}

	private static final Imyhat QUERY_TYPE = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING).asList();

	public JiraLookupRepository() {
		super("JIRA Lookup Repository");
	}

	@Override
	protected Stream<LookupDefinition> create(JiraConfig config) {
		try {
			final Lookup lookup = MethodHandles.lookup();
			return Stream.<LookupDefinition>of(
					new LookupForInstance(lookup, "lookup", String.format("count_tickets_%s", config.instance()),
							Imyhat.INTEGER, Imyhat.STRING, Imyhat.BOOLEAN) {

						@RuntimeInterop
						public long lookup(String keyword, boolean open) {
							return config.issues().filter(new IssueFilter(keyword, open)).count();
						}
					}, new LookupForInstance(lookup, "lookup", String.format("query_tickets_%s", config.instance()),
							QUERY_TYPE, Imyhat.STRING, Imyhat.BOOLEAN) {

						@RuntimeInterop
						public Set<Tuple> lookup(String keyword, boolean open) {
							return config.issues().filter(new IssueFilter(keyword, open))
									.map(issue -> new Tuple(issue.getKey(), issue.getSummary()))
									.collect(Collectors.toSet());
						}
					});
		} catch (final Exception e) {
			e.printStackTrace();
			return Stream.empty();
		}
	}

	@Override
	protected String purpose() {
		return "lookup";
	}

	@Override
	public Stream<LookupDefinition> queryLookups() {
		return stream();
	}

}