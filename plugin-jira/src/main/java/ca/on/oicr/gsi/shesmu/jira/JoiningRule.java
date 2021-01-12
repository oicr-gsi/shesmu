package ca.on.oicr.gsi.shesmu.jira;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.ActionState;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilter;
import ca.on.oicr.gsi.shesmu.plugin.filter.ActionFilterBuilder;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Rules for combining prepared filters with ones discovered from another source (e.g., JIRA) */
public enum JoiningRule {
  /** Take the base filter and remove all the accessory filters */
  ALL_EXCEPT {
    @Override
    public <F> Stream<Pair<String, F>> join(
        String baseName,
        F baseFilters,
        Stream<JiraConnection.JiraActionFilter> accessoryFilters,
        ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
      return ActionFilter.joinAllExcept(
          baseName, baseFilters, accessoryFilters.map(f -> f.process(baseName, builder)), builder);
    }
  },
  /** Take the base filter and intersect it with the union of all accessory filters */
  ALL_AND {
    @Override
    public <F> Stream<Pair<String, F>> join(
        String baseName,
        F baseFilters,
        Stream<JiraConnection.JiraActionFilter> accessoryFilters,
        ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
      return ActionFilter.joinAllAnd(
          baseName, baseFilters, accessoryFilters.map(f -> f.process(baseName, builder)), builder);
    }
  },
  /**
   * Take each accessory filter and produce the intersection of the base filter and the accessory
   * filter
   */
  EACH_AND {
    @Override
    public <F> Stream<Pair<String, F>> join(
        String baseName,
        F baseFilters,
        Stream<JiraConnection.JiraActionFilter> accessoryFilters,
        ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
      return ActionFilter.joinEachAnd(
          baseName, baseFilters, accessoryFilters.map(f -> f.process(baseName, builder)), builder);
    }
  },
  BY_ASSIGNEE {
    @Override
    public <F> Stream<Pair<String, F>> join(
        String baseName,
        F baseFilters,
        Stream<JiraConnection.JiraActionFilter> accessoryFilters,
        ActionFilterBuilder<F, ActionState, String, Instant, Long> builder) {
      return accessoryFilters
          .collect(Collectors.groupingBy(JiraConnection.JiraActionFilter::assignee))
          .entrySet()
          .stream()
          .flatMap(
              entry ->
                  ActionFilter.joinAllAnd(
                      baseName.replace("{assignee}", entry.getKey()),
                      baseFilters,
                      entry.getValue().stream().map(f -> f.process(baseName, builder)),
                      builder));
    }
  };

  /**
   * Create a list of filters based on the desired joining strategy
   *
   * @param baseName the output name if only one filter is produced
   * @param baseFilters the filters that should be fixed across all the output
   * @param accessoryFilters the set of filters that are provided from a dynamic location; each one
   *     has an individual label
   * @param builder the filter builder
   * @param <F> the type of filter output
   * @return a stream of output; this may contain one item or many depending on the joining rule
   */
  public abstract <F> Stream<Pair<String, F>> join(
      String baseName,
      F baseFilters,
      Stream<JiraConnection.JiraActionFilter> accessoryFilters,
      ActionFilterBuilder<F, ActionState, String, Instant, Long> builder);
}
