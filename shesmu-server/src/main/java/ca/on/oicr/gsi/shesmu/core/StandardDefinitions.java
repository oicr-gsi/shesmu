package ca.on.oicr.gsi.shesmu.core;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

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
import ca.on.oicr.gsi.shesmu.plugin.Parser;
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
  private static class ClampFunction implements FunctionDefinition {

    private static final Type A_MATH_TYPE = Type.getType(Math.class);
    private final String name;
    private final Imyhat type;

    private ClampFunction(String namespace, Imyhat type) {
      this.type = type;
      name = String.join(Parser.NAMESPACE_SEPARATOR, "std", namespace, "clamp");
    }

    @Override
    public String description() {
      return "Passes through a value with a minimum and maximum limit.";
    }

    @Override
    public Path filename() {
      return null;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Stream<FunctionParameter> parameters() {
      return Stream.of(
          new FunctionParameter("lower limit", type),
          new FunctionParameter("value to use", type),
          new FunctionParameter("upper limit", type));
    }

    @Override
    public void render(GeneratorAdapter methodGen) {
      final Type t = type.apply(TO_ASM);
      methodGen.invokeStatic(A_MATH_TYPE, new Method("min", t, new Type[] {t, t}));
      methodGen.invokeStatic(A_MATH_TYPE, new Method("max", t, new Type[] {t, t}));
    }

    @Override
    public void renderStart(GeneratorAdapter methodGen) {
      // Do nothing
    }

    @Override
    public Imyhat returnType() {
      return type;
    }
  }

  private static final Type A_CHAR_SEQUENCE_TYPE = Type.getType(CharSequence.class);
  private static final Type A_INSTANT_TYPE = Type.getType(Instant.class);
  private static final Type A_JSON_SIGNATURE_TYPE = Type.getType(JsonSigner.class);
  private static final Type A_NOTHING_ACTION_TYPE = Type.getType(NothingAction.class);
  private static final Type A_PATHS_TYPE = Type.getType(Paths.class);
  private static final Type A_PATH_TYPE = Type.getType(Path.class);
  private static final Type A_SHA1_SIGNATURE_TYPE = Type.getType(SHA1DigestSigner.class);
  protected static final Type A_STRING_ARRAY_TYPE = Type.getType(String[].class);
  protected static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Method DEFAULT_CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] {});
  private static final FunctionDefinition[] FUNCTIONS =
      new FunctionDefinition[] {
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "date", "to_seconds"),
            "getEpochSecond",
            "Get the number of seconds since the UNIX epoch for this date.",
            Imyhat.INTEGER,
            new FunctionParameter("date", Imyhat.DATE)),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "date", "to_millis"),
            "toEpochMilli",
            "Get the number of milliseconds since the UNIX epoch for this date.",
            Imyhat.INTEGER,
            new FunctionParameter("date", Imyhat.DATE)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "date", "from_seconds"),
            Instant.class,
            "ofEpochSecond",
            "Get create a date from the number of seconds since the UNIX epoch.",
            Imyhat.DATE,
            new FunctionParameter("date", Imyhat.INTEGER)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "date", "from_millis"),
            Instant.class,
            "ofEpochMilli",
            "Get create a date from the number of milliseconds since the UNIX epoch.",
            Imyhat.DATE,
            new FunctionParameter("date", Imyhat.INTEGER)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "float", "is_infinite"),
            Double.class,
            "isInfinite",
            "Check if the number is infinite.",
            Imyhat.BOOLEAN,
            new FunctionParameter("input number", Imyhat.FLOAT)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "float", "is_nan"),
            Double.class,
            "isNaN",
            "Check if the number is not-a-number.",
            Imyhat.BOOLEAN,
            new FunctionParameter("input number", Imyhat.FLOAT)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "float", "max"),
            Math.class,
            "max",
            "Pick the maximum between two numbers",
            Imyhat.FLOAT,
            new FunctionParameter("input number", Imyhat.FLOAT),
            new FunctionParameter("input number", Imyhat.FLOAT)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "float", "min"),
            Math.class,
            "min",
            "Pick the minimum between two numbers",
            Imyhat.FLOAT,
            new FunctionParameter("input number", Imyhat.FLOAT),
            new FunctionParameter("input number", Imyhat.FLOAT)),
        new ClampFunction("float", Imyhat.FLOAT),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "integer", "max"),
            Math.class,
            "max",
            "Pick the maximum between two numbers",
            Imyhat.INTEGER,
            new FunctionParameter("input number", Imyhat.INTEGER),
            new FunctionParameter("input number", Imyhat.INTEGER)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "integer", "min"),
            Math.class,
            "min",
            "Pick the minimum between two numbers",
            Imyhat.INTEGER,
            new FunctionParameter("input number", Imyhat.INTEGER),
            new FunctionParameter("input number", Imyhat.INTEGER)),
        new ClampFunction("integer", Imyhat.INTEGER),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "date", "start_of_day"),
            StandardDefinitions.class,
            "start_of_day",
            "Rounds a date-time to the previous midnight.",
            Imyhat.DATE,
            new FunctionParameter("date", Imyhat.DATE)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "json", "object"),
            StandardDefinitions.class,
            "jsonObject",
            "Create a JSON object from fields.",
            Imyhat.JSON,
            new FunctionParameter("fields", Imyhat.tuple(Imyhat.STRING, Imyhat.JSON).asList())),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "json", "array_from_dict"),
            RuntimeSupport.class,
            "jsonMap",
            "Convert a dictionary to an array of arrays. If a dictionary has strings for keys, it will normally be encoded as a JSON object. For other key types, it will be encoded as a JSON array of two element arrays. This function forces conversion of a dictionary with string keys to the array-of-arrays JSON encoding. Shesmu will be able to convert either back to dictionary.",
            Imyhat.JSON,
            new FunctionParameter("Map to encode", Imyhat.dictionary(Imyhat.STRING, Imyhat.JSON))),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "file"),
            "getFileName",
            "Extracts the last element in a path.",
            Imyhat.PATH,
            new FunctionParameter("input path", Imyhat.PATH)),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "dir"),
            "getParent",
            "Extracts all but the last elements in a path (i.e., the containing directory).",
            Imyhat.PATH,
            new FunctionParameter("input path", Imyhat.PATH)),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "normalize"),
            "normalize",
            "Normalize a path (i.e., remove any ./ and ../ in the path).",
            Imyhat.PATH,
            new FunctionParameter("input path", Imyhat.PATH)),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "relativize"),
            "relativize",
            "Creates a new path of relativize one path as if in the directory of the other.",
            Imyhat.PATH,
            new FunctionParameter("directory path", Imyhat.PATH),
            new FunctionParameter("path to relativize", Imyhat.PATH)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "replace_home"),
            StandardDefinitions.class,
            "replaceHome",
            "Replace any path that starts with $HOME or ~ with the provided home directory",
            Imyhat.PATH,
            new FunctionParameter("path to change", Imyhat.PATH),
            new FunctionParameter("home directory", Imyhat.PATH)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "version_at_least"),
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
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "trim"),
            "trim",
            "Remove white space from a string.",
            Imyhat.STRING,
            new FunctionParameter("input to trim", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "truncate"),
            RuntimeSupport.class,
            "truncate",
            "Truncates a string to a maximum length.",
            Imyhat.STRING,
            new FunctionParameter("input string", Imyhat.STRING),
            new FunctionParameter("maximum length", Imyhat.INTEGER),
            new FunctionParameter(
                "method",
                Imyhat.algebraicTuple("START")
                    .unify(Imyhat.algebraicTuple("START_ELLIPSIS", Imyhat.STRING))
                    .unify(Imyhat.algebraicTuple("MIDDLE"))
                    .unify(Imyhat.algebraicTuple("MIDDLE_ELLIPSIS", Imyhat.STRING))
                    .unify(Imyhat.algebraicTuple("END"))
                    .unify(Imyhat.algebraicTuple("END_ELLIPSIS", Imyhat.STRING)))),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "lower"),
            "toLowerCase",
            "Convert a string to lower case.",
            Imyhat.STRING,
            new FunctionParameter("input to convert", Imyhat.STRING)),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "upper"),
            "toUpperCase",
            "Convert a string to upper case.",
            Imyhat.STRING,
            new FunctionParameter("input to convert", Imyhat.STRING)),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "eq"),
            "equalsIgnoreCase",
            "Compares two strings ignoring case.",
            Imyhat.BOOLEAN,
            new FunctionParameter("first", Imyhat.STRING),
            new FunctionParameter("second", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "integer", "parse"),
            RuntimeSupport.class,
            "parseLong",
            "Convert a string containing digits into an integer.",
            Imyhat.INTEGER.asOptional(),
            new FunctionParameter("String to parse", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "boolean", "parse"),
            RuntimeSupport.class,
            "parseBool",
            "Convert a string containing into a Boolean.",
            Imyhat.BOOLEAN.asOptional(),
            new FunctionParameter("String to parse", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "float", "parse"),
            RuntimeSupport.class,
            "parseDouble",
            "Convert a string containing digits and a decimal point into an float.",
            Imyhat.FLOAT.asOptional(),
            new FunctionParameter("String to parse", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "json", "parse"),
            RuntimeSupport.class,
            "parseJson",
            "Convert a string containing JSON data into a JSON value.",
            Imyhat.JSON.asOptional(),
            new FunctionParameter("String to parse", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "url", "decode"),
            RuntimeSupport.class,
            "urlDecode",
            "Convert a URL-encoded string back to a normal string.",
            Imyhat.STRING.asOptional(),
            new FunctionParameter("String to encode", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "url", "encode"),
            RuntimeSupport.class,
            "urlEncode",
            "Convert a string to a URL-encoded string (also escaping *, even though that is not standard).",
            Imyhat.STRING,
            new FunctionParameter("String to encode", Imyhat.STRING)),
        FunctionDefinition.staticMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "change_prefix"),
            RuntimeSupport.class,
            "changePrefix",
            "Replaces the prefix of a path with a replacement. A number of prefixes are provided and the longest match is the one that is selected. If no match is found, the path is returned unchanged. This is mean to reconcile different mounts or symlink trees of the same file tree.",
            Imyhat.PATH,
            new FunctionParameter("The path to adjust", Imyhat.PATH),
            new FunctionParameter(
                "A dictionary of prefix paths and the replacement that should be used.",
                Imyhat.dictionary(Imyhat.PATH, Imyhat.PATH))),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "ends_with"),
            "endsWith",
            "Checks if ends with the directory and filename suffix provided.",
            Imyhat.BOOLEAN,
            new FunctionParameter("The path to check", Imyhat.PATH),
            new FunctionParameter("The suffix path", Imyhat.PATH)),
        FunctionDefinition.virtualMethod(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "path", "starts_with"),
            "startsWith",
            "Checks if starts with the directory prefix provided.",
            Imyhat.BOOLEAN,
            new FunctionParameter("The path to check", Imyhat.PATH),
            new FunctionParameter("The prefix directory", Imyhat.PATH)),
        FunctionDefinition.virtualIntegerFunction(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "length"),
            "length",
            "Gets the length of a string.",
            new FunctionParameter("input to measure", Imyhat.STRING)),
        FunctionDefinition.virtualIntegerFunction(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "hash"),
            "hashCode",
            "Compute the hash of a string (equivalent to Java's hashCode).",
            new FunctionParameter("string to hash", Imyhat.STRING)),
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
            return String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "replace");
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
            return String.join(Parser.NAMESPACE_SEPARATOR, "std", "string", "to_path");
          }

          @Override
          public Stream<FunctionParameter> parameters() {
            return Stream.of(new FunctionParameter("input to convert", Imyhat.STRING));
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
        FunctionDefinition.cast(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "float", "to_integer"),
            Imyhat.INTEGER,
            Imyhat.FLOAT,
            "Truncates a floating point number of an integer."),
        FunctionDefinition.cast(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "integer", "to_float"),
            Imyhat.FLOAT,
            Imyhat.INTEGER,
            "Converts an integer to a floating point number.")
      };
  private static final Method METHOD_INSTANT__NOW =
      new Method("now", A_INSTANT_TYPE, new Type[] {});
  private static final ConstantDefinition[] CONSTANTS =
      new ConstantDefinition[] {
        ConstantDefinition.of(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "date", "epoch"),
            Instant.EPOCH,
            "The date at UNIX timestamp 0: 1970-01-01T00:00:00Z"),
        new ConstantDefinition(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "date", "now"),
            Imyhat.DATE,
            "The current timestamp. This is fetched every time this constant is referenced, so now != now.",
            null) {

          @Override
          public void load(GeneratorAdapter methodGen) {
            methodGen.invokeStatic(A_INSTANT_TYPE, METHOD_INSTANT__NOW);
          }
        }
      };
  private static final ActionDefinition NOTHING_ACTION =
      new ActionDefinition(
          String.join(Parser.NAMESPACE_SEPARATOR, "std", "nothing"),
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
        new SignatureVariableForDynamicSigner(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "json", "signature"), Imyhat.JSON) {

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
        new SignatureVariableForDynamicSigner(
            String.join(Parser.NAMESPACE_SEPARATOR, "std", "signature", "sha1"), Imyhat.STRING) {

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

  @Override
  public Stream<ActionDefinition> actions() {
    return Stream.of(NOTHING_ACTION);
  }

  @Override
  public Stream<ConstantDefinition> constants() {
    return Stream.of(CONSTANTS);
  }

  @Override
  public Stream<CallableOliveDefinition> oliveDefinitions() {
    return Stream.empty();
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
