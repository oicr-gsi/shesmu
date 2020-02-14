package ca.on.oicr.gsi.shesmu.plugin.filter;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = FilterAdded.class, name = "added"),
  @JsonSubTypes.Type(value = FilterAddedAgo.class, name = "addedago"),
  @JsonSubTypes.Type(value = FilterAnd.class, name = "and"),
  @JsonSubTypes.Type(value = FilterChecked.class, name = "checked"),
  @JsonSubTypes.Type(value = FilterCheckedAgo.class, name = "checkedago"),
  @JsonSubTypes.Type(value = FilterExternal.class, name = "external"),
  @JsonSubTypes.Type(value = FilterExternalAgo.class, name = "externalago"),
  @JsonSubTypes.Type(value = FilterIds.class, name = "id"),
  @JsonSubTypes.Type(value = FilterOr.class, name = "or"),
  @JsonSubTypes.Type(value = FilterRegex.class, name = "regex"),
  @JsonSubTypes.Type(value = FilterSourceFile.class, name = "sourcefile"),
  @JsonSubTypes.Type(value = FilterSourceLocation.class, name = "sourcelocation"),
  @JsonSubTypes.Type(value = FilterStatus.class, name = "status"),
  @JsonSubTypes.Type(value = FilterStatusChanged.class, name = "statuschanged"),
  @JsonSubTypes.Type(value = FilterStatusChangedAgo.class, name = "statuschangedago"),
  @JsonSubTypes.Type(value = FilterTag.class, name = "tag"),
  @JsonSubTypes.Type(value = FilterText.class, name = "text"),
  @JsonSubTypes.Type(value = FilterType.class, name = "type")
})
public abstract class FilterJson {

  public static Optional<FilterJson> extractFromText(String text, ObjectMapper mapper) {
    final Set<String> actionIds = new TreeSet<>();
    final List<FilterJson> filters = new ArrayList<>();
    final Matcher actionMatcher = ACTION_ID.matcher(text);
    while (actionMatcher.find()) {
      actionIds.add("shesmu:" + actionMatcher.group(1).toUpperCase());
    }
    if (!actionIds.isEmpty()) {
      final FilterIds idFilter = new FilterIds();
      idFilter.setIds(new ArrayList<>(actionIds));
      filters.add(idFilter);
    }
    final Matcher filterMatcher = SEARCH.matcher(text);
    while (filterMatcher.find()) {
      try {
        final FilterJson[] current =
            mapper.readValue(
                Base64.getDecoder().decode(filterMatcher.group(1)), FilterJson[].class);
        switch (current.length) {
          case 0:
            break;
          case 1:
            filters.add(current[0]);
            break;
          default:
            final FilterAnd and = new FilterAnd();
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
        final FilterOr or = new FilterOr();
        or.setFilters(filters.stream().toArray(FilterJson[]::new));
        return Optional.of(or);
    }
  }

  private static final Pattern ACTION_ID = Pattern.compile("shesmu:([0-9a-fA-F]{40})");
  private static final Pattern SEARCH =
      Pattern.compile(
          "shesmusearch:((?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=|))");
  private boolean negate;

  public abstract <F> F convert(FilterBuilder<F> f);

  public boolean isNegate() {
    return negate;
  }

  protected <F> F maybeNegate(F filter, FilterBuilder<F> filterBuilder) {
    return negate ? filterBuilder.negate(filter) : filter;
  }

  public void setNegate(boolean negate) {
    this.negate = negate;
  }
}
