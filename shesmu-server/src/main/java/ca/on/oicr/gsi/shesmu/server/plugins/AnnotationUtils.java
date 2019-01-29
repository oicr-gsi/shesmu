package ca.on.oicr.gsi.shesmu.server.plugins;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnnotationUtils {
  private static final Pattern VALID_SHESMU_NAME = Pattern.compile("[a-z][a-z_]*");
  private static final Pattern VALID_SHESMU_NAME_WITH_INSTANCE =
      Pattern.compile("([a-z][a-z_]*)?\\$[a-z_]*");

  public static String checkName(String annotationName, Method method, boolean isInstance) {
    final String name = annotationName.isEmpty() ? method.getName() : annotationName;
    final Matcher m =
        (isInstance ? VALID_SHESMU_NAME_WITH_INSTANCE : VALID_SHESMU_NAME).matcher(name);
    if (!m.matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Method %s of %s is not a valid Shesmu name.",
              method.getName(), method.getDeclaringClass().getName()));
    }
    return name;
  }

  public static String checkName(String annotationName, Field field, boolean isInstance) {
    final String name = annotationName.isEmpty() ? field.getName() : annotationName;
    final Matcher m =
        (isInstance ? VALID_SHESMU_NAME_WITH_INSTANCE : VALID_SHESMU_NAME).matcher(name);
    if (!m.matches()) {
      throw new IllegalArgumentException(
          String.format(
              "Field %s of %s is not a valid Shesmu name.",
              field.getName(), field.getDeclaringClass().getName()));
    }
    return name;
  }

  private AnnotationUtils() {}
}
