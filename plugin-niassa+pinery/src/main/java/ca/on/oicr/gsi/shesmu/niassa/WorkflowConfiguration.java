package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class WorkflowConfiguration {
  private long accession;
  private Map<String, String> annotations = Collections.emptyMap();
  private int maxInFlight;
  private String name;
  private IniParam<?>[] parameters;
  private long[] previousAccessions;
  private String[] services;
  private InputLimsKeyType type;

  public void define(Definer<NiassaServer> definer) {
    final String description =
        String.format(
                "Runs SeqWare/Niassa workflow %d with settings in %s.",
                accession, definer.get().fileName())
            + (previousAccessions.length == 0
                ? ""
                : LongStream.of(getPreviousAccessions())
                    .sorted()
                    .mapToObj(Long::toString)
                    .collect(
                        Collectors.joining(", ", " Considered equivalent to workflows: ", "")));
    definer.defineAction(
        name,
        description,
        WorkflowAction.class,
        () -> new WorkflowAction(definer, accession, previousAccessions, services, annotations),
        Stream.concat(
            Stream.of(getType().parameter()), Stream.of(getParameters()).map(IniParam::parameter)));
  }

  public long getAccession() {
    return accession;
  }

  public Map<String, String> getAnnotations() {
    return annotations;
  }

  public int getMaxInFlight() {
    return maxInFlight;
  }

  public String getName() {
    return name;
  }

  public IniParam<?>[] getParameters() {
    return parameters;
  }

  public long[] getPreviousAccessions() {
    return previousAccessions;
  }

  public String[] getServices() {
    return services;
  }

  public InputLimsKeyType getType() {
    return type;
  }

  public void setAccession(long accession) {
    this.accession = accession;
  }

  public void setAnnotations(Map<String, String> annotations) {
    this.annotations = annotations;
  }

  public void setMaxInFlight(int maxInFlight) {
    this.maxInFlight = maxInFlight;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setParameters(IniParam<?>[] parameters) {
    this.parameters = parameters;
  }

  public void setPreviousAccessions(long[] previousAccessions) {
    this.previousAccessions = previousAccessions;
  }

  public void setServices(String[] services) {
    this.services = services;
  }

  public void setType(InputLimsKeyType type) {
    this.type = type;
  }
}
