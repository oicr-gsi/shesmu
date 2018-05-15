package ca.on.oicr.gsi.shesmu;

public interface Dumper {

	public void start();

	public void stop();

	public void write(Object[] values);
}
