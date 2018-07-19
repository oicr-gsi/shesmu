# Implementing Plugins for Shesmu

Shesmu does not provide much out-of-the-box. Every environment is going to be
very different, so Shesmu is designed to be very modular. If the available
plugins are suitable, great. If not, it was designed to be easy to extend.

## General Plugin Infrastructure
Shesmu uses
[`ServiceLoader`](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html)
to find plugins. For service loaders to work, a plugin should extend the
service interface, be marked with a `@MetaInfServices` annotation, and placed
into a JAR on the `CLASSPATH`. The Java libraries will take care of the rest.
Since all the plugins must coexist, it is strongly recommended to that a
[_shaded_ JAR](https://maven.apache.org/plugins/maven-shade-plugin/) is used.
Shading a JAR renames classes in a way that allows different versions of the
same library to coexist without conflict.


In a typical Maven build file, the following is necessary to get the basic dependencies:

    <dependencies>
      <dependency>
        <groupId>ca.on.oicr.gsi</groupId>
        <artifactId>shesmu</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <scope>provided</scope>
      </dependency>
      <dependency>
        <groupId>io.prometheus</groupId>
        <artifactId>simpleclient</artifactId>
        <version>0.0.26</version>
        <scope>provided</scope>
      </dependency>
      <dependency>
        <groupId>org.kohsuke.metainf-services</groupId>
        <artifactId>metainf-services</artifactId>
        <version>1.1</version>
        <optional>true</optional>
        <scope>provided</scope>
      </dependency>
      <dependency>
        <groupId>org.ow2.asm</groupId>
        <artifactId>asm-debug-all</artifactId>
        <version>5.2</version>
        <scope>provided</scope>
      </dependency>
    </dependencies>

Shesmu provides all monitoring output via
[Prometheus](https://github.com/prometheus/client_java). All the infrastructure
is wired in, so simply building instruments and using `.register()` will add
them to the monitoring output.

## The Compiler: JVM and ASM
Shesmu is a compiler: it takes olives and compiles them to JVM bytecode. The
plugin interfaces avoid as much of the JVM internals as possible, but some is
required.

There are plenty of tutorials on JVM internals, but the [DZone bytecode
tutorial](https://dzone.com/articles/nominalized-adjectives-as-names-for-decorators)
is one of the better ones.

Bytecode is written using [ASM's
`GeneratorAdapter`](https://static.javadoc.io/org.ow2.asm/asm/5.2/org/objectweb/asm/commons/GeneratorAdapter.html)
which provides methods for writing bytecode that is slightly more high-level
than regular bytecode.

## General Principles
For all types of plugins in Shesmu:

1. Shesmu is heavily multi-threaded, so plugins must be thread-safe. Locking is
permitted, but blocking should be avoided.
1. Configuration is handled by the plugin and no configuration can be provided
by command line arguments.
1. The Shesmu server has no state. Upon restart, a Shesmu instance has to
recover its state by reading in all the input data again and generating all
actions again through the olives.

## Provided Utilities
The class `AutoUpdatingDirectory` provides a mechanism to scan for new
configuration files in the Shesmu data directory and update the files when they
change on disk. If the configuration is a JSON file, `AutoUpdatingJsonFile` is
a utility class for parsing the JSON files.

## Types and Erasure
The correct handling of types in Shesmu is complicated. There are different but
interlocking type systems:

1. The Java type system
1. The JVM type system
1. The ASM type system
1. The Shesmu type system
1. The JSON type system

Shesmu's type system describe the types of every type that can be used in an
olive. Because `Type` is used by the ASM library, Shesmu's types are
represented by `Imyhat` objects (this is an Ancient Egyptian word for _mould of
conduct_). 

Java's type system is similar to, but more sophisticated than, the JVM type
system. In particular, generic types in Java are [erased on the
JVM](https://docs.oracle.com/javase/tutorial/java/generics/index.html).

Shesmu's types are also erased in a way that is similar to Java's. This matters
for Shesmu's lists, which are Java's `Set` underneath. Shesmu's tuples are
erased to `Object` values. Although some of these JVM types may be null, Shesmu
olives cannot handle null values.

| Name      | JVM Type                      | Syntax                   | Signature  |
|---        |---                            |---                       |---         |
| Integer   | `long`                        | `integer`                | `i`	      |
| String    | `java.lang.String`            | `string`                 | `s`	      |
| Boolean   | `boolean`                     | `boolean`                | `b`	      |
| Date      | `java.time.Instant`           | `date`                   | `d`        |
| List      | `java.lang.Set`               | `[`_inner_`]`            | `a`_inner_ |
| Tuple     | `ca.on.oicr.gsi.shesmu.Tuple` | `{`_t1_`,`_t2_`,` ...`}` | `t` _n_ _t1_ _t2_ Where _n_ is the number of elements in the tuple. |

The ASM bytecode generation library has a class `Type` that describes JVM
types. A `Type` object can be constructed either by knowing the JVM name for a
class, or by using `Type.getType(Foo.class)` where _Foo_ is the class.

In JSON documents, Shesmu types must be converted to the more limited types
available. Again, there is a type erasure, so the Shesmu `Imyhat` type is
necessary to convert a JSON document back into an interpretable format.

| Name      | JSON Type                          |
|---        |---                                 |
| Integer   | number                             |
| String    | string                             |
| Boolean   | boolean                            |
| Date      | number of milliseconds since epoch |
| List      | array                              |
| Tuple     | array                              |

## Writing a Plugin
Each plugin can be considered separately, but a JAR file can deliver multiple
different things at once. Plugins are described in this section from least
complicated to most complicated.

### Throttler
Throttlers temporarily block actions from starting. They can block actions by
service names provided by plugins and these names are arbitrary strings. To
create a new throttler:

1. Create a class which implements the `Throttler` interface.
1. Annotate this class with `@MetaInfServices`.
1. Make sure the class has a no-argument constructor.

###  Dumpers
Dumpers write intermediate values for debugging purposes.

1. Create a class that implements `Dumper`. A dumper can be reused, but every
use will be prefaced with a call to the `start()` method. The `stop()` will be
called at the end, even if an exception occurs.
1. Write a class that implements `DumperSource`. If a dumper by the requested
name is available, return an instance wrapped by `Optional.of`, otherwise,
`Optional.empty()`.
1. Annotate this class with `@MetaInfServices`.
1. Include a no-arguments constructor for the `DumperSource` class.

Dumpers will get an array of values, one for each of the expressions provided
by the user, boxed as `Object`. They must unbox the objects appropriately for
themselves.

### Input Format
Creating a new source format is meant to be easy, but convoluted in order to be
type safe. To create a new source format:

1. Create a class, _V_, that will hold all the data for a “row” in the incoming
data. It must be a class and not an interface.
1. Create a parameterless method in _V_ for every variable to be exposed. The
method names must be valid Shemsu names (lowercase with underscores) and
decorated with `@Export` annotations with the correct type signature. All
methods must return `boolean`, `long`, `String`, `Instant`, `Set`, or `Tuple`
(and `Set` and `Tuple` may only contain more of the same).
1. Create a new interface, _R_, extending `InputRepository<`_V_`>`.
1. Create a new class that extends `BaseInputFormatDefinition<`_V_`,`_R_`>` and
provides those types to the constructor as well as a name that will be used in
the `Input` instruction. This class must be annotated with
`@MetaInfServices(InputFormatDefinition)`.
1. Create new classes that provide data which implement _R_ and are annotated
with `@MetaInfServices(`_R_`)`.

#### Static JSON Repository
This is an optional step. It allows reading input data from a live Shesmu
instance, storing it as a JSON file, then using that on another instance.

1. Create a class, _J_ that `extends BaseJsonFileRepository<`_V_`> implements `_R_.
1. Create a no-argument constructor that calls `super("`_name_`");`.
1. Annotate _J_ with `@MetaInfServices(`_R_`.class)`.
1. Implement the `convert` method to generate the correct object from the JSON blob.

### Constants
A constant is a value that can be generated with no input. It need not actually
be constant (_e.g._, `now` is a constant). The Shesmu compiler will arbitrarily
copy the value of a constant, so a constant should be side-effect free.

1. Create a class that implements `ConstantSource`.
1. Annotate this class with `@MetaInfServices`.
1. Include a no-arguments constructor for the `ConstantSource` class.
1. Return a stream of `Constant` objects for the constants to be created.

There are a number of convience methods to create constants for values. If
something more complicated is desired, extend the `Constant` class and write
arbitrary bytecode in the `load` method. The code must not change any value on
the stack and must leave exactly one value of the correct type on the stack.

### Functions
A function is a transformation of input data. It matches with a call to a
method. Since there is no user-defined error-handling, these functions should
not throw. Also, since Shesmu has no null values, they should not return null
when an error occurs.

To provide functions:

1. Create a class taht implements `FunctionRepository`.
1. Annotate this class with `@MetaInfServices`.
1. Include a no-arguments constructor for the `FunctionRepository` class.
1. Return a stream of `FunctionDefintion` objects for the functions to be created.

There are a few ways methods to generate a `FunctionDefinition`:

1. Create a class that implements `FunctionDefinition` and write arbitrary
bytecode.
1. Use `FunctionDefinition.staticMethod` to create a binding for a public
static method in a class.
1. Use `FunctionForInstance.bind` to create a binding for a method in an
instance of a class (a closure).

In all cases, the Java types for the parameters must match the Shesmu types
provided. The order of the arguments must match. If they do not match, errors
will occur during compilation of an olive.

When writing byte code, the arguments will be on the stack in order.

When binding an instance, a `MethodHandles.Lookup` object must be provided.
There are two ways to obtain one
[`publicLookup()`](https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/MethodHandles.html#publicLookup--),
which can only bind to public methods, and
[`lookup()`](https://docs.oracle.com/javase/8/docs/api/java/lang/invoke/MethodHandles.html#lookup--),
which can bind to any method that is visible from the caller.

### Actions
Actions are the most complicated. Shesmu pushes a number of questions about how
actions work onto the plugin.

Actions are created by the olives as necessary and put in a large set to be
processed. When an action is created, arbitrary data can be stored in the
action via the constructor (or methods called after construction). This allows
an action to have “secret” knowledge not provided by the olive, such as the URL
of remote service that should perform the action.

All of the data provided in the `With` block is done after the action is
created. Once all the data is loaded into the action, it is put into the set.
The set deduplicates olives based on their `hashCode` and `equals` method.
Since the same olive will regenerate the same action many, many times during
the life of a Shemu server instance, the deduplication must work properly to
decide that two actions with the “same” parameters are identical. Which
parameters must be considered for two actions to be identical is entirely
chosen by the implementer.

When the system is going to perform actions, it sorts them by priority (smaller
numbers are higher priority). If two actions are going to use the same
resource, then priority is a good way to allocate the resource to the most
appropriate action. Since new actions are being generated constantly, priority
inversion may occur. An item can also return a different priorty over its life.

At some point, an action will be given time to `perform`. There is a limited CPU pool
for actions to run in, so blocking is strongly discouraged. An action should
always start its `perform` method with:

    if (Throttler.anyOverloaded("x", "y", "z")) {
      return ActionState.THROTTLED;
    }

where _x_, _y_, and _z_ are service names this action should throttle on. These
might come from the service configuration (_e.g._, for a JIRA project _ABC_, the
JIRA ticketing action will throttle on `jira` and _ABC_).

After running, it must return an `ActionState` to indicate its current status.
If the action throws an exception, it will be caught, reported, and given
`ActionState.UNKNOWN`.

The meaning of each `ActionState` is defined in the enum's documentation. The
`perform` method will be called periodically until `SUCCEEDED` is returned. If
any other value is returned, the action will be retried later. The scheduler
will wait until at least `retryMinutes()` minutes have elapsed before trying
again.

When queried by the user, an action can return a JSON representation. This is
arbitrary and entirely for the benefit of users. Any information can be
included in an appropriate way.

To create an action:

1. Create a class _A_ that extends `Action`.
1. It must provide a unique _type_ name to the superconstructor that will be available for searching via the REST API.
1. Override all the methods for the desired behaviour as described above.
1. Create a class _AR_ that implements `ActionRepository`.
1. Annotate this class with `@MetaInfServices`.
1. Include a no-arguments constructor for the `ActionRepository` class.
1. Create a class _AD_ that extends `ActionDefinition`.
1. In the constructor of _AD_, call the superconstructor with `("_json type name_`"`, Type.getType(`_A_`.class)`, Stream.of(`_parameters_`);`
1. In _AD_, override `initialize` to generate bytecode to create a new instance of the class. Details are provided below.
1. If _A_ requires a step to prepare it for use after all the `With` arguments are set, override `finalize` in _AD_ to generate the bytecode to perform this step.
1. In _AD_, provide all the `With` arguments this action requires in the _parameters_ stream. Details are provided below
1. In _AR_, return a stream of _AD_.

#### Constructors
Calling a constructor is straight forward if all the parameter to the constructors are primitive types and strings. If this is the case, it can be done with the following code:

    // Create an ASM type for a String
    private static final Type A_STRING_TYPE = Type.getType(String.class);
    // Create an ASM type for the action to be built
    private static final Type MY_ACTION_TYPE = Type.getType(MyAction.class);

		// Create a method definition for a constructor that takes a string and a
		// long. Note that the name of the method is "<init>" and the return type
		// is void.

    private static final Method CTOR = new Method("<init>", Type.VOID_TYPE, new Type[] { A_STRING_TYPE, Type.LONG_TYPE });

    @Override
    public void initialize(GeneratorAdapter methodGen) {
      methodGen.newInstance(MY_ACTION_TYPE); // Create a new instance of MyAction and put a reference on the stack
      methodGen.dup(); // Make a duplicate reference to be consumed by the constructor
      methodGen.push("hello"); // Push the first parameter (a string) onto the stack
      methodGen.push(3L); // Push the second parameter (a long) onto the stack
      methodGen.invokeConstructor(MY_ACTION_TYPE, CTOR); // Invoke the constructor consume a reference to MyAction, a string, and a long
      // Leave one reference to the new action on the stack
    }

Keep the constructor as simple as possible. Avoid passing unnecessarily
complicated arguments when possible. If unsure of the right bytecode, create a
new class with a method that returns a new instance in the way you require,
then use `javap -c -p com.example.Test` to see the bytecode generated by the
Java compiler and copy it.

#### Action Parameters
An action needs to take some data from the Shesmu olive. To do this, there are three methods:

1. Put data in a public field using `ParameterDefinition.forField`.
1. Put data in a JSON object stored in a field using `new JsonParameter`. _A_ must extend `JsonParameterised`.
1. Write bytecode to set the parameter.
