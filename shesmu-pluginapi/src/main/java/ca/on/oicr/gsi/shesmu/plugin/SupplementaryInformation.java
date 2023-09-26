package ca.on.oicr.gsi.shesmu.plugin;

import ca.on.oicr.gsi.Pair;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.stream.Stream;

/** A list of table rows that can be inserted into definitions pages and the olive dashboard */
public interface SupplementaryInformation {

  /** A rich formatting element that can be displayed by the front-end. */
  abstract class DisplayElement {
    private DisplayElement() {}

    /**
     * Convert this element to a JSON object the front-end can display.
     *
     * @return the JSON representation
     */
    public abstract JsonNode toJson();
  }

  /**
   * Creates bolded text
   *
   * @param text the text to bold
   * @return the formatted text
   */
  static DisplayElement bold(String text) {
    return new DisplayElement() {
      @Override
      public JsonNode toJson() {
        final var node = JsonNodeFactory.instance.objectNode();
        node.put("type", "b");
        node.put("contents", text);
        return node;
      }
    };
  }

  /**
   * Displays an icon
   *
   * @param icon the icon to display
   * @return the icon
   */
  static DisplayElement icon(FrontEndIcon icon) {
    return new DisplayElement() {
      @Override
      public JsonNode toJson() {
        final var node = JsonNodeFactory.instance.objectNode();
        node.put("type", "icon");
        node.put("icon", icon.icon());
        return node;
      }
    };
  }

  /**
   * Creates italic text
   *
   * @param text the text to italicise
   * @return the formatted text
   */
  static DisplayElement italic(String text) {
    return new DisplayElement() {
      @Override
      public JsonNode toJson() {
        final var node = JsonNodeFactory.instance.objectNode();
        node.put("type", "i");
        node.put("contents", text);
        return node;
      }
    };
  }

  static DisplayElement link(String url, DisplayElement text, String title) {
    return new DisplayElement() {
      @Override
      public JsonNode toJson() {
        final var node = JsonNodeFactory.instance.objectNode();
        node.put("type", "a");
        node.put("url", url);
        node.set("contents", text.toJson());
        node.put("title", title);
        return node;
      }
    };
  }

  /**
   * Creates monospace text
   *
   * @param text the text to monospace
   * @return the formatted text
   */
  static DisplayElement mono(String text) {
    return new DisplayElement() {
      @Override
      public JsonNode toJson() {
        final var node = JsonNodeFactory.instance.objectNode();
        node.put("type", "tt");
        node.put("contents", text);
        return node;
      }
    };
  }

  /**
   * Concatenates multiple formatting elements
   *
   * @param elements the elements
   * @return the combined elements
   */
  static DisplayElement of(DisplayElement... elements) {
    return new DisplayElement() {
      @Override
      public JsonNode toJson() {
        final var node = JsonNodeFactory.instance.arrayNode();
        for (final var element : elements) {
          node.add(element.toJson());
        }
        return node;
      }
    };
  }

  /**
   * Creates plain text
   *
   * @param text the text
   * @return the formatted text
   */
  static DisplayElement text(String text) {
    return new DisplayElement() {
      @Override
      public JsonNode toJson() {
        return JsonNodeFactory.instance.textNode(text);
      }
    };
  }

  /**
   * Generate a table with a series of rows (a label and a value) to be inserted into the front end
   */
  Stream<Pair<DisplayElement, DisplayElement>> generate();
}
