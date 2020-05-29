package ca.on.oicr.gsi.shesmu.prometheus;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.stream.Stream;

/** Bean of the Alert Manager alert JSON object */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertDto {
  private ObjectNode annotations;
  private String endsAt;
  private String fingerprint;
  private String generatorURL;
  private ObjectNode labels;
  private List<String> receivers;
  private String startsAt;
  private ObjectNode status;

  public ObjectNode getAnnotations() {
    return annotations;
  }

  public String getEndsAt() {
    return endsAt;
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public String getGeneratorURL() {
    return generatorURL;
  }

  public ObjectNode getLabels() {
    return labels;
  }

  public List<String> getReceivers() {
    return receivers;
  }

  public String getStartsAt() {
    return startsAt;
  }

  public ObjectNode getStatus() {
    return status;
  }

  public Stream<String> matches(String environment, List<String> labelNames) {
    if (!labels.get("alertname").asText("").equals("AutoInhibit")) {
      return Stream.empty();
    }
    if (labels.hasNonNull("environment")
        && !labels.get("environment").asText("").equals(environment)) {
      return Stream.empty();
    }
    return labelNames.stream().filter(labels::hasNonNull).map(l -> labels.get(l).asText(""));
  }

  public void setAnnotations(ObjectNode annotations) {
    this.annotations = annotations;
  }

  public void setEndsAt(String endsAt) {
    this.endsAt = endsAt;
  }

  public void setFingerprint(String fingerprint) {
    this.fingerprint = fingerprint;
  }

  public void setGeneratorURL(String generatorURL) {
    this.generatorURL = generatorURL;
  }

  public void setLabels(ObjectNode labels) {
    this.labels = labels;
  }

  public void setReceivers(List<String> receivers) {
    this.receivers = receivers;
  }

  public void setStartsAt(String startsAt) {
    this.startsAt = startsAt;
  }

  public void setStatus(ObjectNode status) {
    this.status = status;
  }
}
