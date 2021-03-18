/*
 * Cromwell Server REST API
 * Describes the REST API provided by a Cromwell server
 *
 * OpenAPI spec version: 30
 *
 *
 * NOTE: This class is auto generated by the swagger code generator program.
 * https://github.com/swagger-api/swagger-codegen.git
 * Do not edit the class manually.
 */

package ca.on.oicr.gsi.shesmu.cromwell;

import java.util.HashMap;
import java.util.Map;

/** LabelsResponse */
public class LabelsResponse {
  private String id = null;
  private Map<String, String> labels = new HashMap<String, String>();

  public String getId() {
    return id;
  }

  public Map<String, String> getLabels() {
    return labels;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setLabels(Map<String, String> labels) {
    this.labels = labels;
  }
}