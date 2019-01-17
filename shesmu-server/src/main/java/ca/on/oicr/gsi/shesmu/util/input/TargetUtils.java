package ca.on.oicr.gsi.shesmu.util.input;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.compiler.NameDefinitions.DefaultStreamTarget;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import java.util.Arrays;
import java.util.stream.Stream;

public class TargetUtils {
  public static Target[] readAnnotationsFor(Class<?> itemClass) {
    return Arrays.stream(itemClass.getMethods())
        .flatMap(
            method -> {
              final ShesmuVariable[] exports = method.getAnnotationsByType(ShesmuVariable.class);
              if (exports.length == 1) {
                Imyhat type =
                    Imyhat.convert(
                        String.format("Method %s of %s", method.getName(), itemClass.getName()),
                        exports[0].type(),
                        method.getReturnType());
                return Stream.of(
                    new DefaultStreamTarget(method.getName(), type, exports[0].signable()));
              }
              return Stream.empty();
            })
        .toArray(Target[]::new);
  }

  private TargetUtils() {}
}
