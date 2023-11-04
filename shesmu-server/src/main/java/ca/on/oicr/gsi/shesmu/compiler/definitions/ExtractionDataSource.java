package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.shesmu.compiler.ExtractionNode.OutputCollector;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import java.util.Set;

public interface ExtractionDataSource {

  Set<String> captures();

  String name();

  void renderColumns(OutputCollector collector);

  void renderStream(Renderer renderer);
}
