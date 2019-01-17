package ca.on.oicr.gsi.shesmu;

public interface Signer<T> {

  void addVariable(String name, Imyhat type, Object value);

  T finish();
}
