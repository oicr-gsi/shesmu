package ca.on.oicr.gsi.shesmu.plugin.filter;

public final class AlertFilterIsLive extends AlertFilter {

  @Override
  public <F> F convert(AlertFilterBuilder<F, String> f) {
    return maybeNegate(f.isLive(), f);
  }
}
