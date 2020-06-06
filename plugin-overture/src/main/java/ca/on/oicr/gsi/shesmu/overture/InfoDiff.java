package ca.on.oicr.gsi.shesmu.overture;

import com.fasterxml.jackson.databind.JsonNode;

public class InfoDiff {

  private final String objectId;
  private final JsonNode olive;
  private final JsonNode server;

  public InfoDiff(String objectId, JsonNode server, JsonNode olive) {
    this.objectId = objectId;
    this.server = server;
    this.olive = olive;
  }
}
