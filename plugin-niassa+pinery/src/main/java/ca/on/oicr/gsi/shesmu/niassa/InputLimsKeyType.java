package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
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
              CellRangerIUSEntry::new,
              TypeGuarantee.tuple(
                  IusTriple::new,
                  TypeGuarantee.STRING,
                  TypeGuarantee.LONG,
                  TypeGuarantee.STRING), // IUS
              TypeGuarantee.STRING, // library name
              TypeGuarantee.tuple(
                  SimpleLimsKey::new,
                  TypeGuarantee.STRING,
                  TypeGuarantee.STRING,
                  TypeGuarantee.STRING,
                  TypeGuarantee.DATE), // LIMS key
              TypeGuarantee.STRING) // group id
          )),
  FILES(
      "inputs",
      FilesInputLimsCollection::new,
      TypeGuarantee.list(
          TypeGuarantee.tuple(
              FilesInputFile::new,
              TypeGuarantee.STRING, // SWID
              TypeGuarantee.tuple(
                  SimpleLimsKey::new,
                  TypeGuarantee.STRING,
                  TypeGuarantee.STRING,
                  TypeGuarantee.STRING,
                  TypeGuarantee.DATE), // LIMS key
              TypeGuarantee.BOOLEAN))); // staleness

  private final CustomActionParameter<WorkflowAction> parameter;

  <T> InputLimsKeyType(
      String name, Function<T, InputLimsCollection> create, TypeGuarantee<T> type) {
    this.parameter =
        new CustomActionParameter<WorkflowAction>(name, true, type.type()) {

          @Override
          public void store(WorkflowAction action, Object value) {
            action.limsKeyCollection(create.apply(type.unpack(value)));
          }
        };
  }

  public final CustomActionParameter<WorkflowAction> parameter() {
    return parameter;
  }
}
