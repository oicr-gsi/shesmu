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

package io.swagger.client.model;

import com.google.gson.annotations.SerializedName;
import io.swagger.annotations.ApiModelProperty;
import java.util.Objects;

/** WorkflowSubmitResponse */
@javax.annotation.Generated(
    value = "io.swagger.codegen.languages.JavaClientCodegen",
    date = "2018-12-03T20:33:09.260Z")
public class WorkflowSubmitResponse {
  @SerializedName("id")
  private String id = null;

  @SerializedName("status")
  private String status = null;

  public WorkflowSubmitResponse id(String id) {
    this.id = id;
    return this;
  }

  /**
   * The identifier of the workflow
   *
   * @return id
   */
  @ApiModelProperty(
      example = "e442e52a-9de1-47f0-8b4f-e6e565008cf1",
      required = true,
      value = "The identifier of the workflow")
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public WorkflowSubmitResponse status(String status) {
    this.status = status;
    return this;
  }

  /**
   * The status of the workflow
   *
   * @return status
   */
  @ApiModelProperty(example = "Submitted", required = true, value = "The status of the workflow")
  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  @Override
  public boolean equals(java.lang.Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    WorkflowSubmitResponse workflowSubmitResponse = (WorkflowSubmitResponse) o;
    return Objects.equals(this.id, workflowSubmitResponse.id)
        && Objects.equals(this.status, workflowSubmitResponse.status);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, status);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class WorkflowSubmitResponse {\n");

    sb.append("    id: ").append(toIndentedString(id)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces (except the first line).
   */
  private String toIndentedString(java.lang.Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}
