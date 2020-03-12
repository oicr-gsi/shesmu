package ca.on.oicr.gsi.shesmu.core;

import ca.on.oicr.gsi.shesmu.compiler.RefillerDefinition;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ActionParameterDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.DefinitionRepository;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.SignatureVariableForDynamicSigner;
import ca.on.oicr.gsi.shesmu.core.signers.JsonSigner;
import ca.on.oicr.gsi.shesmu.core.signers.SHA1DigestSigner;
import ca.on.oicr.gsi.shesmu.core.signers.SignatureCount;
import ca.on.oicr.gsi.shesmu.core.signers.SignatureNames;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeInterop;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.status.ConfigurationSection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

/** The standard library of Shesmu */
public final class StandardDefinitions implements DefinitionRepository {

  @RuntimeInterop
  public static JsonNode jsonObject(Set<Tuple> fields) {
    final ObjectNode result = RuntimeSupport.MAPPER.createObjectNode();
    for (final Tuple field : fields) {
      result.set((String) field.get(0), (JsonNode) field.get(1));
    }
    return result;
  }

  @RuntimeInterop
  public static Path replaceHome(Path original, Path homedir) {
    if (original.getNameCount() == 0) {
      return original;
    }
    final String start = original.getName(0).toString();
    if (start.equals("$HOME") || start.equals("~")) {
      return homedir.resolve(original.subpath(1, original.getNameCount()));
    }
    return original;
  }

  /** Truncate a time stamp to midnight */
  @RuntimeInterop
  public static Instant start_of_day(Instant input) {
    return input.truncatedTo(ChronoUnit.DAYS);
  }

  @RuntimeInterop
  public static boolean version_at_least(Tuple version, long major, long minor, long patch) {
    if ((Long) version.get(0) < major) {
      return false;
    }
    if ((Long) version.get(1) < minor) {
      return false;
    }
    return (Long) version.get(2) >= patch;
  }

  private static final Type A_CHAR_SEQUENCE_TYPE = Type.getType(CharSequence.class);
  private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
  private static final Type A_JSON_SIGNATURE_TYPE = Type.getType(JsonSigner.class);
  private static final Type A_NOTHING_ACTION_TYPE = Type.getType(NothingAction.class);
  private static final Type A_PATH_TYPE = Type.getType(Path.class);
  private static final Type A_PATHS_TYPE = Type.getType(Paths.class);
  private static final Type A_SHA1_SIGNATURE_TYPE = Type.getType(SHA1DigestSigner.class);
  protected static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
  protected static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
  private static final FunctionDefinition[] FUNCTIONS =
      new FunctionDefinition[] {
        FunctionDefinition.virtualMethod(
            "date_to_seconds",
            "getEpochSecond",
            "Get the number of seconds since the UNIX epoch for this date.",
            Imyhat.INTEGER,
            new FunctionParameter("date", Imyhat.DATE)),
        FunctionDefinition.virtualMethod(
            "date_to_millis",
            "toEpochMilli",
            "Get the number of milliseconds since the UNIX epoch for this date.",
            Imyhat.INTEGER,
            new FunctionParameter("date", Imyhat.DATE)),
        FunctionDefinition.staticMethod(
            "is_infinite",
            Double.class,
            "isInfinite",
            "Check is the number is infininte.",
            Imyhat.BOOLEAN,
            new FunctionParameter("input number", Imyhat.FLOAT)),
        FunctionDefinition.staticMethod(
            "is_nan",
            Double.class,
            "isNaN",
            "Check is the number is not-a-number.",
            Imyhat.BOOLEAN,
            new FunctionParameter("input number", Imyhat.FLOAT)),
        FunctionDefinition.staticMethod(
            "start_of_day",
            StandardDefinitions.class,
            "start_of_day",
            "Rounds a date-time to the previous midnight.",
            Imyhat.DATE,
            new FunctionParameter("date", Imyhat.DATE)),
        FunctionDefinition.staticMethod(
            "json_object",
            StandardDefinitions.class,
            "jsonObject",
            "Create a JSON object from fields.",
            Imyhat.JSON,
            new FunctionParameter("fields", Imyhat.tuple(Imyhat.STRING, Imyhat.JSON).asList())),
        FunctionDefinition.virtualMethod(
            "path_file",
            "getFileName",
            "Extracts the last element in a path.",
            Imyhat.PATH,
            new FunctionParameter("input path", Imyhat.PATH)),
        FunctionDefinition.virtualMethod(
            "path_dir",
            "getParent",
            "Extracts all but the last elements in a path (i.e., the containing directory).",
            Imyhat.PATH,
            new FunctionParameter("input path", Imyhat.PATH)),
        FunctionDefinition.virtualMethod(
            "path_normalize",
            "normalize",
            "Normalize a path (i.e., remove any ./ and ../ in the path).",
            Imyhat.PATH,
            new FunctionParameter("input path", Imyhat.PATH)),
        FunctionDefinition.virtualMethod(
            "path_relativize",
            "relativize",
            "Creates a new path of relativize one path as if in the directory of the other.",
            Imyhat.PATH,
            new FunctionParameter("directory path", Imyhat.PATH),
            new FunctionParameter("path to relativize", Imyhat.PATH)),
        FunctionDefinition.staticMethod(
            "path_replace_home",
            StandardDefinitions.class,
            "replaceHome",
            "Replace any path that starts with $HOME or ~ with the provided home directory",
            Imyhat.PATH,
            new FunctionParameter("path to change", Imyhat.PATH),
            new FunctionParameter("home directory", Imyhat.PATH)),
        FunctionDefinition.staticMethod(
            "version_at_least",
            StandardDefinitions.class,
            "version_at_least",
            "Checks whether the supplied version tuple is the same or greater than version numbers provided.",
            Imyhat.BOOLEAN,
            new FunctionParameter(
                "version", Imyhat.tuple(Imyhat.INTEGER, Imyhat.INTEGER, Imyhat.INTEGER)),
            new FunctionParameter("major", Imyhat.INTEGER),
            new FunctionParameter("minor", Imyhat.INTEGER),
            new FunctionParameter("patch", Imyhat.INTEGER)),
        FunctionDefinition.virtualMethod(
            "str_trim",
            "trim",
            "Remove white space from a string.",
            Imyhat.STRING,
            new FunctionParameter("str", Imyhat.STRING)),
        FunctionDefinition.virtualMethod(
            "str_lower",
            "toLowerCase",
            "Convert a string to lower case.",
            Imyhat.STRING,
            new FunctionParameter("str", Imyhat.STRING)),
        FunctionDefinition.virtualMethod(
            "str_upper",
            "toUpperCase",
            "Convert a string to upper case.",
            Imyhat.STRING,
            new FunctionParameter("str", Imyhat.STRING)),
        FunctionDefinition.virtualMethod(
            "str_eq",
            "equalsIgnoreCase",
            "Compares two strings ignoring case.",
            Imyhat.BOOLEAN,
            new FunctionParameter("first", Imyhat.STRING),
            new FunctionParameter("second", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            "parse_int",
            RuntimeSupport.class,
            "parseLong",
            "Convert a string containing digits into an integer.",
            Imyhat.INTEGER.asOptional(),
            new FunctionParameter("String to parse", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            "parse_float",
            RuntimeSupport.class,
            "parseDouble",
            "Convert a string containing digits and a decimal point into an float.",
            Imyhat.FLOAT.asOptional(),
            new FunctionParameter("String to parse", Imyhat.STRING)),
        new FunctionDefinition() {

          @Override
          public String description() {
            return "Gets the length of a string.";
          }

          @Override
          public Path filename() {
            return null;
          }

          @Override
          public String name() {
            return "str_len";
          }

          @Override
          public Stream<FunctionParameter> parameters() {
            return Stream.of(new FunctionParameter("str", Imyhat.STRING));
          }

          @Override
          public void render(GeneratorAdapter methodGen) {
            methodGen.invokeVirtual(
                A_STRING_TYPE, new Method("length", Type.INT_TYPE, new Type[] {}));
            methodGen.cast(Type.INT_TYPE, Type.LONG_TYPE);
          }

          @Override
          public void renderStart(GeneratorAdapter methodGen) {
            // None required.
          }

          @Override
          public Imyhat returnType() {
            return Imyhat.INTEGER;
          }
        },
        new FunctionDefinition() {

          @Override
          public String description() {
            return "Replaces parts of a string.";
          }

          @Override
          public Path filename() {
            return null;
          }

          @Override
          public String name() {
            return "str_replace";
          }

          @Override
          public Stream<FunctionParameter> parameters() {
            return Stream.of(
                new FunctionParameter("haystack", Imyhat.STRING),
                new FunctionParameter("needle", Imyhat.STRING),
                new FunctionParameter("replacement", Imyhat.STRING));
          }

          @Override
          public void render(GeneratorAdapter methodGen) {
            methodGen.invokeVirtual(
                A_STRING_TYPE,
                new Method(
                    "replace",
                    A_STRING_TYPE,
                    new Type[] {A_CHAR_SEQUENCE_TYPE, A_CHAR_SEQUENCE_TYPE}));
          }

          @Override
          public void renderStart(GeneratorAdapter methodGen) {
            // None required.
          }

          @Override
          public Imyhat returnType() {
            return Imyhat.STRING;
          }
        },
        new FunctionDefinition() {

          @Override
          public String description() {
            return "Converts a string to a path.";
          }

          @Override
          public Path filename() {
            return null;
          }

          @Override
          public String name() {
            return "str_to_path";
          }

          @Override
          public Stream<FunctionParameter> parameters() {
            return Stream.of(new FunctionParameter("str", Imyhat.STRING));
          }

          @Override
          public void render(GeneratorAdapter methodGen) {
            methodGen.push(0);
            methodGen.newArray(A_STRING_TYPE);
            methodGen.invokeStatic(
                A_PATHS_TYPE,
                new Method("get", A_PATH_TYPE, new Type[] {A_STRING_TYPE, A_STRING_ARRAY_TYPE}));
          }

          @Override
          public void renderStart(GeneratorAdapter methodGen) {
            // None required.
          }

          @Override
          public Imyhat returnType() {
            return Imyhat.PATH;
          }
        },
        new FunctionDefinition() {

          @Override
          public String description() {
            return "Truncates a floating point number of an integer.";
          }

          @Override
          public Path filename() {
            return null;
          }

          @Override
          public String name() {
            return "to_int";
          }

          @Override
          public Stream<FunctionParameter> parameters() {
            return Stream.of(new FunctionParameter("floating point number", Imyhat.FLOAT));
          }

          @Override
          public void render(GeneratorAdapter methodGen) {
            methodGen.cast(Type.DOUBLE_TYPE, Type.LONG_TYPE);
          }

          @Override
          public void renderStart(GeneratorAdapter methodGen) {
            // None required.
          }

          @Override
          public Imyhat returnType() {
            return Imyhat.INTEGER;
          }
        },
        new FunctionDefinition() {

          @Override
          public String description() {
            return "Converts an integer to a floating point number.";
          }

          @Override
          public Path filename() {
            return null;
          }

          @Override
          public String name() {
            return "to_float";
          }

          @Override
          public Stream<FunctionParameter> parameters() {
            return Stream.of(new FunctionParameter("integer", Imyhat.INTEGER));
          }

          @Override
          public void render(GeneratorAdapter methodGen) {
            methodGen.cast(Type.LONG_TYPE, Type.DOUBLE_TYPE);
          }

          @Override
          public void renderStart(GeneratorAdapter methodGen) {
            // None required.
          }

          @Override
          public Imyhat returnType() {
            return Imyhat.FLOAT;
          }
        }
      };
  private static final Method METHOD_INSTANT__NOW =
      new Method("now", A_INSTANT_TYPE, new Type[] {});
  private static final ConstantDefinition[] CONSTANTS =
      new ConstantDefinition[] {
        ConstantDefinition.of(
            "epoch", Instant.EPOCH, "The date at UNIX timestamp 0: 1970-01-01T00:00:00Z"),
        new ConstantDefinition(
            "now",
            Imyhat.DATE,
            "The current timestamp. This is fetched every time this constant is referenced, so now != now.",
            null) {

          @Override
          protected void load(GeneratorAdapter methodGen) {
            methodGen.invokeStatic(A_INSTANT_TYPE, METHOD_INSTANT__NOW);
          }
        }
      };
  private static final ActionDefinition NOTHING_ACTION =
      new ActionDefinition(
          "nothing",
          "Does absolutely nothing and ignores the value provided. Useful for debugging.",
          null,
          Stream.of(
              new ActionParameterDefinition() {

                @Override
                public String name() {

                  return "value";
                }

                @Override
                public boolean required() {
                  return true;
                }

                @Override
                public void store(
                    Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
                  renderer.methodGen().loadLocal(actionLocal);
                  renderer.methodGen().checkCast(A_NOTHING_ACTION_TYPE);
                  loadParameter.accept(renderer);
                  renderer.methodGen().putField(A_NOTHING_ACTION_TYPE, "value", A_STRING_TYPE);
                }

                @Override
                public Imyhat type() {
                  return Imyhat.STRING;
                }
              })) {

        @Override
        public void initialize(GeneratorAdapter methodGen) {
          methodGen.newInstance(A_NOTHING_ACTION_TYPE);
          methodGen.dup();
          methodGen.invokeConstructor(A_NOTHING_ACTION_TYPE, DEFAULT_CTOR);
        }
      };
  private static final SignatureDefinition[] SIGNATURES =
      new SignatureDefinition[] {
        new SignatureCount(),
        new SignatureNames(),
        new SignatureVariableForDynamicSigner("json_signature", Imyhat.JSON) {

          @Override
          public Path filename() {
            return null;
          }

          @Override
          protected void newInstance(GeneratorAdapter method) {
            method.newInstance(A_JSON_SIGNATURE_TYPE);
            method.dup();
            method.invokeConstructor(A_JSON_SIGNATURE_TYPE, DEFAULT_CTOR);
          }
        },
        new SignatureVariableForDynamicSigner("sha1_signature", Imyhat.STRING) {

          @Override
          public Path filename() {
            return null;
          }

          @Override
          protected void newInstance(GeneratorAdapter method) {
            method.newInstance(A_SHA1_SIGNATURE_TYPE);
            method.dup();
            method.invokeConstructor(A_SHA1_SIGNATURE_TYPE, DEFAULT_CTOR);
          }
        }
      };

  @Override
  public Stream<ActionDefinition> actions() {
    return Stream.of(NOTHING_ACTION);
  }

  @Override
  public Stream<ConstantDefinition> constants() {
    return Stream.of(CONSTANTS);
  }

  @Override
  public Stream<FunctionDefinition> functions() {
    return Stream.of(FUNCTIONS);
  }

  @Override
  public Stream<ConfigurationSection> listConfiguration() {
    return Stream.empty();
  }

  @Override
  public Stream<RefillerDefinition> refillers() {
    return Stream.empty();
  }

  @Override
  public Stream<SignatureDefinition> signatures() {
    return Stream.of(SIGNATURES);
  }

  @Override
  public void writeJavaScriptRenderer(PrintStream writer) {
    writer.print(
        "actionRender.set('nothing', a => [title(a, 'Nothing'), text(`Value: ${a.value}`)]);");
  }
}
