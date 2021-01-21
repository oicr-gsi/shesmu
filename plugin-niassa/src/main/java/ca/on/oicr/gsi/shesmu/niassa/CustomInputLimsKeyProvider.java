package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.wdl.PackWdlVariables;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * This takes a set of {@link CustomLimsEntryType} structures associated with the output names of a
 * WDL workflow and can then demand that an olive provide this data, take the data from the olive,
 * pack it into a JSON blob that the WDL workflow wrapper can interpret.
 *
 * <p>Two key points on this: this process only depends on the output types provided by the workflow
 * and the output types of the workflow <b>do not match</b> the types that the olive needs to
 * provide. The workflow is saying what files it outputs and the olive must specify what LIMS keys
 * go with those file.
 */
public class CustomInputLimsKeyProvider implements InputLimsKeyProvider {
  private final Imyhat type;
  private final CustomLimsTransformer converter;

  public CustomInputLimsKeyProvider(Stream<Pair<String[], CustomLimsEntryType>> input) {
    // Because we have a nested structure, we need to convert the implied nesting to real nesting of
    // Shesmu objects
    final Pair<CustomLimsTransformer, Imyhat> pair =
        PackWdlVariables.create(
            (propertyName, type) -> type.bind(propertyName),
            handlers ->
                (type, value, output) -> type.accept(new PackWdlOutputs(output, handlers), value),
            CustomLimsEntryType::type,
            input,
            0);
    converter = pair.first();
    type = pair.second();
  }

  @Override
  public CustomActionParameter<WorkflowAction> parameter() {
    return new CustomActionParameter<WorkflowAction>("wdl_outputs", true, type) {
      @Override
      public void store(WorkflowAction action, Object value) {
        final List<Pair<String, CustomLimsEntry>> entries = new ArrayList<>();
        converter.write(type, value, (name, handler) -> entries.add(new Pair<>(name, handler)));
        action.limsKeyCollection(new CustomLimsKeys(entries));
      }
    };
  }
}
