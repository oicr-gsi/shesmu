package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.MergingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.cache.ValueCache;
import ca.on.oicr.gsi.shesmu.plugin.filter.FilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.FilterJson;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueFieldId;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;

public class JiraConnection extends JsonPluginFile<Configuration> {
  private class FilterCache extends KeyValueCache<String, Stream<JiraActionFilter>> {
    public FilterCache(Path fileName) {
      super("jira-filters " + fileName.toString(), 15, ReplacingRecord::new);
    }

    @Override
    protected Stream<JiraActionFilter> fetch(String jql, Instant lastUpdated) throws Exception {
      if (client == null) {
        return Stream.empty();
      }
      final List<JiraActionFilter> buffer = new ArrayList<>();
      for (int page = 0; true; page++) {
        final SearchResult results =
            client.getSearchClient().searchJql(jql, 500, 500 * page, FIELDS_FILTERS).claim();
        for (final Issue issue : results.getIssues()) {
          final String assignee;
          if (issue.getAssignee() == null) {
            assignee = "Unassigned";
          } else if (issue.getAssignee().getDisplayName() == null) {
            assignee = "Unknown";
          } else {
            assignee = issue.getAssignee().getDisplayName();
          }
          FilterJson.extractFromText(issue.getDescription(), MAPPER)
              .ifPresent(
                  filter ->
                      buffer.add(
                          new JiraActionFilter(
                              filter, issue.getKey(), issue.getSummary(), assignee)));
        }
        if (buffer.size() >= results.getTotal()) {
          break;
        }
      }
      return buffer.stream();
    }
  }

  private class IssueCache extends ValueCache<Stream<Issue>> {
    public IssueCache(Path fileName) {
      super("jira-issues " + fileName.toString(), 15, MergingRecord.by(Issue::getId));
    }

    @Override
    protected Stream<Issue> fetch(Instant lastUpdated) throws Exception {
      if (client == null) {
        return Stream.empty();
      }
      final String jql =
          String.format(
              "updated >= '%s' AND project = %s",
              FORMAT.format(lastUpdated.atZone(ZoneId.systemDefault())), projectKey);
      final List<Issue> buffer = new ArrayList<>();
      for (int page = 0; true; page++) {
        final SearchResult results =
            client.getSearchClient().searchJql(jql, 500, 500 * page, FIELDS_STANDARD).claim();
        for (final Issue issue : results.getIssues()) {
          buffer.add(issue);
        }
        if (buffer.size() >= results.getTotal()) {
          break;
        }
      }
      return buffer.stream();
    }
  }

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

  private static class JiraActionFilter {
    private final String assignee;
    private final FilterJson filter;
    private final String key;
    private final String summary;

    private JiraActionFilter(FilterJson filter, String key, String summary, String assignee) {
      this.filter = filter;
      this.key = key;
      this.summary = summary;
      this.assignee = assignee;
    }

    <F> Pair<String, F> process(String name, FilterBuilder<F> builder) {
      return new Pair<>(
          name.replace("{key}", key).replace("{summary}", summary).replace("{assignee}", assignee),
          filter.convert(builder));
    }
  }

  protected static final String EXTENSION = ".jira";
  private static final Set<String> FIELDS_FILTERS =
      Stream.of(
              IssueFieldId.SUMMARY_FIELD,
              IssueFieldId.DESCRIPTION_FIELD,
              IssueFieldId.ASSIGNEE_FIELD)
          .map(x -> x.id)
          .collect(Collectors.toSet());
  private static final Set<String> FIELDS_STANDARD =
      Stream.of(
              IssueFieldId.SUMMARY_FIELD,
              IssueFieldId.ISSUE_TYPE_FIELD,
              IssueFieldId.CREATED_FIELD,
              IssueFieldId.UPDATED_FIELD,
              IssueFieldId.PROJECT_FIELD,
              IssueFieldId.STATUS_FIELD,
              IssueFieldId.DESCRIPTION_FIELD,
              IssueFieldId.LABELS_FIELD)
          .map(x -> x.id)
          .collect(Collectors.toSet());
  private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm");
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private JiraRestClient client;

  private List<String> closeActions = Collections.emptyList();

  private List<String> closedStatuses = Collections.emptyList();
  private final Supplier<JiraConnection> definer;
  private final FilterCache filters;
  private final IssueCache issues;
  private String passwordFile;

  private String projectKey = "FAKE";

  private List<String> reopenActions = Collections.emptyList();

  private String url;

  private String user;

  public JiraConnection(Path fileName, String instanceName, Definer<JiraConnection> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    issues = new IssueCache(fileName);
    filters = new FilterCache(fileName);
    this.definer = definer;
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
  public void configuration(SectionRenderer renderer) throws XMLStreamException {
    if (url != null) {
      renderer.link("URL", url, url);
    }
    renderer.line("Project", projectKey);
    renderer.lineSpan("Last Cache Update", issues.lastUpdated());
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

  @ShesmuMethod(
      description =
          "Count the number of open or closed tickets from the JIRA project defined in {file}.")
  public long count_tickets_$(
      @ShesmuParameter(description = "keyword") String keyword,
      @ShesmuParameter(description = "is ticket open") boolean open) {
    return issues().filter(new IssueFilter(keyword, open)).count();
  }

  public void invalidate() {
    issues.invalidate();
  }

  public Stream<Issue> issues() {
    return issues.get();
  }

  public String projectKey() {
    return projectKey;
  }

  @ShesmuMethod(
      type = "at2ss",
      description =
          "Get the ticket summary and descriptions from the JIRA project defined in {file}.")
  public Set<Tuple> query_tickets_$(
      @ShesmuParameter(description = "keyword") String keyword,
      @ShesmuParameter(description = "is ticket open") boolean open) {
    return issues()
        .filter(new IssueFilter(keyword, open))
        .map(issue -> new Tuple(issue.getKey(), issue.getSummary()))
        .collect(Collectors.toSet());
  }

  public Stream<String> reopenActions() {
    return reopenActions.stream();
  }

  @ShesmuAction(description = "Closes any JIRA tickets with a matching summary. Defined in {file}.")
  public ResolveTicket resolve_ticket_$() {
    return new ResolveTicket(definer);
  }

  @Override
  public Stream<String> services() {
    return Stream.of("jira", projectKey);
  }

  @ShesmuAction(description = "Opens (or re-opens) a JIRA ticket. Defined in {file}.")
  public FileTicket ticket_$() {
    return new FileTicket(definer);
  }

  @Override
  public Optional<Integer> update(Configuration config) {
    url = config.getUrl();
    try (Scanner passwordScanner =
        new Scanner(fileName().getParent().resolve(Paths.get(config.getPasswordFile())))) {
      client =
          new AsynchronousJiraRestClientFactory()
              .createWithBasicHttpAuthentication(
                  new URI(config.getUrl()), config.getUser(), passwordScanner.nextLine());
      projectKey = config.getProjectKey();
      user = config.getUser();
      passwordFile = config.getPasswordFile();
      closedStatuses = config.getClosedStatuses();
      closeActions = config.getCloseActions();
      reopenActions = config.getReopenActions();
      issues.invalidate();
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public String url() {
    return url;
  }
}
