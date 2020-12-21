package ca.on.oicr.gsi.shesmu;

import ca.on.oicr.gsi.shesmu.compiler.*;
import ca.on.oicr.gsi.shesmu.compiler.Compiler;
import ca.on.oicr.gsi.shesmu.compiler.definitions.*;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.functions.FunctionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.util.NameLoader;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;

/**
 * The command-line checker for Shesmu scripts
 *
 * <p>This talks to a running Shesmu server and uses the actions, functions, and constants it knows
 * to validate a script. This cannot compile the script, so no bytecode generation is attempted.
 */
public final class Check extends Compiler {
  public static Stream<ObjectNode> fetch(String remote, String slug) {
    return fetch(remote, slug, ObjectNode[].class).map(Stream::of).orElse(Stream.empty());
  }

  public static <T> Optional<T> fetch(String remote, String slug, Class<T> clazz) {
    final HttpGet request = new HttpGet(String.format("%s/%s", remote, slug));
    try (CloseableHttpResponse response = HTTP_CLIENT.execute(request)) {
      return Optional.of(RuntimeSupport.MAPPER.readValue(response.getEntity().getContent(), clazz));
    } catch (final Exception e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  public static void main(String[] args) {
    final Options options = new Options();
    options.addOption("h", "help", false, "This dreck.");
    options.addOption(
        "r", "remote", true, "The remote instance with all the actions/functions/etc.");
    final CommandLineParser parser = new DefaultParser();
    String[] files;
    String remote = "http://localhost:8081/";
    try {
      final CommandLine cmd = parser.parse(options, args);

      if (cmd.hasOption("h")) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("Shesmu Compiler", options);
        System.exit(0);
        return;
      }
      if (cmd.hasOption('r')) {
        remote = cmd.getOptionValue('r');
      }
      if (cmd.getArgs().length == 0) {
        System.err.println("At least one file must be specified to compile.");
        System.exit(1);
        return;
      }
      files = cmd.getArgs();
    } catch (final ParseException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      return;
    }
    final List<ConstantDefinition> constants =
        fetch(remote, "constants") //
            .map(
                o ->
                    new ConstantDefinition(
                        o.get("name").asText(),
                        Imyhat.parse(o.get("type").asText()),
                        o.get("description").asText(),
                        null) {

                      @Override
                      public void load(GeneratorAdapter methodGen) {
                        throw new UnsupportedOperationException();
                      }
                    }) //
            .collect(Collectors.toList());
    final List<SignatureDefinition> signatures =
        fetch(remote, "signatures") //
            .map(
                o ->
                    new SignatureDefinition(
                        o.get("name").asText(), null, Imyhat.parse(o.get("type").asText())) {

                      @Override
                      public void build(
                          GeneratorAdapter method,
                          Type streamType,
                          Stream<SignableRenderer> variables) {
                        throw new UnsupportedOperationException();
                      }

                      @Override
                      public Path filename() {
                        return null;
                      }
                    }) //
            .collect(Collectors.toList());
    final NameLoader<InputFormatDefinition> inputFormats =
        new NameLoader<>(
            fetch(remote, "variables", ObjectNode.class)
                .map(Check::makeInputFormat)
                .orElse(Stream.empty()),
            InputFormatDefinition::name);
    final NameLoader<FunctionDefinition> functions =
        new NameLoader<>(
            fetch(remote, "functions").map(Check::makeFunction), FunctionDefinition::name);
    final NameLoader<ActionDefinition> actions =
        new NameLoader<>(fetch(remote, "actions").map(Check::makeAction), ActionDefinition::name);
    final NameLoader<RefillerDefinition> refillers =
        new NameLoader<>(
            fetch(remote, "refillers").map(Check::makeRefiller), RefillerDefinition::name);
    final NameLoader<CallableDefinition> oliveDefinitions =
        new NameLoader<>(
            fetch(remote, "olivedefinitions").map(Check::makeOliveDefinition),
            CallableDefinition::name);

    final boolean ok =
        Stream.of(files)
                .filter(
                    file -> {
                      boolean fileOk;
                      try {
                        fileOk =
                            new Check(
                                    file,
                                    inputFormats,
                                    functions,
                                    actions,
                                    refillers,
                                    oliveDefinitions)
                                .compile(
                                    Files.readAllBytes(Paths.get(file)),
                                    "dyn/shesmu/Program",
                                    file,
                                    constants::stream,
                                    signatures::stream,
                                    null,
                                    null);
                      } catch (final IOException e) {
                        e.printStackTrace();
                        fileOk = false;
                      }
                      System.err.printf(
                          "%s\033[0m\t%s%n", fileOk ? "\033[1;36mOK" : "\033[1;31mFAIL", file);
                      return fileOk;
                    })
                .count()
            == files.length;
    System.exit(ok ? 0 : 1);
  }

  private static ActionDefinition makeAction(ObjectNode node) {
    return new ActionDefinition(
        node.get("name").asText(),
        node.get("description").asText(),
        null,
        Utils.stream(node.get("parameters").elements()).map(Check::makeParameter)) {

      @Override
      public void initialize(GeneratorAdapter methodGen) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static FunctionDefinition makeFunction(ObjectNode node) {
    final String name = node.get("name").asText();
    final String description = node.get("description").asText();
    final Imyhat returnType = Imyhat.parse(node.get("return").asText());
    final FunctionParameter[] parameters =
        Utils.stream(node.get("parameters").elements())
            .map(
                p ->
                    new FunctionParameter(
                        p.get("description").asText(), Imyhat.parse(p.get("type").asText())))
            .toArray(FunctionParameter[]::new);
    return new FunctionDefinition() {

      @Override
      public String description() {
        return description;
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
        return Arrays.stream(parameters);
      }

      @Override
      public void render(GeneratorAdapter methodGen) {
        throw new UnsupportedOperationException();
      }

      @Override
      public final void renderStart(GeneratorAdapter methodGen) {
        // None required.
      }

      @Override
      public Imyhat returnType() {
        return returnType;
      }
    };
  }

  private static Stream<InputFormatDefinition> makeInputFormat(ObjectNode node) {
    return Utils.stream(node.fields())
        .map(
            pair -> {
              final List<InputVariable> variables =
                  Utils.stream(pair.getValue().get("variables").fields())
                      .map(
                          field -> {
                            final String name = field.getKey();
                            final Imyhat type = Imyhat.parse(field.getValue().asText());
                            return new InputVariable() {

                              @Override
                              public void extract(GeneratorAdapter method) {
                                throw new UnsupportedOperationException("Checker cannot render");
                              }

                              @Override
                              public Flavour flavour() {
                                return Flavour.STREAM;
                              }

                              @Override
                              public String name() {
                                return name;
                              }

                              @Override
                              public void read() {
                                // Interesting. Don't care.
                              }

                              @Override
                              public Imyhat type() {
                                return type;
                              }
                            };
                          })
                      .collect(Collectors.toList());
              final List<? extends GangDefinition> gangs =
                  Utils.stream(pair.getValue().get("gangs").fields())
                      .map(
                          gang ->
                              new GangDefinition() {
                                private final String name = gang.getKey();
                                private final List<GangElement> elements =
                                    Utils.stream(gang.getValue().elements())
                                        .map(
                                            e ->
                                                new GangElement(
                                                    e.get("name").asText(),
                                                    Imyhat.parse(e.get("type").asText()),
                                                    e.get("dropIfDefault").asBoolean()))
                                        .collect(Collectors.toList());;

                                @Override
                                public Stream<GangElement> elements() {
                                  return elements.stream();
                                }

                                @Override
                                public String name() {
                                  return name;
                                }
                              })
                      .collect(Collectors.toList());
              final String name = pair.getKey();
              return new InputFormatDefinition() {

                @Override
                public Stream<InputVariable> baseStreamVariables() {
                  return variables.stream();
                }

                @Override
                public String name() {
                  return name;
                }

                @Override
                public Stream<? extends GangDefinition> gangs() {
                  return gangs.stream();
                }

                @Override
                public Type type() {
                  throw new UnsupportedOperationException("Checker formats cannot be used.");
                }
              };
            });
  }

  private static ActionParameterDefinition makeParameter(JsonNode node) {
    final String name = node.get("name").asText();
    final Imyhat type = Imyhat.parse(node.get("type").asText());
    final boolean required = node.get("required").asBoolean();
    return new ActionParameterDefinition() {

      @Override
      public String name() {
        return name;
      }

      @Override
      public boolean required() {
        return required;
      }

      @Override
      public void store(Renderer renderer, int actionLocal, Consumer<Renderer> loadParameter) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Imyhat type() {
        return type;
      }
    };
  }

  private static RefillerDefinition makeRefiller(ObjectNode node) {
    return new RefillerDefinition() {
      final String description = node.get("description").asText();
      final String name = node.get("name").asText();
      final List<RefillerParameterDefinition> parameters =
          Utils.stream(node.get("parameters").elements())
              .map(
                  parameter ->
                      new RefillerParameterDefinition() {
                        final String name = parameter.get("name").asText();
                        final Imyhat type = Imyhat.parse(parameter.get("type").asText());

                        @Override
                        public String name() {
                          return name;
                        }

                        @Override
                        public void render(
                            Renderer renderer, int refillerLocal, int functionLocal) {
                          throw new UnsupportedOperationException();
                        }

                        @Override
                        public Imyhat type() {
                          return type;
                        }
                      })
              .collect(Collectors.toList());

      @Override
      public String description() {
        return description;
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
      public Stream<RefillerParameterDefinition> parameters() {
        return parameters.stream();
      }

      @Override
      public void render(Renderer renderer) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static CallableDefinition makeOliveDefinition(ObjectNode node) {
    return new CallableDefinition() {
      final boolean isRoot = node.get("isRoot").asBoolean();
      final String name = node.get("name").asText();
      final String format = node.get("format").asText();
      final List<Imyhat> parameters =
          Utils.stream(node.get("parameters").elements())
              .map(parameter -> Imyhat.parse(parameter.asText()))
              .collect(Collectors.toList());
      final List<Target> output =
          Utils.stream(node.get("output").fields())
              .map(
                  output ->
                      new Target() {
                        private final String name = output.getKey();
                        private final Imyhat type = Imyhat.parse(output.getValue().asText());

                        @Override
                        public Flavour flavour() {
                          return Flavour.STREAM;
                        }

                        @Override
                        public String name() {
                          return name;
                        }

                        @Override
                        public void read() {
                          // Don't care.
                        }

                        @Override
                        public Imyhat type() {
                          return type;
                        }
                      })
              .collect(Collectors.toList());

      @Override
      public void collectSignables(
          Set<String> signableNames, Consumer<SignableVariableCheck> addSignableCheck) {
        // Pretend like there's none, since we can't know.
      }

      @Override
      public Stream<OliveClauseRow> dashboardInner(Optional<String> label, int line, int column) {
        return Stream.empty();
      }

      @Override
      public Path filename() {
        return null;
      }

      @Override
      public String format() {
        return format;
      }

      @Override
      public boolean isRoot() {
        return isRoot;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public Optional<Stream<Target>> outputStreamVariables(
          OliveCompilerServices oliveCompilerServices, Consumer<String> errorHandler) {
        return Optional.of(output.stream());
      }

      @Override
      public int parameterCount() {
        return parameters.size();
      }

      @Override
      public Imyhat parameterType(int index) {
        return parameters.get(index);
      }
    };
  }

  static final CloseableHttpClient HTTP_CLIENT = HttpClients.createDefault();
  private final NameLoader<ActionDefinition> actions;
  private final NameLoader<CallableDefinition> definitions;
  private final String fileName;
  private final NameLoader<FunctionDefinition> functions;
  private final NameLoader<InputFormatDefinition> inputFormats;
  private final NameLoader<RefillerDefinition> refillers;

  Check(
      String fileName,
      NameLoader<InputFormatDefinition> inputFormats,
      NameLoader<FunctionDefinition> functions,
      NameLoader<ActionDefinition> actions,
      NameLoader<RefillerDefinition> refillers,
      NameLoader<CallableDefinition> definitions) {
    super(true);
    this.fileName = fileName;
    this.inputFormats = inputFormats;
    this.functions = functions;
    this.actions = actions;
    this.refillers = refillers;
    this.definitions = definitions;
  }

  @Override
  protected ClassVisitor createClassVisitor() {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void errorHandler(String message) {
    System.out.printf("%s:%s%n", fileName, message);
  }

  @Override
  protected ActionDefinition getAction(String name) {
    return actions.get(name);
  }

  @Override
  protected FunctionDefinition getFunction(String function) {
    return functions.get(function);
  }

  @Override
  protected InputFormatDefinition getInputFormats(String name) {
    return inputFormats.get(name);
  }

  @Override
  protected CallableDefinition getOliveDefinition(String name) {
    return definitions.get(name);
  }

  @Override
  protected CallableDefinitionRenderer getOliveDefinitionRenderer(String name) {
    return null;
  }

  @Override
  protected RefillerDefinition getRefiller(String name) {
    return refillers.get(name);
  }
}
