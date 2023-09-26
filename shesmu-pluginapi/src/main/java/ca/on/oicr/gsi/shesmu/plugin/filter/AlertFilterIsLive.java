package ca.on.oicr.gsi.shesmu.plugin.filter;
/** Alert filter that checks for an alert firing */
public final class AlertFilterIsLive extends AlertFilter {

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(f.isLive(), f);
  }
}
