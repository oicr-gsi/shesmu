package ca.on.oicr.gsi.shesmu.signers;

import java.util.stream.Stream;

import org.kohsuke.MetaInfServices;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.commons.Method;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.SignatureStorage;
import ca.on.oicr.gsi.shesmu.SignatureVariable;
import ca.on.oicr.gsi.shesmu.Signer;
import ca.on.oicr.gsi.shesmu.compiler.Renderer;
import ca.on.oicr.gsi.shesmu.compiler.Target;

/**
 * Create a signature variable for a subclass of {@link Signer}
 * 
 * To create a signature,
 * <ol>
 * <li>create a public class that implements the {@link Signer} with a public
 * no-arguments constructor.
 * <li>create a public class extending this one with a no-arguments constructor
 * that is marked with {@link MetaInfServices} for {@link SignatureVariable}
 * </ol>
 *
 * @param <T>
 *            the signer class to use
 * @param <R>
 *            the result value of the signature
 */
public abstract class SignatureVariableForSigner<T, R> extends SignatureVariable {
	private static final Type A_IMYHAT_TYPE = Type.getType(Imyhat.class);
	private static final Type A_OBJECT_TYPE = Type.getType(Object.class);
	private static final Type A_SIGNER_TYPE = Type.getType(Signer.class);
	private static final Type A_STRING_TYPE = Type.getType(String.class);
	private static final Method CTOR_DEFAULT = new Method("<init>", Type.VOID_TYPE, new Type[] {});

	private static final Method METHOD_SIGNER__ADD_VARIABLE = new Method("addVariable", Type.VOID_TYPE,
			new Type[] { A_STRING_TYPE, A_IMYHAT_TYPE, A_OBJECT_TYPE });

	private static final Method METHOD_SIGNER__FINISH = new Method("finish", A_OBJECT_TYPE, new Type[] {});

	private final Type outputBuilderType;

	public SignatureVariableForSigner(String name, Class<? extends Signer<R>> outputBuilderClazz, Imyhat type,
			Class<R> returnClazz) {
		super(name, SignatureStorage.STATIC_METHOD, type);
		if (!type.javaType().isAssignableFrom(returnClazz)) {
			throw new IllegalArgumentException(
					String.format("Return type of signer %s cannot be assigned to Imyhat %s.",
							returnClazz.getCanonicalName(), type.name()));
		}
		this.outputBuilderType = Type.getType(outputBuilderClazz);
	}

	@Override
	public final void build(GeneratorAdapter method, Type initialType, Stream<Target> variables) {
		method.newInstance(outputBuilderType);
		method.dup();
		method.invokeConstructor(outputBuilderType, CTOR_DEFAULT);
		variables.forEach(target -> {

			method.dup();
			method.push(target.name());
			Renderer.loadImyhatInMethod(method, target.type().signature());
			method.loadArg(0);
			method.invokeVirtual(initialType, new Method(target.name(), target.type().asmType(), new Type[] {}));
			method.box(target.type().asmType());
			method.invokeInterface(A_SIGNER_TYPE, METHOD_SIGNER__ADD_VARIABLE);
		});
		method.invokeInterface(A_SIGNER_TYPE, METHOD_SIGNER__FINISH);
		method.unbox(type().asmType());
	}

}
