package ca.on.oicr.gsi.shesmu;

public interface ActionConsumer {
	void accept(Action action, String filename, int line, int column, long time);
}
