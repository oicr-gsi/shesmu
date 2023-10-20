package ca.on.oicr.gsi.shesmu.jira;

import static ca.on.oicr.gsi.shesmu.jira.JiraConnection.MAPPER;

import com.fasterxml.jackson.databind.JsonNode;

public enum JiraVersion {
  V2("2") {
    @Override
    public JsonNode createDocument(String inputText) {
      return MAPPER.valueToTree(inputText);
    }
  },
  V3("3") {
    @Override
    public JsonNode createDocument(String inputText) {
      final var descriptionNode = MAPPER.createObjectNode();
      descriptionNode.put("type", "doc");
      descriptionNode.put("version", 1);
      final var paragraph = descriptionNode.putArray("content").addObject();
      paragraph.put("type", "paragraph");
      final var text = paragraph.putArray("content").addObject();
      text.put("type", "text");
      text.put("text", inputText);
      return descriptionNode;
    }
  };
  private final String slug;

  JiraVersion(String slug) {
    this.slug = slug;
  }

  public abstract JsonNode createDocument(String inputText);

  public String slug() {
    return slug;
  }
}
