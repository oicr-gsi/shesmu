package ca.on.oicr.gsi.shesmu.jira;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;

import com.atlassian.jira.rest.client.api.domain.Issue;

import ca.on.oicr.gsi.shesmu.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.FunctionParameter;
import ca.on.oicr.gsi.shesmu.FunctionRepository;
import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.Tuple;
import ca.on.oicr.gsi.shesmu.util.RuntimeBinding;

@MetaInfServices(FunctionRepository.class)
public final class JiraFunctionRepository extends BaseJiraRepository<FunctionDefinition> implements FunctionRepository {

	private static class IssueFilter implements Predicate<Issue> {
		private final String keyword;
		private final boolean open;
		private final JiraConnection connection;

		public IssueFilter(JiraConnection connection, String keyword, boolean open) {
			super();
			this.connection = connection;
			this.keyword = keyword;
			this.open = open;
		}

		@Override
		public boolean test(Issue issue) {
			return connection.closedStatuses().anyMatch(issue.getStatus().getName()::equals) != open
					&& (issue.getSummary() != null && issue.getSummary().contains(keyword)
							|| issue.getDescription() != null && issue.getDescription().contains(keyword));
		}

	}

	private static final RuntimeBinding<JiraConnection> RUNTIME_BINDING = new RuntimeBinding<>(JiraConnection.class,
			EXTENSION)//
					.staticFunction("count_tickets_%s", JiraFunctionRepository.class, "count", Imyhat.INTEGER,
							"Count the number of open or closed tickets from the JIRA project defined in %2$s.",
							new FunctionParameter("search_test", Imyhat.STRING),
							new FunctionParameter("is_open", Imyhat.BOOLEAN))//
					.staticFunction("query_tickets_%s", JiraFunctionRepository.class, "fetch",
							Imyhat.tuple(Imyhat.STRING, Imyhat.STRING).asList(),
							"Get the ticket summary and descriptions from the JIRA project defined in %2$s.",
							new FunctionParameter("search_test", Imyhat.STRING),
							new FunctionParameter("is_open", Imyhat.BOOLEAN));

	@RuntimeInterop
	public static long count(JiraConnection connection, String keyword, boolean open) {
		return connection.issues().filter(new IssueFilter(connection, keyword, open)).count();
	}

	@RuntimeInterop
	public static Set<Tuple> fetch(JiraConnection connection, String keyword, boolean open) {
		return connection.issues().filter(new IssueFilter(connection, keyword, open))
				.map(issue -> new Tuple(issue.getKey(), issue.getSummary())).collect(Collectors.toSet());
	}

	public JiraFunctionRepository() {
		super("JIRA Function Repository");
	}

	@Override
	protected Stream<FunctionDefinition> create(JiraConfig config, Path filename) {
		return RUNTIME_BINDING.bindFunctions(config).stream();
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