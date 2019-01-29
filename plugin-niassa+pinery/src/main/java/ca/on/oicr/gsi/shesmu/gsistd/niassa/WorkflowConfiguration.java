package ca.on.oicr.gsi.shesmu.gsistd.niassa;

import ca.on.oicr.gsi.shesmu.plugin.Definer;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public class WorkflowConfiguration {
  private long accession;
  private LanesType lanes;
  private int maxInFlight;
  private String name;
  private IniParam<?>[] parameters;
  private long[] previousAccessions;
  private String[] services;

  public void define(NiassaServer server, Definer definer, Configuration value) {
    final String description = //
        String.format(
                "Runs SeqWare/Niassa workflow %d using %s with settings in %s.", //
                accession, //
                value.getJar(), //
                value.getSettings())
            + (previousAccessions.length == 0
                ? ""
                : LongStream.of(getPreviousAccessions()) //
                    .sorted() //
                    .mapToObj(Long::toString) //
                    .collect(
                        Collectors.joining(", ", " Considered equivalent to workflows: ", "")));
    definer.defineAction(
        name,
        description,
        WorkflowAction.class,
        () ->
            new WorkflowAction(
                server,
                getLanes(),
                accession,
                previousAccessions,
                value.getJar(),
                value.getSettings(),
                services), //
        Stream.concat( //
            makeLanesParam(), //
            Stream.of(getParameters()).map(IniParam::parameter)));
  }

  Stream<CustomActionParameter<WorkflowAction, ?>> makeLanesParam() {
    return getLanes() == null
        ? Stream.empty()
        : Stream.of(
            new CustomActionParameter<WorkflowAction, Set<? extends Object>>(
                "lanes", true, getLanes().innerType().asList()) {

              @Override
              public void store(WorkflowAction action, Set<? extends Object> lanes) {
                action.lanes(lanes);
              }
            });
  }

  public long getAccession() {
    return accession;
  }

  public int getMaxInFlight() {
    return maxInFlight;
  }

  public String getName() {
    return name;
  }

  public long[] getPreviousAccessions() {
    return previousAccessions;
  }

  public String[] getServices() {
    return services;
  }

  public void setAccession(long accession) {
    this.accession = accession;
  }

  public void setMaxInFlight(int maxInFlight) {
    this.maxInFlight = maxInFlight;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void setPreviousAccessions(long[] previousAccessions) {
    this.previousAccessions = previousAccessions;
  }

  public void setServices(String[] services) {
    this.services = services;
  }

  public IniParam<?>[] getParameters() {
    return parameters;
  }

  public void setParameters(IniParam<?>[] parameters) {
    this.parameters = parameters;
  }

  public LanesType getLanes() {
    return lanes;
  }

  public void setLanes(LanesType lanes) {
    this.lanes = lanes;
  }
}
