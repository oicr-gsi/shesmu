package ca.on.oicr.gsi.shesmu.plugin.jsonschema;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonAnyFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonArrayFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonBooleanFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitable;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonIntegerFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonMapFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNullFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonNumberFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonStringFormatVisitor;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

public class ImyhatJsonFormatVisitor implements JsonFormatVisitorWrapper {
  private final Consumer<Imyhat> output;

  public ImyhatJsonFormatVisitor(Consumer<Imyhat> output) {
    this.output = output;
  }

  @Override
  public JsonObjectFormatVisitor expectObjectFormat(JavaType javaType) throws JsonMappingException {
    return new JsonObjectFormatVisitor.Base() {
      private final Map<String, Imyhat> fields = new TreeMap<>();

      @Override
      public void property(String name, JsonFormatVisitable handler, JavaType propertyTypeHint)
          throws JsonMappingException {
        handler.acceptJsonFormatVisitor(
            new ImyhatJsonFormatVisitor(
                t -> {
                  fields.put(name, t);
                  emit();
                }),
            propertyTypeHint);
      }

      private void emit() {
        // This visitor design does't let us know when it's done, so we regenerate and send the
        // result to our caller for every new field.
        output.accept(
            new ObjectImyhat(
                fields.entrySet().stream().map(e -> new Pair<>(e.getKey(), e.getValue()))));
      }

      @Override
      public void optionalProperty(
          String name, JsonFormatVisitable handler, JavaType propertyTypeHint)
          throws JsonMappingException {
        handler.acceptJsonFormatVisitor(
            new ImyhatJsonFormatVisitor(
                t -> {
                  fields.put(name, t.asOptional());
                  emit();
                }),
            propertyTypeHint);
      }
    };
  }

  @Override
  public JsonArrayFormatVisitor expectArrayFormat(JavaType javaType) throws JsonMappingException {
    return new JsonArrayFormatVisitor.Base() {
      @Override
      public void itemsFormat(JsonFormatVisitable handler, JavaType elementType)
          throws JsonMappingException {
        handler.acceptJsonFormatVisitor(
            new ImyhatJsonFormatVisitor(inner -> output.accept(inner.asList())), elementType);
      }
    };
  }

  @Override
  public JsonStringFormatVisitor expectStringFormat(JavaType javaType) throws JsonMappingException {
    output.accept(Imyhat.STRING);
    return new JsonStringFormatVisitor.Base();
  }

  @Override
  public JsonNumberFormatVisitor expectNumberFormat(JavaType javaType) throws JsonMappingException {
    output.accept(Imyhat.FLOAT);
    return new JsonNumberFormatVisitor.Base();
  }

  @Override
  public JsonIntegerFormatVisitor expectIntegerFormat(JavaType javaType)
      throws JsonMappingException {
    output.accept(Imyhat.INTEGER);
    return new JsonIntegerFormatVisitor.Base();
  }

  @Override
  public JsonBooleanFormatVisitor expectBooleanFormat(JavaType javaType)
      throws JsonMappingException {
    output.accept(Imyhat.BOOLEAN);
    return new JsonBooleanFormatVisitor.Base();
  }

  @Override
  public JsonNullFormatVisitor expectNullFormat(JavaType javaType) throws JsonMappingException {
    output.accept(Imyhat.NOTHING);
    return new JsonNullFormatVisitor.Base();
  }

  @Override
  public JsonAnyFormatVisitor expectAnyFormat(JavaType javaType) throws JsonMappingException {
    output.accept(Imyhat.JSON);
    return new JsonAnyFormatVisitor.Base();
  }

  @Override
  public JsonMapFormatVisitor expectMapFormat(JavaType javaType) throws JsonMappingException {
    return new JsonMapFormatVisitor.Base() {
      private Imyhat key;
      private Imyhat value;

      private void emit() {
        if (key != null && value != null) {
          output.accept(Imyhat.dictionary(key, value));
        }
      }

      @Override
      public void keyFormat(JsonFormatVisitable jsonFormatVisitable, JavaType javaType)
          throws JsonMappingException {
        jsonFormatVisitable.acceptJsonFormatVisitor(
            new ImyhatJsonFormatVisitor(
                k -> {
                  key = k;
                  emit();
                }),
            javaType);
      }

      @Override
      public void valueFormat(JsonFormatVisitable jsonFormatVisitable, JavaType javaType)
          throws JsonMappingException {
        jsonFormatVisitable.acceptJsonFormatVisitor(
            new ImyhatJsonFormatVisitor(
                v -> {
                  value = v;
                  emit();
                }),
            javaType);
      }
    };
  }

  private SerializerProvider provider;

  @Override
  public SerializerProvider getProvider() {
    return provider;
  }

  @Override
  public void setProvider(SerializerProvider serializerProvider) {
    this.provider = serializerProvider;
  }
}
