package ca.on.oicr.gsi.shesmu.niassa;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.Tuple;
import ca.on.oicr.gsi.shesmu.plugin.Utils;
import ca.on.oicr.gsi.shesmu.plugin.action.CustomActionParameter;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.plugin.wdl.WdlInputType;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Creates a parameter that will be formatted and saved as INI parameter for a {@link
 * WorkflowAction}
 */
public final class IniParam<T> {
  /** Save a Boolean value as "true" or "false" */
  public static final Stringifier BOOLEAN =
      new Stringifier() {

        @Override
        public String stringify(WorkflowAction action, Object value) {
          return value.toString();
        }

        @Override
        public Imyhat type() {
          return Imyhat.BOOLEAN;
        }
      };
  /** Save an integer in the way you'd expect */
  public static final Stringifier INTEGER =
      new Stringifier() {

        @Override
        public String stringify(WorkflowAction action, Object value) {
          return value.toString();
        }

        @Override
        public Imyhat type() {
          return Imyhat.INTEGER;
        }
      };
  /** Save a string exactly as it is passed by the user */
  public static final Stringifier STRING =
      new Stringifier() {

        @Override
        public String stringify(WorkflowAction action, Object value) {
          return (String) value;
        }

        @Override
        public Imyhat type() {
          return Imyhat.STRING;
        }
      };
  /** Save a path */
  public static final Stringifier PATH =
      new Stringifier() {

        @Override
        public String stringify(WorkflowAction action, Object value) {
          return value.toString();
        }

        @Override
        public Imyhat type() {
          return Imyhat.PATH;
        }
      };

  private String iniName;
  private String name;
  private boolean required;
  private Stringifier type;

  public IniParam() {}

  /**
   * Convert a date to the specified format, in UTC.
   *
   * @param format a format understandable by {@link DateTimeFormatter#ofPattern(String)}
   */
  public static Stringifier date(String format) {
    return new Stringifier() {
      private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);

      @Override
      public String stringify(WorkflowAction action, Object value) {
        return formatter.format(LocalDateTime.ofInstant((Instant) value, ZoneOffset.UTC));
      }

      @Override
      public Imyhat type() {
        return Imyhat.DATE;
      }
    };
  }

  /**
   * Convert a list of items into a delimited string
   *
   * <p>No attempt is made to check that the items do not contain the delimiter
   *
   * @param delimiter the delimiter between the items
   * @param inner the type of the items to be concatenated
   */
  public static Stringifier list(String delimiter, Stringifier inner) {
    return new Stringifier() {

      @Override
      public String stringify(WorkflowAction action, Object values) {
        return ((Set<?>) values)
            .stream()
            .map(value -> inner.stringify(action, value))
            .collect(Collectors.joining(delimiter));
      }

      @Override
      public Imyhat type() {
        return inner.type().asList();
      }
    };
  }

  /**
   * Concatenate a tuple of different items as a delimited string
   *
   * @param delimiter the delimiter between the items
   * @param inner the items in the tuple
   */
  public static Stringifier tuple(String delimiter, Stream<Stringifier> inner) {
    return new Stringifier() {
      private final List<Pair<Integer, Stringifier>> contents =
          inner
              .map(
                  new Function<Stringifier, Pair<Integer, Stringifier>>() {
                    private int index;

                    @Override
                    public Pair<Integer, Stringifier> apply(Stringifier stringifier) {
                      return new Pair<>(index++, stringifier);
                    }
                  })
              .collect(Collectors.toList());

      private <T> String apply(WorkflowAction action, Stringifier stringifier, Object value) {
        return stringifier.stringify(action, value);
      }

      @Override
      public String stringify(WorkflowAction action, Object v) {
        final Tuple value = (Tuple) v;
        return contents
            .stream()
            .map(p -> apply(action, p.second(), value.get(p.first())))
            .collect(Collectors.joining(delimiter));
      }

      @Override
      public Imyhat type() {
        return Imyhat.tuple(contents.stream().map(p -> p.second().type()).toArray(Imyhat[]::new));
      }
    };
  }

  /**
   * Save an integer, but first correct the units
   *
   * <p>We have this problem where workflows use different units as parameters (e.g., memory is in
   * megabytes). We want all values in Shesmu to be specified in base units (bytes, bases) because
   * it has convenient suffixes. This will divide the value specified into those units and round
   * accordingly so the user never has to be concerned about this.
   *
   * @param factor the units of the target value (i.e., 1024*1024 for a value in megabytes)
   */
  public static Stringifier correctInteger(int factor) {
    return new Stringifier() {

      @Override
      public String stringify(WorkflowAction action, Object v) {
        final long value = (Long) v;
        if (value == 0) {
          return "0";
        }
        int round;
        if (value % factor == 0) {
          round = 0;
        } else {
          round = value < 0 ? -1 : 1;
        }
        return Long.toString(value / factor + round);
      }

      @Override
      public Imyhat type() {
        return Imyhat.INTEGER;
      }
    };
  }

  public boolean getRequired() {
    return required;
  }

  public void setRequired(boolean required) {
    this.required = required;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  CustomActionParameter<WorkflowAction> parameter() {
    return new CustomActionParameter<WorkflowAction>(name, required, type.type()) {
      @Override
      public void store(WorkflowAction action, Object value) {
        action.ini.put(iniName, type.stringify(action, value));
      }
    };
  }

  public String getIniName() {
    return iniName;
  }

  public void setIniName(String iniName) {
    this.iniName = iniName;
  }

  public Stringifier getType() {
    return type;
  }

  public void setType(Stringifier type) {
    this.type = type;
  }

  @JsonDeserialize(using = StringifierDeserializer.class)
  public abstract static class Stringifier {
    public abstract String stringify(WorkflowAction action, Object value);

    public abstract Imyhat type();
  }

  public static class StringifierDeserializer extends JsonDeserializer<Stringifier> {

    private Stringifier deserialize(JsonNode node) {
      if (node.isTextual()) {
        final String str = node.asText();
        switch (str) {
          case "boolean":
            return BOOLEAN;
          case "integer":
            return INTEGER;
          case "path":
            return PATH;
          case "string":
            return STRING;
          default:
            throw new IllegalArgumentException("Unknown INI type: " + str);
        }
      }
      if (node.isNumber()) {
        return correctInteger(node.asInt());
      }
      if (node.isObject()) {
        final String type = node.get("is").asText();
        switch (type) {
          case "date":
            return date(node.get("format").asText());
          case "list":
            return list(node.get("delimiter").asText(), deserialize(node.get("of")));
          case "tuple":
            return tuple(
                node.get("delimiter").asText(),
                Utils.stream(node.get("of")).map(this::deserialize));
          case "wdl":
            return wdl((ObjectNode) node.get("parameters"));
          default:
            throw new IllegalArgumentException("Unknown INI type: " + type);
        }
      }
      throw new IllegalArgumentException("Cannot parse INI type: " + node.getNodeType());
    }

    private Stringifier wdl(ObjectNode inputs) {
      final Pair<Function<ObjectNode, ImyhatConsumer>, Imyhat> handler =
          PackWdlVariables.create(
              WdlInputType.of(
                  inputs,
                  (line, column, errorMessage) ->
                      System.err.printf("%d:%d: %s\n", line, column, errorMessage)));
      return new Stringifier() {
        @Override
        public String stringify(WorkflowAction action, Object value) {
          final ObjectNode result = NiassaServer.MAPPER.createObjectNode();
          handler.second().accept(handler.first().apply(result), value);
          try {
            return NiassaServer.MAPPER.writeValueAsString(result);
          } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{}";
          }
        }

        @Override
        public Imyhat type() {
          return handler.second();
        }
      };
    }

    @Override
    public Stringifier deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      final ObjectCodec oc = parser.getCodec();
      final JsonNode node = oc.readTree(parser);
      return deserialize(node);
    }
  }
}
