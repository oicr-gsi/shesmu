package ca.on.oicr.gsi.shesmu.server;

import ca.on.oicr.gsi.Pair;
import ca.on.oicr.gsi.shesmu.compiler.Target;
import ca.on.oicr.gsi.shesmu.compiler.definitions.InputFormatDefinition;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveClauseRow;
import ca.on.oicr.gsi.shesmu.compiler.description.OliveTable;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation;
import ca.on.oicr.gsi.shesmu.compiler.description.VariableInformation.Behaviour;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation;
import ca.on.oicr.gsi.shesmu.plugin.SourceLocation.SourceLocationLinker;
import ca.on.oicr.gsi.shesmu.plugin.types.Imyhat;
import java.awt.Canvas;
import java.awt.Font;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

public class MetroDiagram {
  public interface OliveFlowReader {
    /**
     * Read the number of rows that have crossed a particular olive
     *
     * @param filename the file name containing the olive
     * @param line the source line of the clause being measured
     * @param column the source column of the clause being measured
     * @param hash the source hash
     * @return the number of rows or null if unknown
     */
    Long read(
        String filename,
        int line,
        int column,
        String hash,
        String oliveFilename,
        int oliveLine,
        int oliveColumn,
        String oliveHash);
  }

  private static final class DeathChecker implements Predicate<OliveClauseRow> {
    private boolean done;

    public Stream<VariableInformation> liveVariables(OliveTable olive) {
      final var clauseInput =
          olive
              .clauses()
              .filter(this)
              .flatMap(OliveClauseRow::variables)
              .collect(Collectors.toList());
      if (done) {
        return clauseInput.stream();
      }
      return Stream.concat(clauseInput.stream(), olive.variables());
    }

    @Override
    public boolean test(OliveClauseRow clause) {
      if (done) {
        return false;
      }
      done = clause.deadly();
      return true;
    }
  }

  private interface DelayedXml {
    void write(XMLStreamWriter writer) throws XMLStreamException;
  }

  private static final double AVG_SMALL_CHAR_WIDTH = 1.871;

  private static final String CLAUSE_HEADER = "Clause (Line:Column)";

  private static final String[] COLOURS =
      new String[] {
        "#d09c2e", "#5b7fee", "#bacd4c", "#503290", "#8bc151", "#903691", "#46ca79", "#db64c3",
        "#63bb5b", "#af74db", "#9fa627", "#5a5dc0", "#72891f", "#578ae2", "#c96724", "#38b3eb",
        "#c34f32", "#34d3ca", "#be2e68", "#4ec88c", "#be438d", "#53d1a8", "#d54a4a", "#319466",
        "#d486d8", "#417c25", "#4b2f75", "#c3b857", "#3b5ba0", "#e09c4e", "#6d95db", "#9f741f",
        "#826bb9", "#78bb73", "#802964", "#a8bd69", "#b995e2", "#346e2e", "#d97eb8", "#6e6f24",
        "#e36f96", "#c29b59", "#862644", "#da8b57", "#d2506f", "#8d4e19", "#d34b5b", "#832520",
        "#d06c72", "#ce7058"
      };

  private static final long SVG_CONTROL_DISTANCE = 15;
  private static final long SVG_COUNT_START = 90;
  private static final long SVG_METRO_WIDTH = 25;
  private static final String SVG_NS_URI = "http://www.w3.org/2000/svg";
  private static final long SVG_RADIUS = 3;
  private static final long SVG_ROW_HEIGHT = 64;
  private static final long SVG_SMALL_TEXT = 10;
  private static final long SVG_TEXT_BASELINE = 30;
  private static final long SVG_TITLE_START = 100;
  private static final String XLINK_NS_URI = "http://www.w3.org/1999/xlink";

  public static void draw(
      XMLStreamWriter writer,
      SourceLocationLinker linker,
      String filename,
      String hash,
      OliveTable olive,
      Long inputCount,
      InputFormatDefinition format,
      OliveFlowReader reader)
      throws XMLStreamException {
    final var id = String.format("%s:%d:%d[%s]", filename, olive.column(), olive.line(), hash);
    final var metroStart =
        120
            + Stream.concat(
                    olive.clauses().map(OliveClauseRow::syntax),
                    Stream.of(olive.syntax(), CLAUSE_HEADER))
                .mapToLong(
                    new Canvas().getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 18))
                        ::stringWidth)
                .max()
                .orElse(100);
    final var liveVariableInfo =
        new DeathChecker().liveVariables(olive).collect(Collectors.toList());
    final var topPadding =
        (long)
            Math.max(
                SVG_ROW_HEIGHT,
                liveVariableInfo.stream()
                        .flatMap(VariableInformation::inputs)
                        .mapToInt(String::length)
                        .max()
                        .orElse(0)
                    * AVG_SMALL_CHAR_WIDTH
                    * Math.sin(Math.toRadians(45)));
    final var height =
        SVG_ROW_HEIGHT * (olive.clauses().count() + 2)
            + topPadding; // Padding (includes title) + Input + Clauses + Output
    final var width =
        metroStart
            + SVG_METRO_WIDTH
                * (Stream.concat(
                            olive.clauses().flatMap(OliveClauseRow::variables), olive.variables())
                        .flatMap(
                            variable ->
                                Stream.concat(Stream.of(variable.name()), variable.inputs()))
                        .distinct()
                        .count()
                    + 1)
            + (long)
                (olive
                        .clauses()
                        .flatMap(OliveClauseRow::variables)
                        .map(VariableInformation::name)
                        .mapToInt(String::length)
                        .max()
                        .orElse(0)
                    * AVG_SMALL_CHAR_WIDTH
                    * Math.sin(Math.toRadians(45)))
            + 4 * SVG_RADIUS;

    writer.writeStartElement("svg");
    writer.writeDefaultNamespace(SVG_NS_URI);
    writer.writeNamespace("xlink", XLINK_NS_URI);
    writer.writeAttribute("width", Long.toString(width));
    writer.writeAttribute("height", Long.toString(height));
    writer.writeAttribute("version", "1.1");
    writer.writeAttribute("viewBox", String.format("0 0 %d %d", width, height));

    writer.writeStartElement("defs");
    writer.writeStartElement("filter");
    writer.writeAttribute("id", "blur-" + id);
    writer.writeAttribute("x", "0");
    writer.writeAttribute("y", "0");
    writer.writeStartElement("feFlood");
    writer.writeAttribute("flood-color", "white");
    writer.writeEndElement();
    writer.writeStartElement("feComposite");
    writer.writeAttribute("in2", "SourceGraphic");
    writer.writeAttribute("operator", "in");
    writer.writeEndElement();
    writer.writeStartElement("feGaussianBlur");
    writer.writeAttribute("stdDeviation", "2");
    writer.writeEndElement();
    writer.writeStartElement("feComponentTransfer");
    writer.writeStartElement("feFuncA");
    writer.writeAttribute("type", "gamma");
    writer.writeAttribute("exponent", ".5");
    writer.writeAttribute("amplitude", "2");
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeStartElement("feComposite");
    writer.writeAttribute("in", "SourceGraphic");
    writer.writeEndElement();
    writer.writeStartElement("feComposite");
    writer.writeAttribute("in", "SourceGraphic");
    writer.writeEndElement();
    writer.writeEndElement();
    writer.writeEndElement();

    if (inputCount != null) {
      writer.writeStartElement("text");
      writer.writeAttribute("text-anchor", "end");
      writer.writeAttribute("style", "font-weight:bold");
      writer.writeAttribute("x", Long.toString(SVG_COUNT_START));
      writer.writeAttribute("y", Long.toString(topPadding - SVG_ROW_HEIGHT + SVG_TEXT_BASELINE));
      writer.writeCharacters("Records");
      writer.writeEndElement();
    }
    writer.writeStartElement("text");
    writer.writeAttribute("style", "font-weight:bold");
    writer.writeAttribute("x", Long.toString(SVG_TITLE_START));
    writer.writeAttribute("y", Long.toString(topPadding - SVG_ROW_HEIGHT + SVG_TEXT_BASELINE));
    writer.writeCharacters(CLAUSE_HEADER);
    writer.writeEndElement();

    final var idGen = new AtomicInteger();
    final var row = new AtomicInteger(1);
    final List<DelayedXml> textLayerBuffer = new ArrayList<>();

    final Map<String, MetroDiagram> initialVariables = new HashMap<>();
    liveVariableInfo.stream()
        .flatMap(VariableInformation::inputs)
        .distinct()
        .sorted()
        .forEach(
            name -> {
              final var colour = idGen.getAndIncrement();
              try {
                initialVariables.put(
                    name,
                    new MetroDiagram(
                        textLayerBuffer,
                        writer,
                        id,
                        name,
                        format
                            .baseStreamVariables()
                            .filter(var -> var.name().equals(name))
                            .map(Target::type)
                            .findFirst()
                            .orElse(Imyhat.BAD),
                        "",
                        colour,
                        0,
                        colour,
                        metroStart,
                        topPadding,
                        true));
              } catch (XMLStreamException e) {
                throw new RuntimeException(e);
              }
            });

    final var source = new SourceLocation(filename, olive.line(), olive.column(), hash);
    writeClause(writer, linker, topPadding, 0, "Input", inputCount, source);

    final var terminalVariables =
        olive
            .clauses()
            .reduce(
                initialVariables,
                (variables, clause) -> {
                  final var currentRow = row.getAndIncrement();
                  try {
                    writeClause(
                        writer,
                        linker,
                        topPadding,
                        currentRow,
                        clause.syntax(),
                        clause.measuredFlow() && inputCount != null
                            ? reader.read(
                                filename,
                                clause.line(),
                                clause.column(),
                                hash,
                                filename,
                                olive.line(),
                                olive.column(),
                                hash)
                            : null,
                        new SourceLocation(filename, clause.line(), clause.column(), hash));
                  } catch (XMLStreamException e) {
                    throw new RuntimeException(e);
                  }

                  return drawVariables(
                      textLayerBuffer,
                      writer,
                      id,
                      metroStart,
                      topPadding,
                      idGen,
                      variables,
                      clause::variables,
                      currentRow);
                },
                (a, b) -> {
                  throw new UnsupportedOperationException();
                });
    writeClause(writer, linker, topPadding, row.get(), olive.syntax(), null, source);
    drawVariables(
        textLayerBuffer,
        writer,
        id,
        metroStart,
        topPadding,
        idGen,
        terminalVariables,
        olive::variables,
        row.get());
    for (var textElement : textLayerBuffer) {
      textElement.write(writer);
    }
    writer.writeEndElement();
  }

  private static Map<String, MetroDiagram> drawVariables(
      List<DelayedXml> textLayer,
      XMLStreamWriter connectorLayer,
      String id,
      long metroStart,
      long topPadding,
      AtomicInteger idGen,
      Map<String, MetroDiagram> variables,
      Supplier<Stream<VariableInformation>> information,
      int row) {
    final var outputVariableColumns =
        Stream.concat(
                information
                    .get()
                    .map(
                        vi ->
                            vi.name()
                                + (vi.behaviour() == Behaviour.EPHEMERAL
                                    ? " "
                                    : "")), // Ephemeral variables appear with stupid names so they
                // appear in the flow but don't get passed on to the
                // next layer and if there is a variable of the same
                // name, it appears twice
                variables.keySet().stream())
            .sorted()
            .distinct()
            .map(Pair.number())
            .collect(Collectors.toMap(Pair::second, Pair::first));

    final Map<String, MetroDiagram> outputVariables = new HashMap<>();

    information
        .get()
        .forEach(
            variable -> {
              try {
                final var currentPoint =
                    new Pair<>(outputVariableColumns.get(variable.name()), row);
                switch (variable.behaviour()) {
                  case DEFINITION:
                  case DEFINITION_BY:
                  case EPHEMERAL:
                    // If we have a defined variable, then it always needs to be drawn
                    final var mangledName =
                        variable.name() + (variable.behaviour() == Behaviour.EPHEMERAL ? " " : "");
                    final var newVariable =
                        new MetroDiagram(
                            textLayer,
                            connectorLayer,
                            id,
                            variable.name(),
                            variable.type(),
                            from(variable, variables),
                            idGen.getAndIncrement(),
                            row,
                            outputVariableColumns.get(mangledName),
                            metroStart,
                            topPadding,
                            variable.behaviour() == Behaviour.DEFINITION);
                    variable
                        .inputs()
                        .forEach(
                            input -> {
                              try {
                                variables.get(input).drawConnector(newVariable.start());
                              } catch (XMLStreamException e) {
                                throw new RuntimeException(e);
                              }
                            });
                    outputVariables.put(mangledName, newVariable);
                    break;
                  case OBSERVER:
                    variable
                        .inputs()
                        .forEach(
                            name -> {
                              final var observedVariable = variables.get(name);
                              try {
                                observedVariable.drawDot(currentPoint, "used");
                              } catch (XMLStreamException e) {
                                throw new RuntimeException(e);
                              }
                            });
                    break;
                  case PASSTHROUGH:
                    final var passthroughVariable = variables.get(variable.name());
                    passthroughVariable.drawSquare(currentPoint);
                    break;
                  default:
                    break;
                }
              } catch (XMLStreamException e) {
                throw new RuntimeException(e);
              }
            });

    for (final var entry : outputVariableColumns.entrySet()) {
      if (outputVariables.containsKey(entry.getKey())) {
        continue;
      }
      final var variable = variables.get(entry.getKey());
      variable.append(new Pair<>(entry.getValue(), row));
      outputVariables.put(entry.getKey(), variable);
    }

    return outputVariables;
  }

  private static String from(VariableInformation variable, Map<String, MetroDiagram> variables) {
    if (variable.inputs().count() == 0) {
      return " de novo";
    }
    return variable
        .inputs()
        .map(variables::get)
        .map(v -> v.title)
        .collect(Collectors.joining(", ", " from ", ""));
  }

  private static void writeClause(
      XMLStreamWriter writer,
      SourceLocationLinker linker,
      long topPadding,
      int row,
      String title,
      Long count,
      SourceLocation location)
      throws XMLStreamException {
    if (count != null) {
      writer.writeStartElement("text");
      writer.writeAttribute("text-anchor", "end");
      writer.writeAttribute("x", Long.toString(SVG_COUNT_START));
      writer.writeAttribute(
          "y",
          Long.toString(
              topPadding + SVG_ROW_HEIGHT * row + SVG_TEXT_BASELINE + SVG_ROW_HEIGHT / 2));
      writer.writeCharacters(Long.toString(count));
      writer.writeEndElement();
    }
    final var url = location.url(linker);
    if (url.isPresent()) {
      writer.writeStartElement("a");
      writer.writeAttribute("xlink", XLINK_NS_URI, "href", url.get());
      writer.writeAttribute("xlink", XLINK_NS_URI, "title", "View Source");
      writer.writeAttribute("xlink", XLINK_NS_URI, "show", "new");
    }
    writer.writeStartElement("text");
    writer.writeAttribute("x", Long.toString(SVG_TITLE_START));
    writer.writeAttribute(
        "y", Long.toString(topPadding + SVG_ROW_HEIGHT * row + SVG_TEXT_BASELINE));
    writer.writeCharacters(
        String.format(
            "%s (%d:%d)%s",
            title, location.line(), location.column(), url.isPresent() ? "🔗" : ""));
    writer.writeEndElement();
    if (url.isPresent()) {
      writer.writeEndElement();
    }
  }

  private final String colour;

  private final XMLStreamWriter connectorLayer;
  private final long topPadding;

  private final long metroStart;

  private final Queue<Pair<Integer, Integer>> segments = new LinkedList<>();
  private final Pair<Integer, Integer> start;
  private final List<DelayedXml> textLayer;
  private final String title;

  private MetroDiagram(
      List<DelayedXml> textLayer,
      XMLStreamWriter connectorLayer,
      String id,
      String name,
      Imyhat type,
      String from,
      int colour,
      int row,
      int column,
      long metroStart,
      long topPadding,
      boolean dot)
      throws XMLStreamException {
    this.textLayer = textLayer;
    this.connectorLayer = connectorLayer;
    this.topPadding = topPadding;
    title = name + " (" + type.name() + ")";
    this.metroStart = metroStart;
    this.colour = COLOURS[colour % COLOURS.length];
    start = new Pair<>(column, row);
    segments.offer(start);
    if (dot) {
      drawDot(start, "defined" + from);
    } else {
      drawSquare(start);
    }
    final var x = metroStart + column * SVG_METRO_WIDTH + SVG_METRO_WIDTH / 2;
    final var y = topPadding + SVG_ROW_HEIGHT * row + SVG_ROW_HEIGHT / 2;
    textLayer.add(
        textWriter -> {
          textWriter.writeStartElement("text");
          textWriter.writeAttribute("transform", String.format("rotate(-45, %1$d, %2$d)", x, y));
          textWriter.writeAttribute("x", Long.toString(x + SVG_RADIUS * 2));
          textWriter.writeAttribute("y", Long.toString(y + SVG_RADIUS * 2));
          textWriter.writeAttribute("fill", "#000");
          textWriter.writeAttribute("filter", "url(#blur-" + id + ")");
          textWriter.writeAttribute("font-size", Long.toString(SVG_SMALL_TEXT));
          textWriter.writeStartElement("title");
          textWriter.writeCharacters(type.name());
          textWriter.writeEndElement();
          textWriter.writeCharacters(name);
          textWriter.writeEndElement();
        });
  }

  public void append(Pair<Integer, Integer> point) {
    segments.add(point);
  }

  private void drawConnector(Pair<Integer, Integer> output) throws XMLStreamException {
    if (segments.size() > 0 && segments.peek().equals(output)) {
      return;
    }
    connectorLayer.writeStartElement("path");
    connectorLayer.writeAttribute("stroke", colour);
    connectorLayer.writeAttribute("fill", "none");
    var path = new StringBuilder();
    path.append("M ")
        .append(xCoordinate(segments.peek()))
        .append(" ")
        .append(yCoordinate(segments.peek()));
    while (segments.size() > 1) {
      drawSegment(path, segments.poll(), segments.peek());
    }
    drawSegment(path, segments.peek(), output);
    connectorLayer.writeAttribute("d", path.toString());
    connectorLayer.writeStartElement("title");
    connectorLayer.writeCharacters(title);
    connectorLayer.writeEndElement();
    connectorLayer.writeEndElement();
  }

  private void drawDot(Pair<Integer, Integer> point, String verb) throws XMLStreamException {
    drawConnector(point);
    textLayer.add(
        textWriter -> {
          textWriter.writeStartElement("circle");
          textWriter.writeAttribute("r", Long.toString(SVG_RADIUS));
          textWriter.writeAttribute(
              "cx",
              Long.toString(metroStart + point.first() * SVG_METRO_WIDTH + SVG_METRO_WIDTH / 2));
          textWriter.writeAttribute(
              "cy",
              Long.toString(topPadding + SVG_ROW_HEIGHT * point.second() + SVG_ROW_HEIGHT / 2));
          textWriter.writeAttribute("fill", colour);
          textWriter.writeStartElement("title");
          textWriter.writeCharacters(title);
          textWriter.writeCharacters(" ");
          textWriter.writeCharacters(verb);
          textWriter.writeEndElement();
          textWriter.writeEndElement();
        });
  }

  private void drawSegment(
      StringBuilder path, Pair<Integer, Integer> input, Pair<Integer, Integer> output) {
    final var inputX = xCoordinate(input);
    final var outputX = xCoordinate(output);
    final var inputY = yCoordinate(input);
    final var outputY = yCoordinate(output);
    if (inputX == outputX) {
      path.append("L ").append(outputX).append(" ").append(outputY);
    } else {
      path.append("C ")
          .append(inputX)
          .append(" ")
          .append(inputY + SVG_CONTROL_DISTANCE) // control point
          .append(" ")
          .append(outputX)
          .append(" ")
          .append(outputY - SVG_CONTROL_DISTANCE) // control point
          .append(" ")
          .append(outputX)
          .append(" ")
          .append(outputY); // final point
    }
  }

  private void drawSquare(Pair<Integer, Integer> point) throws XMLStreamException {
    drawConnector(point);
    textLayer.add(
        textWriter -> {
          textWriter.writeStartElement("rect");
          textWriter.writeAttribute("width", Long.toString(SVG_RADIUS * 4));
          textWriter.writeAttribute("height", Long.toString(SVG_RADIUS * 4));
          textWriter.writeAttribute(
              "x",
              Long.toString(
                  metroStart
                      + point.first() * SVG_METRO_WIDTH
                      + SVG_METRO_WIDTH / 2
                      - SVG_RADIUS * 2));
          textWriter.writeAttribute(
              "y",
              Long.toString(
                  topPadding
                      + SVG_ROW_HEIGHT * point.second()
                      + SVG_ROW_HEIGHT / 2
                      - SVG_RADIUS * 2));
          textWriter.writeAttribute("fill", colour);
          textWriter.writeStartElement("title");
          textWriter.writeCharacters("Group By ");
          textWriter.writeCharacters(title);
          textWriter.writeEndElement();
          textWriter.writeEndElement();
        });
  }

  private Pair<Integer, Integer> start() {
    return start;
  }

  private long xCoordinate(Pair<Integer, Integer> point) {
    return metroStart + point.first() * SVG_METRO_WIDTH + SVG_METRO_WIDTH / 2;
  }

  private long yCoordinate(Pair<Integer, Integer> point) {
    return topPadding + point.second() * SVG_ROW_HEIGHT + SVG_ROW_HEIGHT / 2;
  }
}
