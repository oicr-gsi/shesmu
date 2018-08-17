package ca.on.oicr.gsi.shesmu;

import java.util.function.Function;

import org.objectweb.asm.Type;

public enum SignatureStorage {
	STATIC_FIELD {
		@Override
		public Type holderType(Type valueType) {
			return valueType;
		}
	},
	STATIC_METHOD {
		@Override
		public Type holderType(Type valueType) {
			return Type.getType(Function.class);
		}
	};
	public abstract Type holderType(Type valueType);
}
