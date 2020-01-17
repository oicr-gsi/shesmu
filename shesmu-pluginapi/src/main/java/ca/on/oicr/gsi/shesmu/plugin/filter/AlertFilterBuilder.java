package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface AlertFilterBuilder<F> {
  F and(Stream<F> filters);

  F fromSourceLocation(Stream<SourceOliveLocation> locations);

  F hasLabelName(Pattern labelName);

  F hasLabelName(String labelName);

  F hasLabelValue(String labelName, Pattern regex);

  F hasLabelValue(String labelName, String labelValue);

  F isLive();

  F negate(F filter);

  F or(Stream<F> filters);
}
