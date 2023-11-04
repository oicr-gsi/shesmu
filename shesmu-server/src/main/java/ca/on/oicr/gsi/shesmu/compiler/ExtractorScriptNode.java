package ca.on.oicr.gsi.shesmu.compiler;

import static ca.on.oicr.gsi.shesmu.compiler.TypeUtils.TO_ASM;

import ca.on.oicr.gsi.shesmu.compiler.ExtractionNode.OutputCollector;
import ca.on.oicr.gsi.shesmu.compiler.LambdaBuilder.LambdaType;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ConstantDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.ExtractionDataSource;
import ca.on.oicr.gsi.shesmu.compiler.definitions.FunctionDefinition;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.plugin.Parser;
import ca.on.oicr.gsi.shesmu.plugin.input.TimeFormat;
import ca.on.oicr.gsi.shesmu.plugin.json.PackStreaming;
import ca.on.oicr.gsi.shesmu.plugin.json.PackStreamingXml;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat.OptionalImyhat;
import ca.on.oicr.gsi.shesmu.plugin.types.ImyhatConsumer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;
import ca.on.oicr.gsi.shesmu.server.OutputFormat;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

public final class ExtractorScriptNode {
  private static final Type A_APPENDABLE_TYPE = Type.getType(Appendable.class);
  private static final Type A_BOOLEAN_TYPE = Type.getType(Boolean.class);
  private static final Type A_CSV_FORMAT_TYPE = Type.getType(CSVFormat.class);
  private static final Type A_CSV_PRINTER_TYPE = Type.getType(CSVPrinter.class);
  private static final Type A_DOUBLE_TYPE = Type.getType(Double.class);
  private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
  private static final Type A_JSON_GENERATOR_TYPE = Type.getType(JsonGenerator.class);
  private static final Type A_LONG_TYPE = Type.getType(Long.class);
  private static final Type A_OBJECT_ARRAY_TYPE = Type.getType(Object[].class);
  private static final Type A_OBJECT_MAPPER_TYPE = Type.getType(ObjectMapper.class);
  private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
  private static final Type A_OPTIONAL_TYPE = Type.getType(Optional.class);
  private static final Type A_OUTPUT_STREAM_TYPE = Type.getType(OutputStream.class);
  private static final Type A_PACK_STREAMING_TYPE = Type.getType(PackStreaming.class);
  private static final Type A_PACK_STREAMING_XML_TYPE = Type.getType(PackStreamingXml.class);
  private static final Type A_PRINT_WRITER_TYPE = Type.getType(PrintWriter.class);
  private static final Type A_RUNTIME_SUPPORT_TYPE = Type.getType(RuntimeSupport.class);
  private static final Type A_STREAM_TYPE = Type.getType(Stream.class);
  private static final Type A_STRING_TYPE = Type.getType(String.class);
  private static final Type A_TIME_FORMAT_TYPE = Type.getType(TimeFormat.class);
  private static final Type A_XML_OUTPUT_FACTORY_TYPE = Type.getType(XMLOutputFactory.class);
  private static final Type A_XML_STREAM_WRITER_TYPE = Type.getType(XMLStreamWriter.class);
  private static final LambdaType CSV_MAPPER =
      LambdaBuilder.function(A_OBJECT_ARRAY_TYPE, A_OBJECT_TYPE);
  private static final String JSON_GENERATOR_NAME = "JSON Generator";
  public static final Method METHOD_BOOLEAN__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {Type.BOOLEAN_TYPE});
  private static final Method METHOD_CSV_FORMAT__PRINT =
      new Method("print", A_CSV_PRINTER_TYPE, new Type[] {A_APPENDABLE_TYPE});
  private static final Method METHOD_CSV_PRINTER__CLOSE =
      new Method("close", Type.VOID_TYPE, new Type[] {Type.BOOLEAN_TYPE});
  private static final Method METHOD_CSV_PRINTER__PRINT_RECORD =
      new Method("printRecord", Type.VOID_TYPE, new Type[] {A_OBJECT_ARRAY_TYPE});
  private static final Method METHOD_CSV_PRINTER__PRINT_RECORDS =
      new Method("printRecords", Type.VOID_TYPE, new Type[] {A_STREAM_TYPE});
  public static final Method METHOD_DOUBLE__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {Type.DOUBLE_TYPE});
  private static final Method METHOD_EXTRACT_VISITOR__SUCCESS =
      new Method("success", A_OUTPUT_STREAM_TYPE, new Type[] {Type.getType(String.class)});
  private static final Method METHOD_IMYHAT__ACCEPT =
      new Method(
          "accept", Type.VOID_TYPE, new Type[] {Type.getType(ImyhatConsumer.class), A_OBJECT_TYPE});
  public static final Method METHOD_JSON_GENERATOR__CLOSE =
      new Method("close", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_JSON_GENERATOR__WRITE_END_ARRAY =
      new Method("writeEndArray", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_JSON_GENERATOR__WRITE_END_OBJECT =
      new Method("writeEndObject", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_JSON_GENERATOR__WRITE_FIELD_NAME =
      new Method("writeFieldName", Type.VOID_TYPE, new Type[] {A_STRING_TYPE});
  private static final Method METHOD_JSON_GENERATOR__WRITE_START_ARRAY =
      new Method("writeStartArray", Type.VOID_TYPE, new Type[] {});
  private static final Method METHOD_JSON_GENERATOR__WRITE_START_OBJECT =
      new Method("writeStartObject", Type.VOID_TYPE, new Type[] {});
  public static final Method METHOD_LONG__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {Type.LONG_TYPE});
  public static final Method METHOD_OBJECT_MAPPER__CREATE_GENERATOR =
      new Method("createGenerator", A_JSON_GENERATOR_TYPE, new Type[] {A_OUTPUT_STREAM_TYPE});
  public static final Method METHOD_OBJECT__TO_STRING =
      new Method("toString", A_STRING_TYPE, new Type[] {});
  private static final Method METHOD_OPTIONAL__MAP =
      new Method("map", A_OPTIONAL_TYPE, new Type[] {Type.getType(Function.class)});
  private static final Method METHOD_OPTIONAL__OR_ELSE =
      new Method("orElse", A_OBJECT_TYPE, new Type[] {A_OBJECT_TYPE});
  public static final Method METHOD_PACK_STREAMING_XML__CTOR =
      new Method(
          "<init>", Type.VOID_TYPE, new Type[] {A_XML_STREAM_WRITER_TYPE, A_TIME_FORMAT_TYPE});
  public static final Method METHOD_PACK_STREAMING__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_JSON_GENERATOR_TYPE, A_TIME_FORMAT_TYPE});
  private static final Method METHOD_PRINT_WRITER__CTOR =
      new Method("<init>", Type.VOID_TYPE, new Type[] {A_OUTPUT_STREAM_TYPE});
  private static final Method METHOD_STREAM__FOR_EACH =
      new Method("forEach", Type.VOID_TYPE, new Type[] {Type.getType(Consumer.class)});
  private static final Method METHOD_STREAM__MAP =
      new Method("map", A_STREAM_TYPE, new Type[] {Type.getType(Function.class)});
  private static final Method METHOD_XML_OUTPUT_FACTORY__CREATE_XML_STREAM_WRITER =
      new Method(
          "createXMLStreamWriter", A_XML_STREAM_WRITER_TYPE, new Type[] {A_OUTPUT_STREAM_TYPE});
  private static final Method METHOD_XML_OUTPUT_FACTORY__NEW_INSTANCE =
      new Method("newInstance", A_XML_OUTPUT_FACTORY_TYPE, new Type[0]);
  private static final Method METHOD_XML_STREAM_WRITER__CLOSE =
      new Method("close", Type.VOID_TYPE, new Type[0]);
  private static final Method METHOD_XML_STREAM_WRITER__WRITE_END_DOCUMENT =
      new Method("writeEndDocument", Type.VOID_TYPE, new Type[0]);
  private static final Method METHOD_XML_STREAM_WRITER__WRITE_END_ELEMENT =
      new Method("writeEndElement", Type.VOID_TYPE, new Type[0]);
  private static final Method METHOD_XML_STREAM_WRITER__WRITE_START_DOCUMENT =
      new Method("writeStartDocument", Type.VOID_TYPE, new Type[0]);
  private static final Method METHOD_XML_STREAM_WRITER__WRITE_START_ELEMENT =
      new Method("writeStartElement", Type.VOID_TYPE, new Type[] {A_STRING_TYPE});
  private static final LambdaType OBJECT_CONSUMER = LambdaBuilder.consumer(A_OBJECT_TYPE);
  private static final LambdaType TO_STRING_TYPE =
      LambdaBuilder.function(A_STRING_TYPE, A_OBJECT_TYPE);
  private static final String XML_STREAM_WRITER_NAME = "XML Stream Writer";

  public static Parser parse(
      OutputFormat outputFormat, Parser input, Consumer<ExtractorScriptNode> output) {
    return input
        .whitespace()
        .list(
            rules -> output.accept(new ExtractorScriptNode(rules, outputFormat)),
            ExtractRuleNode::parse)
        .whitespace();
  }

  private static void renderCommons(
      InputFormatDefinition inputFormatDefinition,
      List<String> columns,
      Renderer renderer,
      Stream<? extends ExtractionDataSource> inputs,
      LoadableValue output,
      String format) {
    renderer.methodGen().getStatic(A_CSV_FORMAT_TYPE, format, A_CSV_FORMAT_TYPE);
    renderer.methodGen().newInstance(A_PRINT_WRITER_TYPE);
    renderer.methodGen().dup();
    output.accept(renderer);
    renderer.methodGen().invokeConstructor(A_PRINT_WRITER_TYPE, METHOD_PRINT_WRITER__CTOR);
    renderer.methodGen().invokeVirtual(A_CSV_FORMAT_TYPE, METHOD_CSV_FORMAT__PRINT);

    // Headers
    renderer.methodGen().dup();
    renderer.methodGen().push(columns.size());
    renderer.methodGen().newArray(A_OBJECT_TYPE);
    for (var i = 0; i < columns.size(); i++) {
      renderer.methodGen().dup();
      renderer.methodGen().push(i);
      renderer.methodGen().push(columns.get(i));
      renderer.methodGen().arrayStore(A_OBJECT_TYPE);
    }
    renderer.methodGen().invokeVirtual(A_CSV_PRINTER_TYPE, METHOD_CSV_PRINTER__PRINT_RECORD);

    inputs.forEach(
        input -> {
          final var captures = input.captures();
          final var builder =
              new LambdaBuilder(
                  renderer.root(),
                  "Delimited Converter for " + input.name(),
                  CSV_MAPPER,
                  null,
                  renderer
                      .allValues()
                      .filter(v -> captures.contains(v.name()))
                      .toArray(LoadableValue[]::new));

          renderer.methodGen().dup();
          input.renderStream(renderer);
          builder.push(renderer);
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__MAP);
          renderer.methodGen().invokeVirtual(A_CSV_PRINTER_TYPE, METHOD_CSV_PRINTER__PRINT_RECORDS);

          final var lambdaRenderer =
              builder.renderer(inputFormatDefinition.type(), renderer.signerEmitter());
          lambdaRenderer.methodGen().visitCode();
          final var array = lambdaRenderer.methodGen().newLocal(A_OBJECT_ARRAY_TYPE);
          lambdaRenderer.methodGen().push(columns.size());
          lambdaRenderer.methodGen().newArray(A_OBJECT_TYPE);
          lambdaRenderer.methodGen().storeLocal(array);
          input.renderColumns(
              new OutputCollector() {
                private int index;

                @Override
                public void addValue(String name, Consumer<Renderer> value, Imyhat type) {
                  lambdaRenderer.methodGen().loadLocal(array);
                  lambdaRenderer.methodGen().push(index++);
                  value.accept(lambdaRenderer);
                  if (type.isSame(Imyhat.BOOLEAN)) {
                    lambdaRenderer
                        .methodGen()
                        .invokeStatic(A_BOOLEAN_TYPE, METHOD_BOOLEAN__TO_STRING);

                  } else if (type.isSame(Imyhat.FLOAT)) {
                    lambdaRenderer
                        .methodGen()
                        .invokeStatic(A_DOUBLE_TYPE, METHOD_DOUBLE__TO_STRING);

                  } else if (type.isSame(Imyhat.INTEGER)) {
                    lambdaRenderer.methodGen().invokeStatic(A_LONG_TYPE, METHOD_LONG__TO_STRING);
                  } else if (type instanceof OptionalImyhat) {
                    LambdaBuilder.pushVirtual(lambdaRenderer, "toString", TO_STRING_TYPE);
                    lambdaRenderer.methodGen().invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__MAP);
                    lambdaRenderer.methodGen().push("");
                    lambdaRenderer
                        .methodGen()
                        .invokeVirtual(A_OPTIONAL_TYPE, METHOD_OPTIONAL__OR_ELSE);
                  } else {
                    lambdaRenderer
                        .methodGen()
                        .invokeVirtual(A_OBJECT_TYPE, METHOD_OBJECT__TO_STRING);
                  }
                  lambdaRenderer.methodGen().arrayStore(A_OBJECT_TYPE);
                }
              });
          lambdaRenderer.methodGen().loadLocal(array);
          lambdaRenderer.methodGen().returnValue();
          lambdaRenderer.methodGen().endMethod();
        });

    renderer.methodGen().push(true);
    renderer.methodGen().invokeVirtual(A_CSV_PRINTER_TYPE, METHOD_CSV_PRINTER__CLOSE);
  }

  private static void renderJson(
      InputFormatDefinition inputFormatDefinition,
      Renderer renderer,
      Stream<? extends ExtractionDataSource> inputs,
      LoadableValue outputStream,
      TimeFormat format) {
    renderer.methodGen().getStatic(A_RUNTIME_SUPPORT_TYPE, "MAPPER", A_OBJECT_MAPPER_TYPE);
    outputStream.accept(renderer);
    renderer
        .methodGen()
        .invokeVirtual(A_OBJECT_MAPPER_TYPE, METHOD_OBJECT_MAPPER__CREATE_GENERATOR);
    final var generator = renderer.methodGen().newLocal(A_JSON_GENERATOR_TYPE);
    renderer.methodGen().dup();
    renderer
        .methodGen()
        .invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_START_ARRAY);
    renderer.methodGen().storeLocal(generator);
    inputs.forEach(
        input -> {
          input.renderStream(renderer);
          final var captures = input.captures();
          final var builder =
              new LambdaBuilder(
                  renderer.root(),
                  "JSON Consumer for " + input.name(),
                  OBJECT_CONSUMER,
                  null,
                  Stream.concat(
                          Stream.of(
                              new LoadableValue() {
                                @Override
                                public void accept(Renderer renderer) {
                                  renderer.methodGen().loadLocal(generator);
                                }

                                @Override
                                public String name() {
                                  return JSON_GENERATOR_NAME;
                                }

                                @Override
                                public Type type() {
                                  return A_JSON_GENERATOR_TYPE;
                                }
                              }),
                          renderer.allValues().filter(v -> captures.contains(v.name())))
                      .toArray(LoadableValue[]::new));
          builder.push(renderer);
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FOR_EACH);

          final var lambdaRenderer =
              builder.renderer(inputFormatDefinition.type(), renderer.signerEmitter());
          lambdaRenderer.methodGen().visitCode();
          lambdaRenderer.emitNamed(JSON_GENERATOR_NAME);
          lambdaRenderer
              .methodGen()
              .invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_START_OBJECT);

          input.renderColumns(
              (name, value, type) -> {
                lambdaRenderer.emitNamed(JSON_GENERATOR_NAME);
                lambdaRenderer.methodGen().push(name);
                lambdaRenderer
                    .methodGen()
                    .invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_FIELD_NAME);

                lambdaRenderer.loadImyhat(type.descriptor());

                lambdaRenderer.methodGen().newInstance(A_PACK_STREAMING_TYPE);
                lambdaRenderer.methodGen().dup();
                lambdaRenderer.emitNamed(JSON_GENERATOR_NAME);
                lambdaRenderer
                    .methodGen()
                    .getStatic(A_TIME_FORMAT_TYPE, format.name(), A_TIME_FORMAT_TYPE);
                lambdaRenderer
                    .methodGen()
                    .invokeConstructor(A_PACK_STREAMING_TYPE, METHOD_PACK_STREAMING__CTOR);

                value.accept(lambdaRenderer);
                lambdaRenderer.methodGen().valueOf(type.apply(TO_ASM));

                lambdaRenderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__ACCEPT);
              });
          lambdaRenderer.emitNamed(JSON_GENERATOR_NAME);
          lambdaRenderer
              .methodGen()
              .invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_END_OBJECT);
          lambdaRenderer.methodGen().visitInsn(Opcodes.RETURN);
          lambdaRenderer.methodGen().endMethod();
        });
    renderer.methodGen().loadLocal(generator);
    renderer.methodGen().dup();
    renderer
        .methodGen()
        .invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__WRITE_END_ARRAY);
    renderer.methodGen().invokeVirtual(A_JSON_GENERATOR_TYPE, METHOD_JSON_GENERATOR__CLOSE);
  }

  private static void renderXml(
      InputFormatDefinition inputFormatDefinition,
      List<String> columns,
      Renderer renderer,
      Stream<? extends ExtractionDataSource> inputs,
      LoadableValue output,
      TimeFormat format) {
    renderer
        .methodGen()
        .invokeStatic(A_XML_OUTPUT_FACTORY_TYPE, METHOD_XML_OUTPUT_FACTORY__NEW_INSTANCE);
    output.accept(renderer);
    renderer
        .methodGen()
        .invokeVirtual(
            A_XML_OUTPUT_FACTORY_TYPE, METHOD_XML_OUTPUT_FACTORY__CREATE_XML_STREAM_WRITER);
    renderer.methodGen().dup();
    renderer
        .methodGen()
        .invokeInterface(A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_START_DOCUMENT);
    renderer.methodGen().dup();
    renderer.methodGen().push("records");
    renderer
        .methodGen()
        .invokeInterface(A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_START_ELEMENT);
    final var local = renderer.methodGen().newLocal(A_XML_STREAM_WRITER_TYPE);
    renderer.methodGen().storeLocal(local);
    inputs.forEach(
        input -> {
          input.renderStream(renderer);
          final var captures = input.captures();
          final var builder =
              new LambdaBuilder(
                  renderer.root(),
                  "XML Consumer for " + input.name(),
                  OBJECT_CONSUMER,
                  null,
                  Stream.concat(
                          Stream.of(
                              new LoadableValue() {
                                @Override
                                public void accept(Renderer renderer) {
                                  renderer.methodGen().loadLocal(local);
                                }

                                @Override
                                public String name() {
                                  return XML_STREAM_WRITER_NAME;
                                }

                                @Override
                                public Type type() {
                                  return A_XML_STREAM_WRITER_TYPE;
                                }
                              }),
                          renderer.allValues().filter(v -> captures.contains(v.name())))
                      .toArray(LoadableValue[]::new));
          builder.push(renderer);
          renderer.methodGen().invokeInterface(A_STREAM_TYPE, METHOD_STREAM__FOR_EACH);

          final var lambdaRenderer =
              builder.renderer(inputFormatDefinition.type(), renderer.signerEmitter());
          lambdaRenderer.methodGen().visitCode();
          lambdaRenderer.emitNamed(XML_STREAM_WRITER_NAME);
          lambdaRenderer.methodGen().push("record");
          lambdaRenderer
              .methodGen()
              .invokeInterface(
                  A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_START_ELEMENT);

          input.renderColumns(
              (name, value, type) -> {
                lambdaRenderer.emitNamed(XML_STREAM_WRITER_NAME);
                lambdaRenderer.methodGen().push(name);
                lambdaRenderer
                    .methodGen()
                    .invokeInterface(
                        A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_START_ELEMENT);

                lambdaRenderer.loadImyhat(type.descriptor());

                lambdaRenderer.methodGen().newInstance(A_PACK_STREAMING_XML_TYPE);
                lambdaRenderer.methodGen().dup();
                lambdaRenderer.emitNamed(XML_STREAM_WRITER_NAME);
                lambdaRenderer
                    .methodGen()
                    .getStatic(A_TIME_FORMAT_TYPE, format.name(), A_TIME_FORMAT_TYPE);
                lambdaRenderer
                    .methodGen()
                    .invokeConstructor(A_PACK_STREAMING_XML_TYPE, METHOD_PACK_STREAMING_XML__CTOR);

                value.accept(lambdaRenderer);
                lambdaRenderer.methodGen().valueOf(type.apply(TO_ASM));

                lambdaRenderer.methodGen().invokeVirtual(A_IMYHAT_TYPE, METHOD_IMYHAT__ACCEPT);
                lambdaRenderer.emitNamed(XML_STREAM_WRITER_NAME);
                lambdaRenderer
                    .methodGen()
                    .invokeInterface(
                        A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_END_ELEMENT);
              });
          lambdaRenderer.emitNamed(XML_STREAM_WRITER_NAME);
          lambdaRenderer
              .methodGen()
              .invokeInterface(
                  A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_END_ELEMENT);
          lambdaRenderer.methodGen().visitInsn(Opcodes.RETURN);
          lambdaRenderer.methodGen().endMethod();
        });
    renderer.methodGen().loadLocal(local);
    renderer.methodGen().dup();
    renderer.methodGen().dup();
    renderer
        .methodGen()
        .invokeInterface(A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_END_ELEMENT);
    renderer
        .methodGen()
        .invokeInterface(A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__WRITE_END_DOCUMENT);
    renderer.methodGen().invokeInterface(A_XML_STREAM_WRITER_TYPE, METHOD_XML_STREAM_WRITER__CLOSE);
  }

  private List<String> columns;
  private InputFormatDefinition inputFormatDefinition;
  private final OutputFormat outputFormat;
  private final List<ExtractRuleNode> rules;

  public ExtractorScriptNode(List<ExtractRuleNode> rules, OutputFormat outputFormat) {
    this.rules = rules;
    this.outputFormat = outputFormat;
  }

  public void render(ExtractBuilder builder) {
    final var renderer = builder.renderer();
    renderer.methodGen().visitCode();
    renderConsumer(
        inputFormatDefinition,
        columns,
        renderer,
        rules.stream(),
        new LoadableValue() {
          @Override
          public void accept(Renderer renderer) {
            renderer.emitNamed(ExtractBuilder.OUTPUT_VISITOR.name());
            renderer.methodGen().push(outputFormat.mimeType());
            renderer
                .methodGen()
                .invokeInterface(
                    ExtractBuilder.OUTPUT_VISITOR.type(), METHOD_EXTRACT_VISITOR__SUCCESS);
          }

          @Override
          public String name() {
            return "Start Output";
          }

          @Override
          public Type type() {
            return A_OUTPUT_STREAM_TYPE;
          }
        });
    renderer.methodGen().endMethod();
  }

  private void renderConsumer(
      InputFormatDefinition inputFormatDefinition,
      List<String> columns,
      Renderer renderer,
      Stream<ExtractRuleNode> stream,
      LoadableValue output) {
    switch (outputFormat) {
      case CSV_EXCEL:
        renderCommons(inputFormatDefinition, columns, renderer, stream, output, "EXCEL");
        return;
      case CSV_MONGO:
        renderCommons(inputFormatDefinition, columns, renderer, stream, output, "MONGODB_CSV");
        return;
      case CSV_MYSQL:
        renderCommons(inputFormatDefinition, columns, renderer, stream, output, "MYSQL");
        return;

      case CSV_POSTGRESQL:
        renderCommons(inputFormatDefinition, columns, renderer, stream, output, "POSTGRESQL_CSV");
        return;
      case CSV_RFC4180:
        renderCommons(inputFormatDefinition, columns, renderer, stream, output, "RFC4180");
        return;
      case JSON:
        renderJson(inputFormatDefinition, renderer, stream, output, TimeFormat.ISO8660_STRING);
        return;
      case JSON_SECS:
        renderJson(inputFormatDefinition, renderer, stream, output, TimeFormat.SECONDS_NUMERIC);
        return;
      case JSON_MILLIS:
        renderJson(inputFormatDefinition, renderer, stream, output, TimeFormat.MILLIS_NUMERIC);
        return;
      case TSV:
        renderCommons(inputFormatDefinition, columns, renderer, stream, output, "TDF");
        return;
      case TSV_MONGO:
        renderCommons(inputFormatDefinition, columns, renderer, stream, output, "MONGODB_TSV");
        return;
      case XML:
        renderXml(
            inputFormatDefinition, columns, renderer, stream, output, TimeFormat.ISO8660_STRING);
        return;
      case XML_SECS:
        renderXml(
            inputFormatDefinition, columns, renderer, stream, output, TimeFormat.SECONDS_NUMERIC);
        return;
      case XML_MILLIS:
        renderXml(
            inputFormatDefinition, columns, renderer, stream, output, TimeFormat.MILLIS_NUMERIC);
        return;
    }
  }

  public boolean validate(
      InputFormatDefinition inputFormatDefinition,
      Function<String, FunctionDefinition> definedFunctions,
      Consumer<String> errorHandler,
      Supplier<Stream<ConstantDefinition>> constants,
      Map<String, List<ExtractionNode>> preparedColumns) {
    this.inputFormatDefinition = inputFormatDefinition;
    if (rules.stream()
            .filter(
                r ->
                    r.validate(
                        inputFormatDefinition,
                        outputFormat,
                        definedFunctions,
                        errorHandler,
                        constants,
                        preparedColumns))
            .count()
        == rules.size()) {
      final var columns =
          rules.stream()
              .collect(Collectors.groupingBy(ExtractRuleNode::columns, Collectors.toList()));
      if (columns.isEmpty()) {
        errorHandler.accept("Output has no columns");
        return false;

      } else if (columns.size() > 1) {
        final var buffer = new StringBuilder();
        buffer.append("Columns do not match between all extraction rules:\n");
        for (final var entry : columns.entrySet()) {
          buffer.append("Columns: ");
          var first = true;
          for (final var name : entry.getKey()) {
            if (first) {
              first = false;
            } else {
              buffer.append(", ");
            }
            buffer.append(name);
          }
          buffer.append("\nUsed by:");
          for (final var rule : entry.getValue()) {
            buffer
                .append("\tRule at ")
                .append(rule.line())
                .append(":")
                .append(rule.column())
                .append("\n");
          }
          buffer.append("\n");
        }

        errorHandler.accept(buffer.toString());
        return false;
      } else {
        this.columns = columns.keySet().stream().findFirst().orElseThrow();
        return true;
      }

    } else {
      return false;
    }
  }
}
