package ca.on.oicr.gsi.shesmu;

public interface ActionConsumer {
  boolean accept(Action action, String filename, int line, int column, long time);

  boolean accept(String[] labels, String[] annotation, long ttl) throws Exception;
}
