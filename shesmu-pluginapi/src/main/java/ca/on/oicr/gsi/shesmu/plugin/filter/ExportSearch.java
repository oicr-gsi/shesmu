package ca.on.oicr.gsi.shesmu.plugin.filter;

import ca.on.oicr.gsi.shesmu.plugin.FrontEndIcon;

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
   * @param icon the icon of the button to display to the user
   * @param name the name of the button to display to the user
   * @param category the category of the search for grouping in the UI
   * @param categoryIcon the icon category of the search for grouping in the UI
   * @param urlStart the part of the URL to prepend to the JSON blob
   * @param urlEnd the part of the URL to append to the JSON blob
   * @param description a tooltip to display on the button
   */
  T linkWithJson(
      FrontEndIcon icon,
      String name,
      FrontEndIcon categoryIcon,
      String category,
      String urlStart,
      String urlEnd,
      String description);
  /**
   * Export the search as a URL containing a base64-encoded representation of the search
   *
   * <p>If the user selects this option, they will be redirected to the URL constructed
   *
   * @param icon the icon of the button to display to the user
   * @param name the name of the button to display to the user
   * @param category the category of the search for grouping in the UI
   * @param categoryIcon the icon category of the search for grouping in the UI
   * @param urlStart the part of the URL to prepend to the base64-encoded data
   * @param urlEnd the part of the URL to append to the base64-encoded data
   * @param description a tooltip to display on the button
   */
  T linkWithUrlSearch(
      FrontEndIcon icon,
      String name,
      FrontEndIcon categoryIcon,
      String category,
      String urlStart,
      String urlEnd,
      String description);
}
