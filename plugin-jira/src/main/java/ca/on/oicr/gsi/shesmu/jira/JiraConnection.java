package ca.on.oicr.gsi.shesmu.jira;

import static ca.on.oicr.gsi.shesmu.jira.IssueAction.STANDARD_LABELS;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.jira.Issue.Field;
import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;
import ca.on.oicr.gsi.shesmu.plugin.LogLevel;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.action.ShesmuAction;
import ca.on.oicr.gsi.shesmu.plugin.cache.KeyValueCache;
import ca.on.oicr.gsi.shesmu.plugin.cache.ReplacingRecord;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import ca.on.oicr.gsi.shesmu.plugin.filter.ExportSearch;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuMethod;
import ca.on.oicr.gsi.shesmu.plugin.functions.ShesmuParameter;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonBodyHandler;
import ca.on.oicr.gsi.shesmu.plugin.json.JsonPluginFile;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.status.SectionRenderer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class JiraConnection extends JsonPluginFile<Configuration> {
  private class IssueCache extends KeyValueCache<String, Stream<Issue>> {
    public IssueCache(Path fileName) {
      super("jira-issue " + fileName.toString(), 15, ReplacingRecord::new);
    }

    @Override
    protected Stream<Issue> fetch(String jql, Instant lastUpdated)
        throws URISyntaxException, IOException, InterruptedException {
      return search(jql, FIELDS).stream();
    }
  }

  static class JiraActionFilter {
    private final String assignee;
    private final ActionFilter filter;
    private final String key;
    private final String summary;

    private JiraActionFilter(ActionFilter filter, String key, String summary, String assignee) {
      this.filter = filter;
      this.key = key;
      this.summary = summary;
      this.assignee = assignee;
    }

    public String assignee() {
      return assignee;
    }

    <F> Pair<String, F> process(
        String name, ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
      return new Pair<>(
          name.replace("{key}", key).replace("{summary}", summary).replace("{assignee}", assignee),
          filter.convert(builder));
    }
  }

  static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static final Set<String> FIELDS =
      Stream.of(
              Issue.ASSIGNEE,
              Issue.DESCRIPTION,
              Issue.LABELS,
              Issue.STATUS,
              Issue.SUMMARY,
              Issue.UPDATED)
          .map(Field::name)
          .collect(Collectors.toSet());
  private static final Imyhat ISSUE_IMYHAT =
      new ObjectImyhat(
          Stream.of(
              new Pair<>("assignee", Imyhat.STRING.asOptional()),
              new Pair<>("key", Imyhat.STRING),
              new Pair<>("labels", Imyhat.STRING.asList()),
              new Pair<>("status", Imyhat.STRING),
              new Pair<>("summary", Imyhat.STRING),
              new Pair<>("updated", Imyhat.DATE.asOptional())));
  static final ObjectMapper MAPPER = new ObjectMapper();

  /**
   * JIRA uses an Atlassian Document specification that is needlessly complicated for Shesmu's
   * needs, so we don't bother to understand the schema.
   *
   * @param jsonNode the node to dissect
   * @return any text found
   */
  private static Optional<String> unmangleDocument(JsonNode jsonNode) {
    if (jsonNode.isTextual()) {
      return Optional.of(jsonNode.asText());
    } else if (jsonNode.isObject()) {
      if (jsonNode.has("content")) {
        return unmangleDocument(jsonNode.get("content"));
      } else if (jsonNode.has("text") && jsonNode.get("text").isTextual()) {
        return Optional.of(jsonNode.get("text").asText());
      } else {
        return Optional.empty();
      }

    } else if (jsonNode.isArray()) {
      return Optional.of(
              StreamSupport.stream(
                      Spliterators.spliteratorUnknownSize(jsonNode.iterator(), 0), false)
                  .flatMap(node -> unmangleDocument(node).stream())
                  .collect(Collectors.joining("\n")))
          .filter(s -> !s.isBlank());

    } else {
      return Optional.empty();
    }
  }

  private Optional<String> authenticationHeader = Optional.empty();
  private List<String> closedStatuses = List.of();
  private Map<String, JsonNode> defaultFieldValues = Map.of();
  private final Supplier<JiraConnection> definer;
  private String issueTypeId;
  private String issueTypeName;
  private final IssueCache issues;
  private String projectId;
  private String projectKey = "FAKE";
  private List<Search> searches = List.of();
  private String url;
  private JiraVersion version = JiraVersion.V2;

  public JiraConnection(Path fileName, String instanceName, Definer<JiraConnection> definer) {
    super(fileName, instanceName, MAPPER, Configuration.class);
    issues = new IssueCache(fileName);
    this.definer = definer;
  }

  public void addLabels(String issueUrl, TreeSet<String> missingLabels)
      throws IOException, URISyntaxException, InterruptedException {
    final var request = MAPPER.createObjectNode();
    missingLabels.forEach(
        request.putObject("update").putArray("labels").addObject().putArray("add")::add);

    final var builder = HttpRequest.newBuilder(new URI(issueUrl));
    authenticationHeader.ifPresent(header -> builder.header("Authorization", header));
    CLIENT
        .send(
            builder
                .header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                .build(),
            BodyHandlers.discarding())
        .body();
  }

  public Stream<String> closedStatuses() {
    return closedStatuses.stream();
  }

  @Override
  public void configuration(SectionRenderer renderer) {
    if (url != null) {
      renderer.link("URL", url, url);
    }
    renderer.line("Project", projectKey);
    renderer.line("Closed Statuses", String.join(" | ", closedStatuses));
  }

  Issue createIssue(
      String summary,
      String description,
      Optional<String> assignee,
      Set<String> labels,
      String type)
      throws URISyntaxException, IOException, InterruptedException {
    final var request = new Issue();
    final var project = new Project();
    project.setId(projectId);
    request.put(Issue.PROJECT, project);
    request.put(Issue.SUMMARY, summary);
    request.put(Issue.DESCRIPTION, version.createDocument(description));
    assignee.ifPresent(
        a -> {
          final var user = new User();
          user.setName(a);
          request.put(Issue.ASSIGNEE, user);
        });
    request.put(
        Issue.LABELS,
        Stream.of(STANDARD_LABELS, labels)
            .flatMap(Collection::stream)
            .distinct()
            .toArray(String[]::new));
    final var issueType = new IssueType();
    issueType.setName(type);
    request.put(Issue.TYPE, issueType);

    IssueAction.issueCreates.labels(url, projectKey).inc();
    final var builder =
        HttpRequest.newBuilder(new URI(String.format("%s/rest/api/%s/issue", url, version.slug())));
    authenticationHeader.ifPresent(header -> builder.header("Authorization", header));
    final var result =
        CLIENT.send(
            builder
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                .build(),
            BodyHandlers.ofString());
    if (result.statusCode() / 100 != 2) {
      throw new RuntimeException(
          String.format(
              "JIRA issue creation failed with %d: %s", result.statusCode(), result.body()));
    }
    invalidate();
    return MAPPER.readValue(result.body(), Issue.class);
  }

  @Override
  public <T> Stream<T> exportSearches(ExportSearch<T> builder) {
    return projectId == null || issueTypeName == null
        ? Stream.empty()
        : Stream.of(
            builder.linkWithUrlSearch(
                FrontEndIcon.FILE_PLUS,
                String.format("File %s in %s", issueTypeName, projectKey),
                FrontEndIcon.FILE,
                "JIRA",
                String.format(
                    "%s/login.jsp?permissionViolation=true&page_caps=&user_role=&os_destination=%%2Fsecure%%2FCreateIssueDetails!init.jspa%%3Fpid%%3D%s%%26issuetype%%3D%s%%26summary%%3D%%26description%%3D%%250A%%250A",
                    url, projectId, issueTypeId),
                "",
                String.format(
                    "Create a new “%s” issue in JIRA project “%s”.", issueTypeName, projectKey)));
  }

  public void invalidate() {
    issues.invalidateAll();
  }

  @ShesmuAction(description = "Opens (or re-opens) or closes a JIRA issue. Defined in {file}.")
  public IssueAction issue() {
    return new IssueAction(definer);
  }

  public String projectKey() {
    return projectKey;
  }

  @ShesmuMethod(
      type = "ao6assignee$qskey$slabels$asstatus$ssummary$supdated$qd",
      description = "Search issues on the JIRA project defined in {file}.")
  public Set<Tuple> query(@ShesmuParameter(description = "the JQL to execute") String jql) {
    return issues
        .get(
            jql.isBlank()
                ? String.format("project = %s", projectKey)
                : String.format("(%s) AND project = %s", jql, projectKey))
        .map(
            issue ->
                new Tuple(
                    issue.extract(Issue.ASSIGNEE).map(User::getName),
                    issue.getKey(),
                    issue
                        .extract(Issue.LABELS)
                        .<Set<String>>map(
                            l -> Stream.of(l).collect(Collectors.toCollection(TreeSet::new)))
                        .orElse(Set.of()),
                    issue.extract(Issue.STATUS).map(Status::name).orElse("<missing>"),
                    issue.extract(Issue.SUMMARY).orElse("<missing>"),
                    issue.extract(Issue.UPDATED).map(Date::toInstant)))
        .collect(Collectors.toCollection(ISSUE_IMYHAT::newSet));
  }

  List<Issue> search(String jql, Set<String> fields)
      throws URISyntaxException, IOException, InterruptedException {
    if (url == null) {
      return List.of();
    }
    final var buffer = new ArrayList<Issue>();
    final var request = new SearchRequest();
    request.setMaxResults(500);
    request.setFields(fields);
    request.setJql(jql);

    for (var page = 0; true; page++) {
      request.setStartAt(500 * page);
      final var builder =
          HttpRequest.newBuilder(
              new URI(String.format("%s/rest/api/%s/search", url, version.slug())));
      authenticationHeader.ifPresent(header -> builder.header("Authorization", header));
      final var results =
          CLIENT
              .send(
                  builder
                      .header("Content-Type", "application/json")
                      .POST(BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                      .build(),
                  new JsonBodyHandler<>(MAPPER, SearchResponse.class))
              .body()
              .get();
      buffer.addAll(results.getIssues());
      if (results.getIssues().isEmpty()) {
        break;
      }
    }
    return buffer;
  }

  @Override
  public <F> Stream<Pair<String, F>> searches(
      ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
    try {
      return searches.stream()
          .flatMap(
              search ->
                  search
                      .type()
                      .join(
                          search.name(),
                          search.filter().convert(builder),
                          issues
                              .get(search.jql())
                              .flatMap(
                                  issue -> {
                                    final var assignee =
                                        issue
                                            .extract(Issue.ASSIGNEE)
                                            .map(
                                                a ->
                                                    Objects.requireNonNullElse(
                                                        a.getDisplayName(), "Unknown"))
                                            .orElse("Unassigned");

                                    return issue
                                        .extract(Issue.DESCRIPTION)
                                        .flatMap(JiraConnection::unmangleDocument)
                                        .flatMap(
                                            description ->
                                                ActionFilter.extractFromText(description, MAPPER))
                                        .map(
                                            filter ->
                                                new JiraActionFilter(
                                                    filter,
                                                    issue.getKey(),
                                                    issue.extract(Issue.SUMMARY).orElse("N/A"),
                                                    assignee))
                                        .stream();
                                  }),
                          builder));
    } catch (Exception e) {
      e.printStackTrace();
      return Stream.empty();
    }
  }

  @Override
  public Stream<String> services() {
    return Stream.of("jira", projectKey);
  }

  boolean transition(
      Issue issue, BiFunction<Stream<String>, Predicate<String>, Boolean> matcher, String comment)
      throws URISyntaxException, IOException, InterruptedException {
    IssueAction.issueUpdates.labels(url, projectKey).inc();
    ((Definer<JiraConnection>) definer)
        .log(
            new StringBuilder("Attempting to transition issue ")
                .append(issue.getKey())
                .append(" with comment ")
                .append(comment)
                .toString(),
            LogLevel.DEBUG,
            null);
    final var builder =
        HttpRequest.newBuilder(
            new URI(
                String.format(
                    "%s/rest/api/%s/issue/%s/transitions?expand=transitions.fields",
                    url, version.slug(), issue.getId())));
    authenticationHeader.ifPresent(header -> builder.header("Authorization", header));

    final var transitions =
        CLIENT
            .send(builder.GET().build(), new JsonBodyHandler<>(MAPPER, TransitionsResponse.class))
            .body()
            .get()
            .transitions();
    ((Definer<JiraConnection>) definer)
        .log(
            new StringBuilder("Transitions available to ")
                .append(issue.getKey())
                .append(" are ")
                .append(transitions)
                .toString(),
            LogLevel.DEBUG,
            null);

    for (final var transition : transitions) {
      ((Definer<JiraConnection>) definer)
          .log(
              new StringBuilder("Attempting to apply transition ")
                  .append(transition)
                  .append(" to issue ")
                  .append(issue.getKey())
                  .append(" by matching against ")
                  .append(closedStatuses())
                  .toString(),
              LogLevel.DEBUG,
              null);
      if (matcher.apply(closedStatuses(), transition.to().name()::equalsIgnoreCase)) {
        final var request = new TransitionRequest();
        /** "fields": { "assignee": { "name": "Will" }, "resolution": { "name": "Fixed" } } */
        for (final var field : transition.fields().entrySet()) {
          if (field.getValue().required() && !field.getValue().hasDefaultValue()) {
            request
                .getFields()
                .put(
                    field.getKey(),
                    MAPPER.createObjectNode().set("name", defaultFieldValues.get(field.getKey())));
          }
        }
        request.setTransition(transition);
        ((Definer<JiraConnection>) definer)
            .log(
                new StringBuilder("Sending transition request ")
                    .append(request)
                    .append(" to ")
                    .append(
                        String.format(
                            "%s/rest/api/%s/issue/%s/transitions",
                            url, version.slug(), issue.getId()))
                    .toString(),
                LogLevel.DEBUG,
                null);
        final var requestBuilder =
            HttpRequest.newBuilder(
                new URI(
                    String.format(
                        "%s/rest/api/%s/issue/%s/transitions",
                        url, version.slug(), issue.getId())));
        authenticationHeader.ifPresent(header -> requestBuilder.header("Authorization", header));

        var transitionResult =
            CLIENT.send(
                requestBuilder
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(MAPPER.writeValueAsString(request)))
                    .build(),
                BodyHandlers.ofString());
        ((Definer<JiraConnection>) definer)
            .log(
                new StringBuilder("Got response ").append(transitionResult).toString(),
                LogLevel.DEBUG,
                null);
        if (transitionResult.statusCode() / 100 != 2) {
          StringBuilder errorBuilder = new StringBuilder();
          errorBuilder
              .append("Unable to transition issue: ")
              .append(issue.getKey())
              .append(" using any of ")
              .append(transitions)
              .append(", sent: ")
              .append(request)
              .append(" which formatted to ")
              .append(MAPPER.writeValueAsString(request))
              .append(", received: ")
              .append(transitionResult.body());
          Map<String, String> lokiLabels = new HashMap<>();
          lokiLabels.put("issue", issue.getKey());
          ((Definer<JiraConnection>) definer)
              .log(errorBuilder.toString(), LogLevel.ERROR, lokiLabels);
          return false;
        }

        final var updateComment = MAPPER.createObjectNode();
        updateComment.set("body", version.createDocument(comment));
        final var commentRequestBuilder =
            HttpRequest.newBuilder(
                new URI(
                    String.format(
                        "%s/rest/api/%s/issue/%s/comment", url, version.slug(), issue.getId())));
        authenticationHeader.ifPresent(
            header -> commentRequestBuilder.header("Authorization", header));

        ((Definer<JiraConnection>) definer)
            .log(
                new StringBuilder("Sending comment request ")
                    .append(BodyPublishers.ofString(MAPPER.writeValueAsString(updateComment)))
                    .append(" to ")
                    .append(
                        String.format(
                            "%s/rest/api/%s/issue/%s/comment", url, version.slug(), issue.getId()))
                    .toString(),
                LogLevel.DEBUG,
                null);
        var commentResult =
            CLIENT.send(
                commentRequestBuilder
                    .header("Content-Type", "application/json")
                    .POST(BodyPublishers.ofString(MAPPER.writeValueAsString(updateComment)))
                    .build(),
                BodyHandlers.ofString());
        ((Definer<JiraConnection>) definer)
            .log(
                new StringBuilder("Got response ").append(commentResult).toString(),
                LogLevel.DEBUG,
                null);
        boolean isGood = commentResult.statusCode() / 100 == 2;
        if (!isGood) {
          StringBuilder errorBuilder = new StringBuilder();
          errorBuilder
              .append("Unable to comment on issue ")
              .append(issue.getKey())
              .append(" using comment ")
              .append(comment)
              .append("\nGot ")
              .append(commentResult.body());
          Map<String, String> lokiLabels = new HashMap<>();
          lokiLabels.put("issue", issue.getKey());
          ((Definer<JiraConnection>) definer)
              .log(errorBuilder.toString(), LogLevel.ERROR, lokiLabels);
        }
        return isGood;
      }
    }
    return false;
  }

  @Override
  public Optional<Integer> update(Configuration config) {
    url = config.getUrl();
    version = config.getVersion();
    try {
      final var authentication = config.getAuthentication();
      authenticationHeader =
          authentication == null
              ? Optional.empty()
              : Optional.of(authentication.prepareAuthentication());
      projectKey = config.getProjectKey();
      closedStatuses = config.getClosedStatuses();
      searches = config.getSearches();
      defaultFieldValues = config.getDefaultFieldValues();
      issues.invalidateAll();
      final var builder =
          HttpRequest.newBuilder(
              new URI(
                  String.format(
                      "%s/rest/api/%s/project/%s",
                      url, version.slug(), URLEncoder.encode(projectKey, StandardCharsets.UTF_8))));
      authenticationHeader.ifPresent(header -> builder.header("Authorization", header));
      final var project =
          CLIENT
              .send(builder.GET().build(), new JsonBodyHandler<>(MAPPER, Project.class))
              .body()
              .get();
      projectId = Objects.requireNonNull(project.getId());
      issueTypeId = null;
      issueTypeName = null;
      if (config.getIssueType() != null) {
        for (final var issueType : project.getIssueTypes()) {
          if (issueType.getName().equals(config.getIssueType())) {
            issueTypeId = issueType.getId();
            issueTypeName = issueType.getName();
          }
        }
      }
    } catch (final Exception e) {
      e.printStackTrace();
    }
    return Optional.empty();
  }

  public String url() {
    return url;
  }

  @Override
  public String toString() {
    StringBuilder writeOut = new StringBuilder();
    writeOut
        .append("JiraConnection with closedStatuses = ")
        .append(closedStatuses)
        .append(", defaultFieldValues = ")
        .append(defaultFieldValues.entrySet())
        .append(", definer = ")
        .append(definer)
        .append(", issueTypeId = ")
        .append(issueTypeId)
        .append(", issueTypeName = ")
        .append(issueTypeName)
        .append(", cached issues = ")
        .append(issues)
        .append(", projectId = ")
        .append(projectId)
        .append(", projectKey = ")
        .append(projectKey)
        .append(", searches = ")
        .append(searches)
        .append(", url = ")
        .append(url)
        .append(", version = ")
        .append(version);
    authenticationHeader.ifPresent(s -> writeOut.append(", authenticationHeader = ").append(s));
    return writeOut.toString();
  }
}
