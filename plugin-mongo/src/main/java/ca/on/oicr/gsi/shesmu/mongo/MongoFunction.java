package ca.on.oicr.gsi.shesmu.mongo;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoIterable;
import java.util.List;
import java.util.stream.Collectors;
import org.bson.BsonValue;
import org.bson.Document;
import org.bson.conversions.Bson;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(name = "aggregate", value = MongoFunction.MongoAggregateFunction.class),
  @JsonSubTypes.Type(name = "find", value = MongoFunction.MongoFindFunction.class)
})
public abstract class MongoFunction {
  public static class MongoAggregateFunction extends MongoFunction {
    private List<QueryBuilder> operations;
    private String collection;

    public String getCollection() {
      return collection;
    }

    public List<QueryBuilder> getOperations() {
      return operations;
    }

    @Override
    protected MongoIterable<Document> run(MongoClient client, BsonValue... arguments) {
      final List<Bson> operations =
          this.operations
              .stream()
              .map(operation -> operation.buildRoot(arguments))
              .collect(Collectors.toList());
      return getCollection() == null
          ? client.getDatabase(getDatabase()).aggregate(operations)
          : client.getDatabase(getDatabase()).getCollection(getCollection()).aggregate(operations);
    }

    public void setCollection(String collection) {
      this.collection = collection;
    }

    public void setOperations(List<QueryBuilder> operations) {
      this.operations = operations;
    }
  }

  public static class MongoFindFunction extends MongoFunction {
    private String collection;
    private QueryBuilder criteria;
    private List<OperationBuilder> operations;

    public String getCollection() {
      return collection;
    }

    public QueryBuilder getCriteria() {
      return criteria;
    }

    public List<OperationBuilder> getOperations() {
      return operations;
    }

    @Override
    protected MongoIterable<Document> run(MongoClient client, BsonValue... arguments) {
      FindIterable<Document> iterable =
          client
              .getDatabase(getDatabase())
              .getCollection(getCollection())
              .find(criteria.buildRoot(arguments));
      for (final OperationBuilder operation : operations) {
        iterable = operation.apply(iterable, arguments);
      }
      return iterable;
    }

    public void setCollection(String collection) {
      this.collection = collection;
    }

    public void setCriteria(QueryBuilder criteria) {
      this.criteria = criteria;
    }

    public void setOperations(List<OperationBuilder> operations) {
      this.operations = operations;
    }
  }

  private String database;
  private String description;
  private List<ParameterConverter> parameters;
  private ReturnConverter resultType;
  private ResultSelector selector;
  private int ttl;

  public final Object apply(MongoClient client, Tuple arguments) {
    final BsonValue[] values = new BsonValue[parameters.size()];
    for (int i = 0; i < parameters.size(); i++) {
      values[i] = parameters.get(i).pack(arguments.get(i));
    }
    return selector.process(run(client, values), resultType);
  }

  public String getDatabase() {
    return database;
  }

  public String getDescription() {
    return description;
  }

  public List<ParameterConverter> getParameters() {
    return parameters;
  }

  public ReturnConverter getResultType() {
    return resultType;
  }

  public ResultSelector getSelector() {
    return selector;
  }

  public int getTtl() {
    return ttl;
  }

  protected abstract MongoIterable<Document> run(MongoClient client, BsonValue... arguments);

  public void setDatabase(String database) {
    this.database = database;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public void setParameters(List<ParameterConverter> parameters) {
    this.parameters = parameters;
  }

  public void setResultType(ReturnConverter resultType) {
    this.resultType = resultType;
  }

  public void setSelector(ResultSelector selector) {
    this.selector = selector;
  }

  public void setTtl(int ttl) {
    this.ttl = ttl;
  }
}
