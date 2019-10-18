package ca.on.oicr.gsi.shesmu.compiler.definitions;

import java.util.stream.Stream;

public interface GangDefinition {

  Stream<GangElement> elements();

  String name();
}
