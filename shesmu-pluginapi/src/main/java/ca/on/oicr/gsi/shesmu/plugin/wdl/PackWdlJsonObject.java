package ca.on.oicr.gsi.shesmu.plugin.wdl;

import ca.on.oicr.gsi.shesmu.plugin.json.PackJsonObject;
import ca.on.oicr.gsi.shesmu.plugin.types.Field;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.stream.Stream;

/** Serialse data to WDL-compatible JSON objects where tuples are converted to pairs */
public class PackWdlJsonObject extends PackJsonObject {
  public static void packWdlTuple(ObjectNode object, Stream<Field<Integer>> fields) {
    fields.forEach(
        f -> {
          switch (f.index()) {
            case 0:
              f.type().accept(new PackWdlJsonObject(object, "Left"), f.value());
              break;
            case 1:
              f.type().accept(new PackWdlJsonObject(object, "Right"), f.value());
              break;
            default:
              throw new UnsupportedOperationException("WDL only supports pairs; not tuples.");
          }
        });
  }

  public PackWdlJsonObject(ObjectNode node, String name) {
    super(node, name);
  }

  @Override
  public void acceptTuple(Stream<Field<Integer>> fields) {
    packWdlTuple(node.putObject(name), fields);
  }

  @Override
  protected ImyhatConsumer createArray(ArrayNode array) {
    return new PackWdlJsonArray(array);
  }

  @Override
  protected ImyhatConsumer createObject(ObjectNode object, String property) {
    return new PackWdlJsonObject(object, property);
  }
}
