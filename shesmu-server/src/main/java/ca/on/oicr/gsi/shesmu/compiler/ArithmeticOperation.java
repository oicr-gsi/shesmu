package ca.on.oicr.gsi.shesmu.compiler;

import java.util.function.Consumer;

import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;

public abstract class ArithmeticOperation {
	public static final ArithmeticOperation BAD = new ArithmeticOperation(Imyhat.BAD, Imyhat.BAD, Imyhat.BAD) {

		@Override
		public void render(Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {
			throw new UnsupportedOperationException();
		}
	};

	public static ArithmeticOperation shortCircuit(int condition) {
		return new ArithmeticOperation(Imyhat.BOOLEAN, Imyhat.BOOLEAN, Imyhat.BOOLEAN) {

			@Override
			public void render(Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {
				leftValue.accept(renderer);
				final Label end = renderer.methodGen().newLabel();
				renderer.methodGen().dup();
				renderer.methodGen().ifZCmp(condition, end);
				renderer.methodGen().pop();
				rightValue.accept(renderer);
				renderer.methodGen().mark(end);
			}
		};
	}

	public static ArithmeticOperation primitiveMath(Imyhat type, int opcode) {
		return new ArithmeticOperation(type, type, type) {

			@Override
			public void render(Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

				leftValue.accept(renderer);
				rightValue.accept(renderer);
				renderer.methodGen().math(opcode, type.asmType());

			}
		};
	}

	public static ArithmeticOperation staticMethod(Imyhat leftType, Imyhat rightType, Imyhat returnType, Type owner,
			String methodName) {
		return new ArithmeticOperation(leftType, rightType, returnType) {

			@Override
			public void render(Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

				leftValue.accept(renderer);
				rightValue.accept(renderer);
				renderer.methodGen().invokeStatic(owner, new Method(methodName, returnType.asmType(),
						new Type[] { leftType.asmType(), rightType.asmType() }));

			}
		};
	}

	public static ArithmeticOperation virtualMethod(Imyhat leftType, Imyhat rightType, Imyhat returnType,
			String methodName) {
		final Method method = new Method(methodName, returnType.asmType(), new Type[] { rightType.asmType() });
		return new ArithmeticOperation(leftType, rightType, returnType) {

			@Override
			public void render(Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue) {

				leftValue.accept(renderer);
				rightValue.accept(renderer);
				if (leftType.javaType().isInterface()) {
					renderer.methodGen().invokeInterface(leftType.asmType(), method);
				} else {
					renderer.methodGen().invokeVirtual(leftType.asmType(), method);
				}

			}
		};
	}

	private final Imyhat leftType;

	private final Imyhat returnType;

	private final Imyhat rightType;

	public ArithmeticOperation(Imyhat leftType, Imyhat rightType, Imyhat returnType) {
		super();
		this.leftType = leftType;
		this.rightType = rightType;
		this.returnType = returnType;
	}

	public Imyhat leftType() {
		return leftType;
	}

	public abstract void render(Renderer renderer, Consumer<Renderer> leftValue, Consumer<Renderer> rightValue);

	public Imyhat returnType() {
		return returnType;
	}

	public Imyhat rightType() {
		return rightType;
	}
}
