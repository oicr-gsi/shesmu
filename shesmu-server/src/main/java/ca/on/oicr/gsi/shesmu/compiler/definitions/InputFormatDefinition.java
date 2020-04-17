package ca.on.oicr.gsi.shesmu.compiler.definitions;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

/** Define a <tt>Input</tt> format for olives to consume */
public interface InputFormatDefinition {
  static Map<String, Imyhat> predefinedTypes(
      Stream<SignatureDefinition> signatures, InputFormatDefinition inputFormatDefinition) {
    return Stream.concat(
            Stream.concat(
                    inputFormatDefinition.baseStreamVariables(), signatures.<Target>map(x -> x))
                .map(t -> new Pair<>(t.name() + "_type", t.type())),
            inputFormatDefinition
                .gangs()
                .map(
                    sg ->
                        new Pair<>(
                            sg.name() + "group",
                            Imyhat.tuple(
                                sg.elements().map(GangElement::type).toArray(Imyhat[]::new)))))
        .collect(Collectors.toMap(Pair::first, Pair::second));
  }

  InputFormatDefinition DUMMY =
      new InputFormatDefinition() {
        @Override
        public Stream<InputVariable> baseStreamVariables() {
          return Stream.empty();
        }

        @Override
        public Stream<? extends GangDefinition> gangs() {
          return Stream.empty();
        }

        @Override
        public String name() {
          return "";
        }

        @Override
        public Type type() {
          return Type.getType(Void.class);
        }
      };

  /** Get all the variables available for this format */
  Stream<InputVariable> baseStreamVariables();

  /** All the variable gangs this format has */
  Stream<? extends GangDefinition> gangs();

  /** The name of this format, which must be a valid Shesmu identifier */
  String name();

  /** Load the class for this format onto the stack */
  Type type();
}
