package ca.on.oicr.gsi.shesmu.plugin.filter;

import java.util.regex.Pattern;
import java.util.stream.Stream;

public interface AlertFilterBuilder<F, S> {
  F and(Stream<F> filters);

  F fromSourceLocation(Stream<SourceOliveLocation> locations);

  F hasLabelName(Pattern labelName);

  F hasLabelName(S labelName);

  F hasLabelValue(S labelName, Pattern regex);

  F hasLabelValue(S labelName, S labelValue);

  F isLive();

  F negate(F filter);

  F or(Stream<F> filters);
}
