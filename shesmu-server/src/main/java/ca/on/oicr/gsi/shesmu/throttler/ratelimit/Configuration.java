package ca.on.oicr.gsi.shesmu.throttler.ratelimit;

public class Configuration {
	private int capacity;
	private int delay;

	public int getCapacity() {
		return capacity;
	}

	public int getDelay() {
		return delay;
	}

	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

	public void setDelay(int delay) {
		this.delay = delay;
	}

}
