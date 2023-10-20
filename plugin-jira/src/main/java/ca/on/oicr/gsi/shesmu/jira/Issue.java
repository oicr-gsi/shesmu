package ca.on.oicr.gsi.shesmu.jira;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

@JsonIgnoreProperties(ignoreUnknown = true)
public final class Issue {
  public record Field<T>(String name, Class<T> type) {}

  public static Field<User> ASSIGNEE = new Field<>("assignee", User.class);
  public static Field<JsonNode> DESCRIPTION = new Field<>("description", JsonNode.class);
  public static Field<String[]> LABELS = new Field<>("labels", String[].class);
  public static final Field<Project> PROJECT = new Field<>("project", Project.class);
  public static final Field<Status> STATUS = new Field<>("status", Status.class);
  public static Field<String> SUMMARY = new Field<>("summary", String.class);
  public static final Field<IssueType> TYPE = new Field<>("issuetype", IssueType.class);
  public static final Field<Date> UPDATED = new Field<>("updated", Date.class);
  private Map<String, JsonNode> fields = new TreeMap<>();
  private String id;
  private String key;
  private String self;

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Issue issue = (Issue) o;
    return Objects.equals(fields, issue.fields)
        && Objects.equals(id, issue.id)
        && Objects.equals(key, issue.key);
  }

  public <T> Optional<T> extract(Field<T> field) {
    if (fields.containsKey(field.name())) {
      return Optional.ofNullable(
          JiraConnection.MAPPER.convertValue(fields.get(field.name()), field.type()));
    } else {
      return Optional.empty();
    }
  }

  public Map<String, JsonNode> getFields() {
    return fields;
  }

  public String getId() {
    return id;
  }

  public String getKey() {
    return key;
  }

  public String getSelf() {
    return self;
  }

  @Override
  public int hashCode() {
    return Objects.hash(fields, id, key);
  }

  public <T> void put(Field<T> field, T value) {
    fields.put(field.name, JiraConnection.MAPPER.convertValue(value, JsonNode.class));
  }

  public void setFields(Map<String, JsonNode> fields) {
    this.fields = fields;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public void setSelf(String self) {
    this.self = self;
  }
}
