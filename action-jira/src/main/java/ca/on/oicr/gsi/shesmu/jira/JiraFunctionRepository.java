package ca.on.oicr.gsi.shesmu.jira;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.atlassian.jira.rest.client.api.domain.Issue;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.Tuple;
import ca.on.oicr.gsi.shesmu.function.FunctionForInstance;

@MetaInfServices(FunctionRepository.class)
public final class JiraFunctionRepository extends BaseJiraRepository<FunctionDefinition> implements FunctionRepository {

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
					&& (issue.getSummary() != null && issue.getSummary().contains(keyword)
							|| issue.getDescription() != null && issue.getDescription().contains(keyword));
		}

	}

	private static final Imyhat QUERY_TYPE = Imyhat.tuple(Imyhat.STRING, Imyhat.STRING).asList();

	public JiraFunctionRepository() {
		super("JIRA Function Repository");
	}

	@Override
	protected Stream<FunctionDefinition> create(JiraConfig config, Path filename) {
		try {
			final Lookup lookup = MethodHandles.lookup();
			return Stream.<FunctionDefinition>of(
					new FunctionForInstance(lookup, "count", String.format("count_tickets_%s", config.instance()),
							String.format(
									"Count the number of open or closed tickets from the JIRA project defined in %s.",
									filename),
							Imyhat.INTEGER, Imyhat.STRING, Imyhat.BOOLEAN) {

						@RuntimeInterop
						public long count(String keyword, boolean open) {
							return config.issues().filter(new IssueFilter(keyword, open)).count();
						}
					},
					new FunctionForInstance(lookup, "fetch", String.format("query_tickets_%s", config.instance()),
							String.format(
									"Get the ticket summary and descriptions from the JIRA project defined in %s.",
									filename),
							QUERY_TYPE, Imyhat.STRING, Imyhat.BOOLEAN) {

						@RuntimeInterop
						public Set<Tuple> fetch(String keyword, boolean open) {
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
		return "function";
	}

	@Override
	public Stream<FunctionDefinition> queryFunctions() {
		return stream();
	}

}