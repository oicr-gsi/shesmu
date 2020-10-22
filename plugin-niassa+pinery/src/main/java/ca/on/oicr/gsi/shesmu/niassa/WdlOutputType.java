package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.TypeParser;
import java.util.concurrent.atomic.AtomicReference;
import org.kohsuke.MetaInfServices;

@MetaInfServices
public final class WdlOutputType implements TypeParser {

  @Override
  public String description() {
    return "Niassa-type for WDL-ouputs";
  }

  @Override
  public String format() {
    return "niassa::wdl_output";
  }

  @Override
  public Imyhat parse(String outputType) {
    final AtomicReference<CustomLimsEntryType> output = new AtomicReference<>();
    final Parser parser =
        Parser.start(outputType, (line, column, message) -> {})
            .whitespace()
            .dispatch(InputLimsKeyDeserializer.DISPATCH, output::set)
            .whitespace();
    return parser.finished() ? output.get().type() : Imyhat.BAD;
  }
}
