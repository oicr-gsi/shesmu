package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public abstract class IssueVerb {
  public static class Close extends IssueVerb {
    private final String comment;

    public Close(String comment) {
      this.comment = comment;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Close that = (Close) o;
      return Objects.equals(comment, that.comment);
    }

    @Override
    public int hashCode() {
      return Objects.hash(comment);
    }

    @Override
    public ActionState perform(
        JiraConnection connection,
        List<Issue> issues,
        String summary,
        Set<String> labels,
        String type,
        Consumer<Issue> bestMatch)
        throws URISyntaxException, IOException, InterruptedException {
      for (final var issue : issues) {
        if (issue
            .extract(Issue.STATUS)
            .map(s -> connection.closedStatuses().noneMatch(s.getName()::equalsIgnoreCase))
            .orElse(false)) {
          bestMatch.accept(issue);
          if (!connection.transition(issue, Stream::anyMatch, comment)) {
            StringBuilder errorBuilder = new StringBuilder();
            errorBuilder
                .append("Unable to transition issue ")
                .append(issue.getKey())
                .append("\nConnection: " + connection);
            if (connection != null)
              errorBuilder
                  .append("\nConnection has closedStatuses: ")
                  .append(connection.closedStatuses());
            System.err.println(errorBuilder);
            return ActionState.FAILED;
          }
        }
      }
      return ActionState.SUCCEEDED;
    }

    @Override
    public boolean search(Pattern query) {
      return query.matcher(comment).matches();
    }

    @Override
    public String verb() {
      return "Close";
    }
  }

  public static class Open extends IssueVerb {
    private final Optional<String> assignee;
    private final String comment;
    private final String description;

    public Open(Optional<String> assignee, String comment, String description) {
      this.comment = comment;
      this.description = description;
      this.assignee = assignee;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Open that = (Open) o;
      return Objects.equals(description, that.description)
          && Objects.equals(assignee, that.assignee);
    }

    @Override
    public int hashCode() {
      return Objects.hash(description, assignee);
    }

    @Override
    public ActionState perform(
        JiraConnection connection,
        List<Issue> issues,
        String summary,
        Set<String> labels,
        String type,
        Consumer<Issue> bestMatch)
        throws URISyntaxException, IOException, InterruptedException {
      if (issues.stream()
          .anyMatch(
              issue ->
                  issue
                      .extract(Issue.STATUS)
                      .map(
                          status -> {
                            final var isOpen =
                                connection
                                    .closedStatuses()
                                    .noneMatch(status.getName()::equalsIgnoreCase);
                            if (isOpen) {
                              bestMatch.accept(issue);
                            }
                            return isOpen;
                          })
                      .orElse(false))) {
        return ActionState.SUCCEEDED;
      }

      for (final var issue : issues) {
        if (connection.transition(issue, Stream::noneMatch, comment)) {
          bestMatch.accept(issue);
          return ActionState.SUCCEEDED;
        }
      }
      bestMatch.accept(connection.createIssue(summary, description, assignee, labels, type));

      return ActionState.SUCCEEDED;
    }

    @Override
    public boolean search(Pattern query) {
      return query.matcher(description).matches()
          || assignee.map(a -> query.matcher(a).matches()).orElse(false);
    }

    @Override
    public String verb() {
      return "Open";
    }
  }

  public abstract ActionState perform(
      JiraConnection connection,
      List<Issue> issues,
      String summary,
      Set<String> labels,
      String type,
      Consumer<Issue> bestMatch)
      throws URISyntaxException, IOException, InterruptedException;

  public abstract boolean search(Pattern query);

  public abstract String verb();
}
