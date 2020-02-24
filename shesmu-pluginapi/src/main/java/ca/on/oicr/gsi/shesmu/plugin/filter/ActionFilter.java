package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.Pair;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = ActionFilterAdded.class, name = "added"),
  @JsonSubTypes.Type(value = ActionFilterAddedAgo.class, name = "addedago"),
  @JsonSubTypes.Type(value = ActionFilterAnd.class, name = "and"),
  @JsonSubTypes.Type(value = ActionFilterChecked.class, name = "checked"),
  @JsonSubTypes.Type(value = ActionFilterCheckedAgo.class, name = "checkedago"),
  @JsonSubTypes.Type(value = ActionFilterExternal.class, name = "external"),
  @JsonSubTypes.Type(value = ActionFilterExternalAgo.class, name = "externalago"),
  @JsonSubTypes.Type(value = ActionFilterIds.class, name = "id"),
  @JsonSubTypes.Type(value = ActionFilterOr.class, name = "or"),
  @JsonSubTypes.Type(value = ActionFilterRegex.class, name = "regex"),
  @JsonSubTypes.Type(value = ActionFilterSourceFile.class, name = "sourcefile"),
  @JsonSubTypes.Type(value = ActionFilterSourceLocation.class, name = "sourcelocation"),
  @JsonSubTypes.Type(value = ActionFilterStatus.class, name = "status"),
  @JsonSubTypes.Type(value = ActionFilterStatusChanged.class, name = "statuschanged"),
  @JsonSubTypes.Type(value = ActionFilterStatusChangedAgo.class, name = "statuschangedago"),
  @JsonSubTypes.Type(value = ActionFilterTag.class, name = "tag"),
  @JsonSubTypes.Type(value = ActionFilterText.class, name = "text"),
  @JsonSubTypes.Type(value = ActionFilterType.class, name = "type")
})
public abstract class ActionFilter {
  public static Optional<ActionFilter> extractFromText(String text, ObjectMapper mapper) {
    final Set<String> actionIds = new TreeSet<>();
    final List<ActionFilter> filters = new ArrayList<>();
    final Matcher actionMatcher = ACTION_ID.matcher(text);
    while (actionMatcher.find()) {
      actionIds.add("shesmu:" + actionMatcher.group(1).toUpperCase());
    }
    if (!actionIds.isEmpty()) {
      final ActionFilterIds idFilter = new ActionFilterIds();
      idFilter.setIds(new ArrayList<>(actionIds));
      filters.add(idFilter);
    }
    final Matcher filterMatcher = SEARCH.matcher(text);
    while (filterMatcher.find()) {
      try {
        final ActionFilter[] current =
            mapper.readValue(
                Base64.getDecoder().decode(filterMatcher.group(1)), ActionFilter[].class);
        switch (current.length) {
          case 0:
            break;
          case 1:
            filters.add(current[0]);
            break;
          default:
            final ActionFilterAnd and = new ActionFilterAnd();
            and.setFilters(current);
            filters.add(and);
        }
      } catch (Exception e) {
        // That was some hot garbage we're going to ignore
      }
    }
    switch (filters.size()) {
      case 0:
        return Optional.empty();
      case 1:
        return Optional.of(filters.get(0));
      default:
        final ActionFilterOr or = new ActionFilterOr();
        or.setFilters(filters.stream().toArray(ActionFilter[]::new));
        return Optional.of(or);
    }
  }

  /** Take the base filter and intersect it with the union of all accessory filters */
  public static <F> Stream<Pair<String, F>> joinAllAnd(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F> builder) {
    return Stream.of(
        new Pair<>(
            baseName,
            builder.and(Stream.of(baseFilters, builder.or(accessoryFilters.map(Pair::second))))));
  }

  /** Take the base filter and remove all the accessory filters */
  public static <F> Stream<Pair<String, F>> joinAllExcept(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F> builder) {
    return Stream.of(
        new Pair<>(
            baseName,
            builder.and(
                Stream.concat(
                    Stream.of(baseFilters),
                    accessoryFilters.map(Pair::second).map(builder::negate)))));
  }

  /**
   * Take each accessory filter and produce the intersection of the base filter and the accessory
   * filter
   */
  public static <F> Stream<Pair<String, F>> joinEachAnd(
      String baseName,
      F baseFilters,
      Stream<Pair<String, F>> accessoryFilters,
      ActionFilterBuilder<F> builder) {
    return accessoryFilters.map(
        p -> new Pair<>(p.first(), builder.and(Stream.of(p.second(), baseFilters))));
  }

  private static final Pattern ACTION_ID = Pattern.compile("shesmu:([0-9a-fA-F]{40})");
  private static final Pattern SEARCH =
      Pattern.compile(
          "shesmusearch:((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|))");
  private boolean negate;

  public abstract <F> F convert(ActionFilterBuilder<F> f);

  public boolean isNegate() {
    return negate;
  }

  protected <F> F maybeNegate(F filter, ActionFilterBuilder<F> filterBuilder) {
    return negate ? filterBuilder.negate(filter) : filter;
  }

  public void setNegate(boolean negate) {
    this.negate = negate;
  }
}
