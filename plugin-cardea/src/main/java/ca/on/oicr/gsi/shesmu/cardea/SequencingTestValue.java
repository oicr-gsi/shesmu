package ca.on.oicr.gsi.shesmu.cardea;

import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SequencingTestValue {
  private final String caseIdentifier;
  private final String test;
  private final String type;
  private final Set<Tuple> limsIds;
  private final boolean complete;

  public SequencingTestValue(
      String caseIdentifier,
      String test,
      String type,
      boolean complete,
      Stream<LimsSequencingInfo> limsIds) {
    super();
    this.caseIdentifier = caseIdentifier;
    this.test = test;
    this.type = type;
    this.complete = complete;
    this.limsIds =
        limsIds
            // Note: DO NOT change the order of these fields as Shesmu is not honouring insertion
            // order
            .map(info -> new Tuple(info.id(), info.qcFailed(), info.supplemental()))
            .collect(Collectors.toSet());
  }

  public SequencingTestValue(
      String caseIdentifier, String test, String type, boolean complete, Set<LimsIdDto> limsIds) {
    this(
        caseIdentifier,
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
  public String case_identifier() {
    return caseIdentifier;
  }

  @ShesmuVariable
  public String test() {
    return test;
  }

  @ShesmuVariable
  public String type() {
    return type;
  }

  // Note: DO NOT change the order of the fields as Shesmu is not honouring insertion order
  @ShesmuVariable(type = "ao3id$sqc_failed$bsupplemental$b")
  public Set<Tuple> lims_ids() {
    return limsIds;
  }

  @ShesmuVariable
  public boolean complete() {
    return complete;
  }
}
