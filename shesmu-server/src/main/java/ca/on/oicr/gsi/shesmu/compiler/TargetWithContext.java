package ca.on.oicr.gsi.shesmu.compiler;

public interface TargetWithContext extends Target {
  /**
   * Set a shadow context for this target
   *
   * <p>When doing optional unboxing with the <tt>?</tt> operator, the expression is evaluated in
   * the containing context (i.e., outside the <tt>``</tt>). This can lead to confusion in
   * situations where local variables are defined. For instance, <tt>`For x In xs: List x?`</tt>
   * seems valid, but <tt>x</tt> will be evaluated outside the <tt>For</tt> and not have access to
   * the <tt>x</tt> that it defines. To produce a better error message, we provide a second set of
   * variables to <tt>x?</tt> that it exist but can't access; if it finds one of these variables, it
   * can explain the situation better than the confusing “x is undefined”.
   *
   * @param defs the variables accessible from the context inside the back ticks
   */
  void setContext(NameDefinitions defs);
}
