package ca.on.oicr.gsi.shesmu.core.signers;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.stream.Stream;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.ImyhatDispatcher;
import ca.on.oicr.gsi.shesmu.Signer;
import ca.on.oicr.gsi.shesmu.runtime.RuntimeSupport;

public class SHA1DigestSigner implements Signer<String>, ImyhatDispatcher {
	private final MessageDigest digest;

	public SHA1DigestSigner() throws NoSuchAlgorithmException {
		digest = MessageDigest.getInstance("SHA1");
	}

	@Override
	public void addVariable(String name, Imyhat type, Object value) {
		digest.update(name.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) ':');
		type.consume(this, value);
	}

	@Override
	public void consume(boolean value) {
		digest.update((byte) (value ? 1 : 0));
	}

	@Override
	public void consume(Instant value) {
		digest.update((byte) 133);
		digest.update(
				ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value.toEpochMilli()).array());

	}

	@Override
	public void consume(int position, Object value, Imyhat type) {
		digest.update((byte) position);
		type.consume(this, value);
	}

	@Override
	public void consume(long value) {
		digest.update(ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(value).array());

	}

	@Override
	public <T> void consume(Stream<T> values, Imyhat inner) {
		digest.update((byte) 9);
		values.forEach(item -> inner.consume(this, item));
	}

	@Override
	public void consume(String value) {
		digest.update(value.getBytes(StandardCharsets.UTF_8));
	}

	@Override
	public void consume(String field, Object value, Imyhat type) {
		digest.update(field.getBytes(StandardCharsets.UTF_8));
		digest.update((byte) '$');
		type.consume(this, value);
	}

	@Override
	public String finish() {
		return RuntimeSupport.printHexBinary(digest.digest());
	}

}
