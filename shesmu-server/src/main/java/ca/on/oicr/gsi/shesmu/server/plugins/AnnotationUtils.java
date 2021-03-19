package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.shesmu.plugin.Parser;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class AnnotationUtils {

  public static String checkName(String annotationName, Method method) {
    final var name = annotationName.isEmpty() ? method.getName() : annotationName;
    final var m = Parser.IDENTIFIER.matcher(name);
    if (!m.matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Method %s of %s is exported as %s which not a valid Shesmu name.",
              method.getName(), method.getDeclaringClass().getName(), name));
    }
    return name;
  }

  public static String checkName(String annotationName, Field field) {
    final var name = annotationName.isEmpty() ? field.getName() : annotationName;
    final var m = Parser.IDENTIFIER.matcher(name);
    if (!m.matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Field %s of %s is exported as %s which not a valid Shesmu name.",
              field.getName(), field.getDeclaringClass().getName(), name));
    }
    return name;
  }

  private AnnotationUtils() {}
}
