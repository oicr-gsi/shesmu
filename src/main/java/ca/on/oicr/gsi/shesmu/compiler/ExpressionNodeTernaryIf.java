package ca.on.oicr.gsi.shesmu.compiler;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.objectweb.asm.Label;
import org.objectweb.asm.commons.GeneratorAdapter;

import ca.on.oicr.gsi.shesmu.Imyhat;
import ca.on.oicr.gsi.shesmu.Lookup;

public class ExpressionNodeTernaryIf extends ExpressionNode {

	private ExpressionNode testExpression;
	private ExpressionNode trueExpression;
	private ExpressionNode falseExpression;

	public ExpressionNodeTernaryIf(int line, int column, ExpressionNode testExpression, 
			ExpressionNode trueExpression, ExpressionNode falseExpression) {
		super(line, column);
		this.testExpression = testExpression;
		this.trueExpression = trueExpression;
		this.falseExpression = falseExpression;
	}

	@Override
	public void collectFreeVariables(Set<String> names) {
		testExpression.collectFreeVariables(names);
		trueExpression.collectFreeVariables(names);
		falseExpression.collectFreeVariables(names);
	}

	@Override
	public void render(Renderer renderer) {
		testExpression.render(renderer);
		renderer.mark(line());
		
		final Label end = renderer.methodGen().newLabel();
		final Label truePath = renderer.methodGen().newLabel();
		renderer.methodGen().ifZCmp(GeneratorAdapter.NE, truePath);
		falseExpression.render(renderer);
		renderer.methodGen().goTo(end);
		renderer.methodGen().mark(truePath);
		trueExpression.render(renderer);
		renderer.methodGen().mark(end);
	}

	@Override
	public boolean resolve(NameDefinitions defs, Consumer<String> errorHandler) {
		return testExpression.resolve(defs, errorHandler) & trueExpression.resolve(defs, errorHandler) 
				& falseExpression.resolve(defs, errorHandler);
	}

	@Override
	public boolean resolveLookups(Function<String, Lookup> definedLookups, Consumer<String> errorHandler) {
		return testExpression.resolveLookups(definedLookups, errorHandler) 
				& trueExpression.resolveLookups(definedLookups, errorHandler) 
				& falseExpression.resolveLookups(definedLookups, errorHandler);
	}

	@Override
	public Imyhat type() {
		return trueExpression.type();
	}

	@Override
	public boolean typeCheck(Consumer<String> errorHandler) {
		boolean testOk = testExpression.typeCheck(errorHandler);
		if (testOk) {
			testOk = testExpression.type().isSame(Imyhat.BOOLEAN);
			if (!testOk) typeError("boolean", testExpression.type(), errorHandler);
		}
		boolean resultOk = trueExpression.typeCheck(errorHandler) & falseExpression.typeCheck(errorHandler);
		if (resultOk) {
			resultOk = trueExpression.type().isSame(falseExpression.type());
			if (!resultOk) typeError(trueExpression.type().name(), falseExpression.type(), errorHandler);
		}
		return testOk & resultOk;
	}

}
