package ca.on.oicr.gsi.shesmu.plugin.wdl;

import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonArray;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.stream.Stream;

/** Serialse data to WDL-compatible JSON arrays where tuples are converted to pairs */
public class PackWdlJsonArray extends PackJsonArray {
  @Override
  protected ImyhatConsumer createObject(ObjectNode object, String property) {
    return new PackWdlJsonObject(object, property);
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    PackWdlJsonObject.packWdlTuple(node.addObject(), fields);
  }

  @Override
  protected ImyhatConsumer createArray(ArrayNode array) {
    return new PackWdlJsonArray(array);
  }

  public PackWdlJsonArray(ArrayNode node) {
    super(node);
  }
}
