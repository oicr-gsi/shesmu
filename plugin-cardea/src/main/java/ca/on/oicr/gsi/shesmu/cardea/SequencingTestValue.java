package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SequencingTestValue {
  private final String test;
  private final String type;
  private final Set<Tuple> limsIds;
  private final boolean isComplete;

  public SequencingTestValue(
      String test, String type, Stream<LimsSequencingInfo> limsIds, boolean isComplete) {
    super();
    this.test = test;
    this.type = type;
    this.limsIds =
        limsIds
            .map(info -> new Tuple(info.limsId(), info.supplemental()))
            .collect(Collectors.toSet());
    this.isComplete = isComplete;
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
  public boolean isComplete() {
    return isComplete;
  }
}
