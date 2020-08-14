package ca.on.oicr.gsi.shesmu.plugin.jsonschema;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatTransformer;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonValueFormat;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.types.AnySchema;
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema;
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema.ArrayItems;
import com.fasterxml.jackson.module.jsonSchema.types.ArraySchema.SingleItems;
import com.fasterxml.jackson.module.jsonSchema.types.IntegerSchema;
import com.fasterxml.jackson.module.jsonSchema.types.NullSchema;
import com.fasterxml.jackson.module.jsonSchema.types.NumberSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema.SchemaAdditionalProperties;
import com.fasterxml.jackson.module.jsonSchema.types.StringSchema;
import com.fasterxml.jackson.module.jsonSchema.types.UnionTypeSchema;
import com.fasterxml.jackson.module.jsonSchema.types.ValueTypeSchema;
import java.util.stream.Stream;

final class JsonSchemaConverter implements ImyhatTransformer<JsonSchema> {

  @Override
  public JsonSchema algebraic(Stream<AlgebraicTransformer> contents) {
    final UnionTypeSchema result = new UnionTypeSchema();
    result.setElements(
        contents
            .map(
                c ->
                    c.visit(
                        new AlgebraicVisitor<ValueTypeSchema>() {
                          @Override
                          public ValueTypeSchema empty(String name) {
                            final ObjectSchema object = new ObjectSchema();
                            object.rejectAdditionalProperties();
                            final StringSchema nameSchema = new StringSchema();
                            nameSchema.setPattern(name);
                            object.putProperty("type", nameSchema.asValueTypeSchema());
                            object.putProperty("contents", new NullSchema());
                            return object.asValueTypeSchema();
                          }

                          @Override
                          public ValueTypeSchema object(
                              String name, Stream<Pair<String, Imyhat>> contents) {
                            final ObjectSchema contentObject = new ObjectSchema();
                            contentObject.rejectAdditionalProperties();
                            contents.forEach(
                                field ->
                                    contentObject.putProperty(
                                        field.first(),
                                        field.second().apply(JsonSchemaConverter.this)));

                            final ObjectSchema object = new ObjectSchema();
                            object.rejectAdditionalProperties();
                            final StringSchema nameSchema = new StringSchema();
                            nameSchema.setPattern(name);
                            object.putProperty("type", nameSchema.asValueTypeSchema());
                            object.putProperty("contents", contentObject);
                            return object.asValueTypeSchema();
                          }

                          @Override
                          public ValueTypeSchema tuple(String name, Stream<Imyhat> contents) {
                            final ArraySchema contentArray = new ArraySchema();
                            contentArray.setItems(
                                new ArrayItems(
                                    contents
                                        .map(element -> element.apply(JsonSchemaConverter.this))
                                        .toArray(JsonSchema[]::new)));

                            final ObjectSchema object = new ObjectSchema();
                            object.rejectAdditionalProperties();
                            final StringSchema nameSchema = new StringSchema();
                            nameSchema.setPattern(name);
                            object.putProperty("type", nameSchema.asValueTypeSchema());
                            object.putProperty("contents", contentArray);
                            return object.asValueTypeSchema();
                          }
                        }))
            .toArray(ValueTypeSchema[]::new));
    return result;
  }

  @Override
  public JsonSchema bool() {
    return new StringSchema();
  }

  @Override
  public JsonSchema date() {
    final IntegerSchema schema = new IntegerSchema();
    schema.setFormat(JsonValueFormat.DATE_TIME);
    return schema;
  }

  @Override
  public JsonSchema floating() {
    return new NumberSchema();
  }

  @Override
  public JsonSchema integer() {
    return new IntegerSchema();
  }

  @Override
  public JsonSchema json() {
    return new AnySchema();
  }

  @Override
  public JsonSchema list(Imyhat inner) {
    final ArraySchema schema = new ArraySchema();
    schema.setItemsSchema(inner.apply(this));
    return schema;
  }

  @Override
  public JsonSchema map(Imyhat key, Imyhat value) {
    if (key.isSame(Imyhat.STRING)) {
      final ObjectSchema object = new ObjectSchema();
      object.setAdditionalProperties(new SchemaAdditionalProperties(value.apply(this)));
      return object;
    } else {
      final ArraySchema inner = new ArraySchema();
      inner.setItems(new ArrayItems(new JsonSchema[] {key.apply(this), value.apply(this)}));
      final ArraySchema schema = new ArraySchema();
      schema.setItems(new SingleItems(inner));
      return schema;
    }
  }

  @Override
  public JsonSchema object(Stream<Pair<String, Imyhat>> contents) {
    final ObjectSchema object = new ObjectSchema();
    object.rejectAdditionalProperties();
    contents.forEach(field -> object.putProperty(field.first(), field.second().apply(this)));
    return object;
  }

  @Override
  public JsonSchema optional(Imyhat inner) {
    if (inner == null) {
      return new NullSchema();
    } else {
      final JsonSchema schema = inner.apply(this);
      schema.setRequired(false);
      return schema;
    }
  }

  @Override
  public JsonSchema path() {
    return new StringSchema();
  }

  @Override
  public JsonSchema string() {
    return new StringSchema();
  }

  @Override
  public JsonSchema tuple(Stream<Imyhat> contents) {
    final ArraySchema schema = new ArraySchema();
    schema.setItems(new ArrayItems(contents.map(e -> e.apply(this)).toArray(JsonSchema[]::new)));
    return schema;
  }
}
