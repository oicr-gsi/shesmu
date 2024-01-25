# Understanding the Shemsu compiler
This is a guide to understanding the Shesmu olive compiler. This is meant to
explain the core components that get the language to Java bytecode.

It assumes you have:

- a working understanding of Java bytecode. The [DZone bytecode tutorial](https://dzone.com/articles/introduction-to-java-bytecode) is a good place to start.
- have read the [plugin implementation guide](implementation.md)
- have written olives and understand the general structure of olives

Since the compiler will continue to expand and change, the bytecode dumps in
this tutorial may be different from future output of the compiler. However, the
core behaviour of the Shesmu compiler is based on Java's
[`Stream`](https://docs.oracle.com/javase/8/docs/api/index.html?java/util/stream/Stream.html),
which is stable, so they will be functionally similar. Bytecode dumps can be
seen on the olive dashboard.

Remember: JVM bytecode lacks generic type information. All the generic type
information is shown in its erased form.

To start off, compiling a simple script:

     Input shesmu;

is compiled to:

    public class dyn/shesmu/Program extends ca/on/oicr/gsi/shesmu/ActionGenerator {
    
      public <init>()V
        ALOAD 0
        INVOKESPECIAL ca/on/oicr/gsi/shesmu/ActionGenerator.<init> ()V
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 1
    
      private clearGauge()V
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 1
    
      public synchronized run(Lca/on/oicr/gsi/shesmu/ActionConsumer;Lca/on/oicr/gsi/shesmu/InputProvider;)V
        ALOAD 0
        INVOKEVIRTUAL dyn/shesmu/Program.clearGauge ()V
       L0
       L1
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 3
    
      public static <clinit>()V
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 0
    }

This class, `dyn.shesmu.Program`, extends the `ActionGenerator` superclass,
which is used by the server to execute actions. The class name is reused for
every script, but since each is loaded by a separate class loader, they are not
in conflict.

The constructor is relatively boring; it just calls the super-constructor. The
`run` method is where all the interesting things happen:

- The `clearGauge` method is executed at the start and performs all the
  necessary initialisation for gauges. This is a convenience of compiler
  design: the `run` and `clearGauge` methods can be built in parallel even though
  they are executed sequentially.
- Since this script has no olives, the `run` method does nothing else and
  returns.

Let's move to a script with an olive:

    Input shesmu;
    Olive
      Run nothing With value = type;


The output bytecode is more complicated. It is laid out below in chunks with
commentary.

The initialisation is much the same:
    
    public class dyn/shesmu/Program extends ca/on/oicr/gsi/shesmu/ActionGenerator {
    
      public <init>()V
        ALOAD 0
        INVOKESPECIAL ca/on/oicr/gsi/shesmu/ActionGenerator.<init> ()V
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 1
    
      private clearGauge()V
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 1
    
      public synchronized run(Lca/on/oicr/gsi/shesmu/ActionConsumer;Lca/on/oicr/gsi/shesmu/InputProvider;)V
        ALOAD 0
        INVOKEVIRTUAL dyn/shesmu/Program.clearGauge ()V


Now there are interesting differences. First, the current time is stored for timing the olive runtime.

       L0
        INVOKESTATIC java/lang/System.nanoTime ()J
        LSTORE 3

Now, a stream of the correct type must be started that will contain the right
objects. The supplied argument is `ca.on.oicr.gsi.shesmu.InputProvider` and its
purpose is to provide a stream of values for a class specified by the input
format. The olives can request all the data for the required format.

For every script, there is one input format. At the top of the file `Input
shesmu;` selects the input format. Each format has a corresponding
`InputFormatDefinition` class. For `Input shesmu;`,
`ca.on.oicr.shesmu.core.input.shesmu.ShesmuIntrospectionFormatDefinition.java`
describes the input format. Each input format has a method `itemClass()` that
returns a class that holds the values that are passed through the stream, that
is, the `T` in `InputProvider.<T>fetch`. For
`ShesmuIntrospectionFormatDefinition`, the specified class is
`ca.on.oicr.shesmu.core.input.shesmu.ShesmuIntrospectionValue`.

When the `InputProvider.fetch` is invoked, it will provide a stream of data of the
correct type from all the input repositories. If the input format is a
`BaseInputFormatDefinition<`_V_`,`_R_`>` as described in the [plugin
implementation guide](implementation.md), this will call effectively do
`ServiceLoader.load(`_R_`.class).stream().flatMap(InputRepository::stream)`:

        ALOAD 2
        LDC Lca/on/oicr/gsi/shesmu/core/input/shesmu/ShesmuIntrospectionValue;.class
        INVOKEINTERFACE ca/on/oicr/gsi/shesmu/InputProvider.fetch (Ljava/lang/Class;)Ljava/util/stream/Stream; (itf)

This particular olive has no filters, so, a method reference/lambda is
generated and then <tt>forEach</tt> is called on the stream and the stream is
closed:


       L1
        LINENUMBER 2 L1
        DUP
        ALOAD 0
        ALOAD 1
        INVOKEDYNAMIC accept(Ldyn/shesmu/Program;Ljava/util/function/Consumer;)Ljava/util/function/Consumer; [
          // handle kind 0x6 : INVOKESTATIC
          java/lang/invoke/LambdaMetafactory.metafactory(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
          // arguments:
          (Ljava/lang/Object;)V, 
          // handle kind 0x5 : INVOKEVIRTUAL
          dyn/shesmu/Program.Run nothing 2:6(Ljava/util/function/Consumer;Lca/on/oicr/gsi/shesmu/core/input/shesmu/ShesmuIntrospectionValue;)V, 
          (Lca/on/oicr/gsi/shesmu/core/input/shesmu/ShesmuIntrospectionValue;)V
        ]
        INVOKEINTERFACE java/util/stream/Stream.forEach (Ljava/util/function/Consumer;)V (itf)
        INVOKEINTERFACE java/util/stream/Stream.close ()V (itf)


Finally, the olive's run time is recorded using the time stored earlier:

        GETSTATIC ca/on/oicr/gsi/shesmu/ActionGenerator.OLIVE_RUN_TIME : Lio/prometheus/client/Gauge;
        ICONST_2
        ANEWARRAY java/lang/String
        DUP
        ICONST_0
        LDC "/home/amasella/shesmu/local/example.shesmu"
        AASTORE
        DUP
        ICONST_1
        LDC "2"
        AASTORE
        INVOKEVIRTUAL io/prometheus/client/Gauge.labels ([Ljava/lang/String;)Ljava/lang/Object;
        CHECKCAST io/prometheus/client/Gauge$Child
        INVOKESTATIC java/lang/System.nanoTime ()J
        LLOAD 3
        LSUB
        L2D
        LDC 1.0E9
        DDIV
        INVOKEVIRTUAL io/prometheus/client/Gauge$Child.set (D)V


And the <tt>run</tt> method is finished:

       L2
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 5


The _signature_ feature of Shesmu can generate two kinds of signatures: ones that
are the same for any input and ones that are computed from the input data. The
first are stored as constants and the second are method.

Therefore, the class constructor, <tt>&lt;clinit&gt;</tt>, prepares the ones that
are constants:

      static J Olive 2:6 signable_count
    
      static Ljava/util/Set; Olive 2:6 signable_names
    
      static J Olive 2:6 signature_count
    
      static Ljava/util/Set; Olive 2:6 signature_names
     
      public static <clinit>()V
        LCONST_0
        PUTSTATIC dyn/shesmu/Program.Olive 2:6 signable_count : J
        NEW java/util/TreeSet
        DUP
        INVOKESPECIAL java/util/TreeSet.<init> ()V
        PUTSTATIC dyn/shesmu/Program.Olive 2:6 signable_names : Ljava/util/Set;
        LCONST_0
        PUTSTATIC dyn/shesmu/Program.Olive 2:6 signature_count : J
        NEW java/util/TreeSet
        DUP
        INVOKESPECIAL java/util/TreeSet.<init> ()V
        PUTSTATIC dyn/shesmu/Program.Olive 2:6 signature_names : Ljava/util/Set;
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 0


and the signatures based on data are created as methods:
    
      private static Olive 2:6 json_signature(Lca/on/oicr/gsi/shesmu/core/input/shesmu/ShesmuIntrospectionValue;)Ljava/lang/String;
        NEW ca/on/oicr/gsi/shesmu/core/signers/JsonSigner
        DUP
        INVOKESPECIAL ca/on/oicr/gsi/shesmu/core/signers/JsonSigner.<init> ()V
        INVOKEINTERFACE ca/on/oicr/gsi/shesmu/Signer.finish ()Ljava/lang/Object; (itf)
        CHECKCAST java/lang/String
        ARETURN
        MAXSTACK = 0
        MAXLOCALS = 1
    
      private static Olive 2:6 sha1_signature(Lca/on/oicr/gsi/shesmu/core/input/shesmu/ShesmuIntrospectionValue;)Ljava/lang/String;
        NEW ca/on/oicr/gsi/shesmu/core/signers/SHA1DigestSigner
        DUP
        INVOKESPECIAL ca/on/oicr/gsi/shesmu/core/signers/SHA1DigestSigner.<init> ()V
        INVOKEINTERFACE ca/on/oicr/gsi/shesmu/Signer.finish ()Ljava/lang/Object; (itf)
        CHECKCAST java/lang/String
        ARETURN
        MAXSTACK = 0
        MAXLOCALS = 1


Earlier, a method was referenced that would be converted to a `Consumer` and
fed to the `forEach` call. Here, this method is defined. This method will
produce an action (or alert if an `Alert` olive):

      private Run nothing 2:6(Ljava/util/function/Consumer;Lca/on/oicr/gsi/shesmu/core/input/shesmu/ShesmuIntrospectionValue;)V
       L0
        LINENUMBER 2 L0

It constructs a new action:

        NEW ca/on/oicr/gsi/shesmu/core/NothingAction
        DUP
        INVOKESPECIAL ca/on/oicr/gsi/shesmu/core/NothingAction.<init> ()V
        ASTORE 3


The loads in all the arguments:

       L1
        LINENUMBER 3 L1
        ALOAD 3
        ALOAD 2
        INVOKEVIRTUAL ca/on/oicr/gsi/shesmu/core/input/shesmu/ShesmuIntrospectionValue.type ()Ljava/lang/String;
        PUTFIELD ca/on/oicr/gsi/shesmu/core/NothingAction.value : Ljava/lang/String;
       L2
        LINENUMBER 2 L2


Then sends the action in to the consumer with data about the olive that generated it:

        ALOAD 3
        INVOKEVIRTUAL ca/on/oicr/gsi/shesmu/core/NothingAction.prepare ()V
        ALOAD 1
        ALOAD 3
        LDC "/home/amasella/shesmu/local/example.shesmu"
        ICONST_2
        BIPUSH 6
        LDC 1546533662036
        INVOKEINTERFACE ca/on/oicr/gsi/shesmu/ActionConsumer.accept (Lca/on/oicr/gsi/shesmu/Action;Ljava/lang/String;IIJ)Z (itf)
        POP
        RETURN
        MAXSTACK = 0
        MAXLOCALS = 4
    }

This is the end of the example olive bytecode.

## Olive Design and Lambdas
Every clause in an olive generates additional calls to methods in Java's
`Stream` or methods in Shemu's `RuntimeSupport` to manipulate the stream. Those
methods take parameters: some of which are simple values; others are method
references/lambdas.

When generating lambdas, it's important to understand captures. Take the following Java code:

    IntUnaryOperator adder(int offset) {
       return x -> x + offset;
    }

When Java compiler it, it creates two methods:

    IntUnaryOperator adder(int offset)
    static int adder$lambda$0(int offset, int x)

When adder creates a `IntUnaryOperator` for `adder$lambda$0`, it must determine
which values from the enclosing context must be forced into the returned
lambda. These values are called captures. In this case, `this` isn't captured,
so the target method is static, but if it accesses fields, then `this` must be
captured.

The Shesmu compiler must capture things in the same way. This gets much more
complicated since Java's captures are either explicit references or,
implicitly, `this`. Shesmu needs to capture:

- `this`
- any variables/constants that the olive mentions
- the current olive line and column numbers
- the signatures
- the _stream_ variable, in some contexts

Most of this capturing is necessary because of `Define`. Take the following:

     Input shesmu;

     Define foo(string x)
       Where type = x;

What does the signature of `foo` look like in bytecode?

Let's assume it's `void foo(String x)` and build outward from there.

First, it must take stream of `ShesmuIntrospectionValue` and return a modified stream:

    Stream<ShesmuIntrospectionValue> foo(
      Stream<ShesmuIntrospectionValue> input,
      String x)

Second, when updating the olive data flow information, it needs to associate
that data with the calling olive, so it needs to take the line and column:

    Stream<ShesmuIntrospectionValue> foo(
      Stream<ShesmuIntrospectionValue> input,
      int line,
      int column,
      String x)

It's possible to use signatures inside the `Define`, but the variables that can
be included in the signature can come from both the caller and callee:

    Input shesmu;

    Define foo(string x)
      Where type = x; # A signature used here should have both `type`
                         and `changed` in it
    Olive
      foo("hi")
      Where now > changed + 3hour
      Run ...;

Therefore, signatures must be passed:

    Stream<ShesmuIntrospectionValue> foo(
      Stream<ShesmuIntrospectionValue> input,
      int line,
      int column,
      long signature_count,
      long signable_count,
      Set<String> signature_names,
      Set<String> signable_names,
      Function<ShesmuIntrospectionValue, String> sha1_signature,
      Function<ShesmuIntrospectionValue, String> json_signature,
      String x)

Currently, all signatures are passed, but that could be improved.

In the case of the `Where` clause, `Predicate<ShesmuIntrospectionValue>` needs
to be generated. It will have a different signature:

    boolean check(
      Instant now,
      ShesmuIntrospectionValue streamValue)

By Java convention, when making a lambda, the arguments to the lambda itself
come after the captures. In the case of Shesmu's use of stream, this is always
the _stream value_ (_i.e._, the current item in the stream).

When accessing the stream variable `changed`, it will generate code like:

     streamValue.changed()

This seems straight forward enough, but it's complicated by adding `For`
expression, which are also implemented using `Stream`. Consider:

    Input shesmu;

    Olive
      Where For l In locations: Any l.timestamp > generated;
      Run ...;

When evaluating `l.timestamp == generated`, `generated` comes from
`ShesmuIntrospectionValue` in the outer olive stream while `l.timestamp` comes
from the inner stream generated by the `For`. In Java, this would be something
like:


     InputDefinition.all(ShesmuIntrospectionValue.class)
       .filter(x -> x.l().stream().anyMatch(l -> l.timestamp() > x.generated()))
       .forEach(...);

All this implicit capturing can produce some pretty _yikes_ methods, but if you
look at lambda-heavy Java code, the methods also tend to be pretty yikes.

There's a constant trade off here: more complexity in the compiler can be used
to avoid capturing unnecessary values.

The `Group` and `LeftJoin` operations are particularly gnarly since they have
multiple expressions connected together. For instance, consider:

    Group
      ys = Where z List y + a
    By x

If `x`, `y` and `z` are all stream variables, then only `a` is captured.

For grouping, two functions are needed: one which makes a key to split the data
up into subsets, and then one that merges everything in a subset together.

For ease, the compiler will generate a single set of captures for both of those
operations. That means that the key operation will capture `a` even though it
doesn't need it.

## Compiler Mechanics

The compiler operates in a few phases:

1. Parse the file into a parse tree, rooted as a `ProgramNode`.
2. Resolve input formats, actions, functions, and `Define`/call references.
3. Resolve variables.
4. Check the type and stream purity of everything.
5. Convert everything to bytecode. This is in the _render_ methods.

There are a lot of classes in the compiler, but the major ones are:

- `OliveNode`, which declares each top-level element in the file (function, olive, define olive, constant)
- `OliveClauseNode`, which defines clauses in an olive
- `ExpressionNode`, which defines all the bits and pieces of expressions

For details on components, the JavaDoc outlines the behaviour and expectations
of the classes. Most of the documentation is on the abstract classes and the
concrete classes have less since they just execute the behaviour defined by the
abstract class.

For general naming conventions:
- _resolve_ methods convert strings into useful metadata (_e.g._ find the `FunctionDefinition` given the name in the script)
- _render_ methods generate bytecode
- classes that end in _Node_ are a general chunk of syntax in the language (_e.g._ expressions); their subclasses represent concrete bits of syntax the user can write and they should be completely interchangeable
- classes that end in _Builder_ are utility classes that make generating bytecode simpler

`ExpressionNode` has many similar clones. For instance, there is an expression
node for `For` and then ones for each of the sources, clauses, subsamples, and
collectors in a `For` expression, containing other expressions.  While these
are structurally different, they are built very similarly to `ExpressionNode`.

The method `collectFreeVariabes` is used to find variables used in expressions.
It serves multiple functions:

- computing captures is done as part of bytecode production in _render_ methods
- finding signable variables for signature preparation
- determining if stream variables are used in `By` expressions

### Type Information
The type system for Shesmu is meant to be very simple and static. There is no
inheritance and no automatic conversion. It's also entirely bottom up. For
instance, in Java, type information flows both ways:

     List<Foo> x = new ArrayList<>();

Type information about the type argument `Foo` is flowing in the opposite
direction as the type information about `ArrayList<Foo>` being assigned to
`List<Foo>`. 

In Shesmu, everything flows from the leaves of the tree towards the root. For instance:

     path_file(dir + 'x.txt')

If `dir` cannot be resolved or was defined by an expression that has a type
error, this has no valid type. Even though we know that `path_file` expects a
`path` type and the `+` operator can combine two paths. Shesmu will simply give
up. This is meant to keep the compiler simple.

### Bytecode Generation
When generating bytecode, Shesmu uses
[`GeneratorAdapter`](https://asm.ow2.io/javadoc/org/objectweb/asm/commons/GeneratorAdapter.html)
from the ASM package to write bytecode. It wraps this in a
`ca.on.oicr.gsi.shesmu.compiler.Renderer` class. `Renderer` provides a number
of utility functions, such as inserting `Imyhat` objects into the bytecode. Its
real purpose is to track extra information. It knows where the current stream
value is stored, which will be different through many layers of capturing. It
also tracks how to access signatures.

The ASM bytecode generation library has a class `Type` that describes JVM
types. A `Type` object can be constructed either by knowing the JVM name for a
class, or by using `Type.getType(Foo.class)` where _Foo_ is the class.

### Stream Purity
Shesmu has two kinds of stream purity: olive-level (type) and `For`-level
(order). They are conceptually similar, but perform different functions.

For all operations on Java streams, the order or type of the items can be
changed by some operations.

In the case of olives, `Group`, `Join`, `LeftJoin` change the type of the
stream value while `Where`, `Monitor`, `Reject`, and `Pick` do not. A `Define`
olive has more stringent requirements; the original type of the input stream
(as defined in `Input x;`) is the only valid input type for `Define` olives.
Therefore, no type-changing operation can precede a call, but the operations
that do not modify the type can precede a call, including call operations to
`Define` olives that do not modify the type. Shesmu must figure out a stream
purity to ensure that call operations will be type-safe. Additionally,
signatures must be computed over the clauses in the original type-pure stream
plus the input half of the first transformative clause.

In `For` clauses, the type is not important, however, the sorting is. In
Shesmu, we want values given to olives to be stable. If, say, a bunch of items
are going to be concatenated into a string, they must be in a well-defined
order. Therefore, each clause in a `For` can be either order-preserving
(_e.g._, `Where`), order-creating (_e.g._, `Sort`), order-destroying (_e.g._,
`Flatten`), or order-dependent (_e.g._ `Reverse`, `Skip`, `Subsample`).

The order checking makes sure that a chain of operations will have a stable
meaningful operation. For instance, the following is not meaningful:

    For x In xs: Reverse FixedConcat x With ","

`Reverse` requires an order that isn't present in the original. The following is valid:

    For x In xs: Sort x.timestamp Reverse FixedConcat x.library_name With ","

Even though the final output doesn't contain the timestamps, the output will be
stable based on the input.

The final collector matters. If the operation is `Count`, then order is
irrelevant. The following is valid, though kind of pointless:

    For x In xs: Sort x.timestamp Flatten (y In x.libraries) Count

The `Sort` operation has its order destroyed by the `Flatten`, however `Count`
doesn't care either way. The goal is to stop the user from writing dangerously
unstable output, not inefficient code.

`Flatten` is not always order-destroying. If the input and output of the
flattening is ordered, then there is still order:

    For x In [ {1, ["a,b,c"], {3,["z,y,x"]} ]:
      Sort x[0]
      Flatten (y Splitting x[1] By /,/)
      FixedConcat y
