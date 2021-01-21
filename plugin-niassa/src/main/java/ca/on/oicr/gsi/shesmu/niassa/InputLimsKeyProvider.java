package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

/**
 * A mechanism to get LIMS keys and input file SWIDs associated with a workflow run.
 *
 * <p>There are two main ways to do this: use one of the prebuilt ones designed for standard Niassa
 * workflows that expect <i>very specific INI entries</i> ({@link InputLimsKeyType}). This is very
 * inflexible and requires a new one for each workflow type. Also, this data can't really be passed
 * on to wrapped WDL workflows. The alternative is to use a custom output description that the WDL
 * workflow wrapper will marry against the actual output of the workflow {@link
 * CustomInputLimsKeyProvider}.
 */
@JsonDeserialize(using = InputLimsKeyDeserializer.class)
public interface InputLimsKeyProvider {
  CustomActionParameter<WorkflowAction> parameter();
}
