package ca.on.oicr.gsi.shesmu.mongo;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mongodb.client.FindIterable;
import org.bson.BsonValue;
import org.bson.Document;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "filter", value = OperationBuilder.FilterOperation.class),
  @JsonSubTypes.Type(name = "limit", value = OperationBuilder.LimitOperation.class),
  @JsonSubTypes.Type(name = "max", value = OperationBuilder.MaxOperation.class),
  @JsonSubTypes.Type(name = "min", value = OperationBuilder.MinOperation.class),
  @JsonSubTypes.Type(name = "projection", value = OperationBuilder.ProjectionOperation.class),
  @JsonSubTypes.Type(name = "skip", value = OperationBuilder.SkipOperation.class),
  @JsonSubTypes.Type(name = "sort", value = OperationBuilder.SortOperation.class)
})
public abstract class OperationBuilder {
  public static class FilterOperation extends OperationBuilder {
    private QueryBuilder filter;

    @Override
    public FindIterable<Document> apply(FindIterable<Document> input, BsonValue... arguments) {
      return input.filter(filter.buildRoot(arguments));
    }

    public QueryBuilder getFilter() {
      return filter;
    }

    public void setFilter(QueryBuilder filter) {
      this.filter = filter;
    }
  }

  public static class LimitOperation extends OperationBuilder {
    private int limit;

    @Override
    public FindIterable<Document> apply(FindIterable<Document> input, BsonValue... arguments) {
      return input.limit(limit);
    }

    public int getLimit() {
      return limit;
    }

    public void setLimit(int limit) {
      this.limit = limit;
    }
  }

  public static class MaxOperation extends OperationBuilder {
    private QueryBuilder comparator;

    @Override
    public FindIterable<Document> apply(FindIterable<Document> input, BsonValue... arguments) {
      return input.max(comparator.buildRoot(arguments));
    }

    public QueryBuilder getComparator() {
      return comparator;
    }

    public void setComparator(QueryBuilder comparator) {
      this.comparator = comparator;
    }
  }

  public static class MinOperation extends OperationBuilder {
    private QueryBuilder comparator;

    @Override
    public FindIterable<Document> apply(FindIterable<Document> input, BsonValue... arguments) {
      return input.min(comparator.buildRoot(arguments));
    }

    public QueryBuilder getComparator() {
      return comparator;
    }

    public void setComparator(QueryBuilder comparator) {
      this.comparator = comparator;
    }
  }

  public static class ProjectionOperation extends OperationBuilder {
    private QueryBuilder projection;

    @Override
    public FindIterable<Document> apply(FindIterable<Document> input, BsonValue... arguments) {
      return input.projection(projection.buildRoot(arguments));
    }

    public QueryBuilder getProjection() {
      return projection;
    }

    public void setProjection(QueryBuilder projection) {
      this.projection = projection;
    }
  }

  public static class SkipOperation extends OperationBuilder {
    private int skip;

    @Override
    public FindIterable<Document> apply(FindIterable<Document> input, BsonValue... arguments) {
      return input.skip(skip);
    }

    public int getSkip() {
      return skip;
    }

    public void setSkip(int skip) {
      this.skip = skip;
    }
  }

  public static class SortOperation extends OperationBuilder {
    private QueryBuilder comparator;

    @Override
    public FindIterable<Document> apply(FindIterable<Document> input, BsonValue... arguments) {
      return input.sort(comparator.buildRoot(arguments));
    }

    public QueryBuilder getComparator() {
      return comparator;
    }

    public void setComparator(QueryBuilder comparator) {
      this.comparator = comparator;
    }
  }

  public abstract FindIterable<Document> apply(
      FindIterable<Document> input, BsonValue... arguments);
}
