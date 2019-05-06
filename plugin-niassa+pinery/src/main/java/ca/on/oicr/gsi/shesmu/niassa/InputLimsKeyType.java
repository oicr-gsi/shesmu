package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeGuarantee;
import java.util.function.Function;

/**
 * Definitions of all the types of ways to create LIMS keys associated with a run.
 *
 * <p>These do not map exactly to Niassa's concept of a workflow. All this code cares about is
 * whether the types of the parameters in the INI are the same. Any workflows that take the same
 * parameters can share on entry here.
 */
public enum InputLimsKeyType {
  CELL_RANGER(
      "lanes",
      CellRangerInputLimsCollection::new,
      TypeGuarantee.list(
          TypeGuarantee.tuple(
              Imyhat.tuple(Imyhat.STRING, Imyhat.INTEGER, Imyhat.STRING), // IUS
              Imyhat.STRING, // library name
              Imyhat.tuple(Imyhat.STRING, Imyhat.STRING, Imyhat.STRING), // LIMS key
              Imyhat.DATE, // last modified
              Imyhat.STRING) // group id
          )),
  FILES(
      "inputs",
      FilesInputLimsCollection::new,
      TypeGuarantee.list(
          TypeGuarantee.tuple(
              Imyhat.STRING, // SWID
              Imyhat.tuple(Imyhat.STRING, Imyhat.STRING, Imyhat.STRING, Imyhat.DATE), // LIMS key
              Imyhat.BOOLEAN))); // staleness

  private final CustomActionParameter<WorkflowAction, ?> parameter;

  <T> InputLimsKeyType(
      String name, Function<T, InputLimsCollection> create, TypeGuarantee<T> type) {
    this.parameter =
        new CustomActionParameter<WorkflowAction, T>(name, true, type) {

          @Override
          public void store(WorkflowAction action, T value) {
            action.limsKeyCollection(create.apply(value));
          }
        };
  }

  public final CustomActionParameter<WorkflowAction, ?> parameter() {
    return parameter;
  }
}
