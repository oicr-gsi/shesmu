package ca.on.oicr.gsi.shesmu.compiler;

import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * This is the definition from something that can be used in a “call” olive clause; one which calls
 * a predefined olive chunk elsewhere.
 */
public interface CallableDefinition {

  void collectSignables(
      Set<String> signableNames, Consumer<SignableVariableCheck> addSignableCheck);

  Stream<OliveClauseRow> dashboardInner(int line, int column);

  Path filename();

  boolean isRoot();

  String name();

  Optional<Stream<Target>> outputStreamVariables(
      OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler);

  int parameterCount();

  Imyhat parameterType(int index);
}
