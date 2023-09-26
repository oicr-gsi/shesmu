package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Construct an implementation-specific alert filter
 *
 * @param <F> the resulting filter type
 * @param <S> the label names and values type
 */
public interface AlertFilterBuilder<F, S> {

  /**
   * Construct a filter that requires all provided filters be matched.
   *
   * @param filters the filters to match
   * @return the constructed filter
   */
  F and(Stream<F> filters);

  /**
   * Construct a filter that requires alerts be generated from one of the provided olives
   *
   * @param locations the source olives to check
   * @return the constructed filter
   */
  F fromSourceLocation(Stream<SourceOliveLocation> locations);

  /**
   * Construct filter that matches any alerts that have a label matching the provided regular
   * expression.
   *
   * @param labelName the regular expression to check
   * @return the constructed filter
   */
  F hasLabelName(Pattern labelName);

  /**
   * Construct filter that matches any alerts that have a label matching the provided name.
   *
   * @param labelName the name to check
   * @return the constructed filter
   */
  F hasLabelName(S labelName);

  /**
   * Construct filter that matches any alerts that have a label, with the provided name, with a
   * value matching the provided regular expression.
   *
   * @param labelName the name to check
   * @param regex the regular expression to check on the value
   * @return the constructed filter
   */
  F hasLabelValue(S labelName, Pattern regex);

  /**
   * Construct filter that matches any alerts that have a label, with the provided name, with a
   * value equal to the provided value.
   *
   * @param labelName the name to check
   * @param labelValue the value to check
   * @return the constructed filter
   */
  F hasLabelValue(S labelName, S labelValue);

  /**
   * Construct a filter that checks if the alert is firing.
   *
   * @return the constructed filter
   */
  F isLive();

  /**
   * Inverts the sense of a filter
   *
   * @param filter the filter to invert
   * @return the inverted filter
   */
  F negate(F filter);

  /**
   * Create a filter that matches any of the supplied filters.
   *
   * @param filters the filters to match
   * @return the constructed filter
   */
  F or(Stream<F> filters);
}
