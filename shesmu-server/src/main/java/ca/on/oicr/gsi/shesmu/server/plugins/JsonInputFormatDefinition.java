package ca.on.oicr.gsi.shesmu.server.plugins;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target.Flavour;
import ca.on.oicr.gsi.shesmu.compiler.definitions.GangElement;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.files.FileWatcher;
import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.objectweb.asm.Type;

public final class JsonInputFormatDefinition extends BaseInputFormatDefinition {
  public record Definition(TreeMap<String, VariableDefinition> variables, TimeFormat timeFormat) {}

  public record GangDefinition(int order, String gang, boolean dropIfDefault) {}

  public record VariableDefinition(Imyhat type, boolean signable, List<GangDefinition> gangs) {}

  private static final Type A_TUPLE_TYPE = Type.getType(Tuple.class);
  private static final String SUFFIX = ".shesmuschema";
  private static final List<JsonInputFormatDefinition> FORMATS =
      FileWatcher.DATA_DIRECTORY
          .paths()
          .flatMap(
              dir -> {
                try {
                  return Files.list(dir);
                } catch (IOException e) {
                  e.printStackTrace();
                  return Stream.empty();
                }
              })
          .filter(path -> path.toFile().toString().endsWith(SUFFIX))
          .map(
              path -> {
                try {
                  return create(path);
                } catch (IllegalAccessException | IOException e) {
                  throw new RuntimeException(e);
                }
              })
          .toList();

  public static JsonInputFormatDefinition create(Path path)
      throws IOException, IllegalAccessException {
    final var fileName = path.getFileName().toString();
    final var format = fileName.substring(0, fileName.length() - SUFFIX.length());
    return create(format, RuntimeSupport.MAPPER.readValue(path.toFile(), Definition.class));
  }

  public static JsonInputFormatDefinition create(String formatName, Definition definition)
      throws IllegalAccessException {
    final List<InputVariableDefinition> variables = new ArrayList<>();
    final Map<String, List<Pair<Integer, GangElement>>> gangs = new HashMap<>();
    for (final var entry : definition.variables().entrySet()) {
      final var name = entry.getKey();
      final var type = entry.getValue().type();
      final var flavour = entry.getValue().signable() ? Flavour.STREAM_SIGNABLE : Flavour.STREAM;
      final var methodType = MethodType.methodType(type.javaType(), Object.class);

      final var handle =
          MethodHandles.insertArguments(MH_TUPLE_GET, 1, variables.size()).asType(methodType);
      variables.add(
          new InputVariableDefinition(
              name, methodType, formatName, flavour, type, definition.timeFormat(), handle));

      for (final var gang : entry.getValue().gangs()) {
        gangs
            .computeIfAbsent(gang.gang(), k -> new ArrayList<>())
            .add(new Pair<>(gang.order(), new GangElement(name, type, gang.dropIfDefault())));
      }
    }
    return new JsonInputFormatDefinition(formatName, variables, gangs);
  }

  public static Stream<JsonInputFormatDefinition> formats() {
    return FORMATS.stream();
  }

  private JsonInputFormatDefinition(
      String format,
      List<InputVariableDefinition> variables,
      Map<String, List<Pair<Integer, GangElement>>> gangs) {
    super(format, variables, gangs);
  }

  @Override
  public Type type() {
    return A_TUPLE_TYPE;
  }
}
