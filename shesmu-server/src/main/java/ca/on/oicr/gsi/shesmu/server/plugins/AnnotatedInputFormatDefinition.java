package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangElement;
import ca.on.oicr.gsi.shesmu.plugin.input.InputFormat;
import ca.on.oicr.gsi.shesmu.plugin.input.ShesmuVariable;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

/** Define a <code>Input</code> format for olives to consume */
public final class AnnotatedInputFormatDefinition extends BaseInputFormatDefinition {

  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final List<AnnotatedInputFormatDefinition> FORMATS = new ArrayList<>();
  public static final JarHashRepository<InputFormat> INPUT_FORMAT_HASHES =
      new JarHashRepository<>();

  static {
    for (final var format : ServiceLoader.load(InputFormat.class)) {
      try {
        INPUT_FORMAT_HASHES.add(format);
        FORMATS.add(create(format));
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }

  public static AnnotatedInputFormatDefinition create(InputFormat format)
      throws IllegalAccessException {
    // Get all the candidate methods in this class and sort them alphabetically
    final SortedMap<String, Pair<ShesmuVariable, Method>> sortedMethods = new TreeMap<>();
    for (final var method : format.type().getMethods()) {
      final var info = method.getAnnotation(ShesmuVariable.class);
      if (info == null) {
        continue;
      }
      sortedMethods.put(method.getName(), new Pair<>(info, method));
    }
    final List<InputVariableDefinition> variables = new ArrayList<>();
    final Map<String, List<Pair<Integer, GangElement>>> gangs = new HashMap<>();
    for (final var entry : sortedMethods.values()) {
      // Create a target for each method for the compiler to use
      final var name = entry.second().getName();
      final var type =
          Imyhat.convert(
              String.format(
                  "Return type of %s in %s", name, entry.second().getDeclaringClass().getName()),
              entry.first().type(),
              entry.second().getGenericReturnType());
      final var flavour = entry.first().signable() ? Flavour.STREAM_SIGNABLE : Flavour.STREAM;
      final var methodType = MethodType.methodType(type.javaType(), Object.class);

      // Now we need to make a call site for this variable. It will happen in two
      // cases: either we have an instance of the real type and we should call the
      // method on it, or we have a Tuple that was generated generically by one of our
      // JSON readers
      final var getter = format.lookup().unreflect(entry.second()).asType(methodType);
      final var handle =
          MethodHandles.guardWithTest(
              MH_TUPLE_IS_INSTANCE,
              MethodHandles.insertArguments(MH_TUPLE_GET, 1, variables.size()).asType(methodType),
              getter);
      variables.add(
          new InputVariableDefinition(
              name, methodType, format.name(), flavour, type, entry.first().timeFormat(), handle));

      // Now, prepare any gangs
      for (final var gang : entry.first().gangs()) {
        gangs
            .computeIfAbsent(gang.name(), k -> new ArrayList<>())
            .add(new Pair<>(gang.order(), new GangElement(name, type, gang.dropIfDefault())));
      }
    }
    return new AnnotatedInputFormatDefinition(format, variables, gangs);
  }

  public static Stream<AnnotatedInputFormatDefinition> formats() {
    return FORMATS.stream();
  }

  private final InputFormat format;

  private AnnotatedInputFormatDefinition(
      InputFormat format,
      List<InputVariableDefinition> variables,
      Map<String, List<Pair<Integer, GangElement>>> gangs) {
    super(format.name(), variables, gangs);
    this.format = format;
  }

  public boolean isAssignableFrom(Class<?> dataType) {
    return format.type().isAssignableFrom(dataType);
  }

  @Override
  public Type type() {
    return A_OBJECT_TYPE;
  }
}
