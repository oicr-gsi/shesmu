package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SequencingTestValueToBeDeprecated {
  private final String test;
  private final String type;
  private final Set<Tuple> limsIds;
  private final boolean complete;

  public SequencingTestValueToBeDeprecated(
      String test, String type, boolean complete, Stream<LimsSequencingInfo> limsIds) {
    super();
    this.test = test;
    this.type = type;
    this.complete = complete;
    this.limsIds =
        limsIds
            .map(info -> new Tuple(info.id(), info.supplemental(), info.qcFailed()))
            .collect(Collectors.toSet());
  }

  public SequencingTestValueToBeDeprecated(
      String test, String type, Set<LimsIdDto> limsIds, boolean complete) {
    this(
        test,
        type,
        complete,
        limsIds.stream()
            .map(
                info ->
                    new LimsSequencingInfo(
                        info.getId(), info.isSupplemental(), info.isQcFailed())));
  }

  @ShesmuVariable
  public String test() {
    return test;
  }

  @ShesmuVariable
  public String type() {
    return type;
  }

  @ShesmuVariable
  public Set<Tuple> limsIds() {
    return limsIds;
  }

  @ShesmuVariable
  public boolean complete() {
    return complete;
  }
}
