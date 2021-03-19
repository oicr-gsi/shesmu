package ca.on.oicr.gsi.shesmu.plugin.jsonschema;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.ObjectImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema.NoAdditionalProperties;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema.SchemaAdditionalProperties;
import com.fasterxml.jackson.module.jsonSchema.types.ReferenceSchema;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

/** Allow converting between Shemu's type system and JSON Schema/Jackson's type schema */
public final class JsonSchemaUtils {

  /** Convert a Shesmu type to a JSON schema */
  public static final ImyhatTransformer<JsonSchema> TO_SCHEMA = new JsonSchemaConverter();

  /**
   * Convert a Jackson type to a Shesmu type
   *
   * @param mapper the object mapper to resolve format information
   * @param type the type to be resolved
   * @return the resulting type; it may be the bad type if errors were encountered
   * @throws JsonMappingException if a class was encountered which Jackson could not explore
   *     properly due to the configuration of the mapper
   */
  public static Imyhat convert(ObjectMapper mapper, JavaType type) throws JsonMappingException {
    final var result = new AtomicReference<>(Imyhat.BAD);
    mapper.acceptJsonFormatVisitor(type, new ImyhatJsonFormatVisitor(result::set));
    return result.get();
  }

  /**
   * Convert a JSON schema to a Shesmu type
   *
   * @param schema the JSON schema to convert
   * @param inferDictionaries if true, schemas that resemble <tt>[[X, Y]]</tt> will be converted to
   *     <tt>X -> Y</tt>. This is how dictionaries with non-string keys are serialised by default in
   *     Shesmu.
   * @param resolver a function to handle <tt>$ref</tt> nodes in the schema; if null, referenes will
   *     be resolved to the bad type
   */
  public static Imyhat convert(
      JsonSchema schema, boolean inferDictionaries, Function<String, Imyhat> resolver) {
    if (schema == null) {
      return Imyhat.BAD;
    } else if (schema.isNullSchema()) {
      return Imyhat.NOTHING;
    } else if (schema.isAnySchema()) {
      return optional(schema, Imyhat.JSON);
    } else if (schema.isArraySchema()) {
      final var arraySchema = schema.asArraySchema();
      if (arraySchema.getItems().isArrayItems()) {
        final var arrayItems = arraySchema.getItems().asArrayItems();
        return optional(
            schema,
            Imyhat.tuple(
                Stream.of(arrayItems.getJsonSchemas())
                    .map(schema1 -> convert(schema1, inferDictionaries, resolver))
                    .toArray(Imyhat[]::new)));
      } else if (arraySchema.getItems().isSingleItems()) {
        final var inner = arraySchema.getItems().asSingleItems().getSchema();
        if (inferDictionaries
            && inner.isArraySchema()
            && inner.getRequired()
            && inner.asArraySchema().getItems().isArrayItems()
            && inner.asArraySchema().getItems().asArrayItems().getJsonSchemas().length == 2) {
          final var types = inner.asArraySchema().getItems().asArrayItems().getJsonSchemas();
          return optional(
              schema,
              Imyhat.dictionary(
                  convert(types[0], true, resolver), convert(types[1], true, resolver)));
        }
        return optional(schema, convert(inner, inferDictionaries, resolver).asList());
      }
    } else if (schema.isBooleanSchema()) {
      return optional(schema, Imyhat.BOOLEAN);
    } else if (schema.isIntegerSchema()) {
      return optional(schema, Imyhat.INTEGER);
    } else if (schema.isObjectSchema()) {
      final var objectSchema = schema.asObjectSchema();
      if (objectSchema.getPatternProperties().isEmpty()
          && (objectSchema.getAdditionalProperties() == null
              || objectSchema.getAdditionalProperties() instanceof NoAdditionalProperties)) {
        return optional(
            objectSchema,
            new ObjectImyhat(
                objectSchema.getProperties().entrySet().stream()
                    .map(
                        e ->
                            new Pair<>(
                                e.getKey(), convert(e.getValue(), inferDictionaries, resolver)))));
      } else if (objectSchema.getProperties().isEmpty()
          && objectSchema.getPatternProperties().isEmpty()
          && objectSchema.getAdditionalProperties() instanceof SchemaAdditionalProperties) {
        return optional(
            objectSchema,
            Imyhat.dictionary(
                Imyhat.STRING,
                convert(
                    ((SchemaAdditionalProperties) objectSchema.getAdditionalProperties())
                        .getJsonSchema(),
                    inferDictionaries,
                    resolver)));
      }
    } else if (schema.isStringSchema()) {
      return optional(schema, Imyhat.STRING);
    } else if (resolver != null && schema instanceof ReferenceSchema) {
      return optional(schema, resolver.apply(schema.get$ref()));
    }
    return Imyhat.BAD;
  }

  private static Imyhat optional(JsonSchema schema, Imyhat type) {
    return schema.getRequired() ? type : type.asOptional();
  }

  private JsonSchemaUtils() {}
}
