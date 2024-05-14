package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
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
        Supplier<JiraConnection> definer,
        List<Issue> issues,
        String summary,
        Set<String> labels,
        String type,
        Consumer<Issue> bestMatch)
        throws URISyntaxException, IOException, InterruptedException {
      JiraConnection connection = definer.get();
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
                .append("\nConnection: ")
                .append(connection);
            Map<String, String> lokiLabels = new HashMap<>();
            lokiLabels.put("type", "jira");
            lokiLabels.put("issue", issue.getKey());
            lokiLabels.put("verb", "close");
            ((Definer<JiraConnection>) definer).log(errorBuilder.toString(), lokiLabels);
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

    @Override
    public String toString() {
      return "Close JIRA Issue with comment " + comment;
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
        Supplier<JiraConnection> definer,
        List<Issue> issues,
        String summary,
        Set<String> labels,
        String type,
        Consumer<Issue> bestMatch)
        throws URISyntaxException, IOException, InterruptedException {
      JiraConnection connection = definer.get();
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

    @Override
    public String toString() {
      StringBuilder writeOut = new StringBuilder();
      writeOut
          .append("Re/open JIRA Issue with description '")
          .append(description)
          .append("', reopen with comment '")
          .append(comment)
          .append("'");
      assignee.ifPresent(s -> writeOut.append(", with assignee '").append(s));
      return writeOut.toString();
    }
  }

  public abstract ActionState perform(
      Supplier<JiraConnection> definer,
      List<Issue> issues,
      String summary,
      Set<String> labels,
      String type,
      Consumer<Issue> bestMatch)
      throws URISyntaxException, IOException, InterruptedException;

  public abstract boolean search(Pattern query);

  public abstract String verb();
}
