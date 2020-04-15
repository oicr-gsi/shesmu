package ca.on.oicr.gsi.shesmu.plugin.filter;

/**
 * Allow creating custom "Export search" actions
 *
 * @param <T> the return type expected by the caller
 */
public interface ExportSearch<T> {
  /**
   * Export the search as a URL containing a URL-encoded JSON representation of the search
   *
   * <p>If the user selects this option, they will be redirected to the URL constructed
   *
   * @param name the name of the button to display to the user
   * @param urlStart the part of the URL to prepend to the JSON blob
   * @param urlEnd the part of the URL to append to the JSON blob
   * @param description a tooltip to display on the button
   */
  T linkWithJson(String name, String urlStart, String urlEnd, String description);
  /**
   * Export the search as a URL containing a base64-encoded representation of the search
   *
   * <p>If the user selects this option, they will be redirected to the URL constructed
   *
   * @param name the name of the button to display to the user
   * @param urlStart the part of the URL to prepend to the base64-encoded data
   * @param urlEnd the part of the URL to append to the base64-encoded data
   * @param description a tooltip to display on the button
   */
  T linkWithUrlSearch(String name, String urlStart, String urlEnd, String description);
}
