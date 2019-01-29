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
        <artifactId>shesmu-pluginapi</artifactId>
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
tutorial](https://dzone.com/articles/introduction-to-java-bytecode) is one of
the better ones.

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
| Object    | `ca.on.oicr.gsi.shesmu.Tuple` | `{`_f1_` = `_t1_`,`_f2_` = `_t2_`,` ...`}` | `o` _n_ _f1_`$`_t1_ _f2_`$`_t2_ Where _n_ is the number of elements in the object. Fields must be sorted alphabetically. |

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
| Object    | object                             |

## Writing an Input Format Plugin
Creating a new source format is meant to be easy, but convoluted in order to be
type safe. To create a new source format:

1. Create a class, _V_, that will hold all the data for a “row” in the incoming
data. It must be a class and not an interface.
1. Create a parameterless method in _V_ for every variable to be exposed. The
method names must be valid Shemsu names (lowercase with underscores) and
decorated with `@ShesmuVariable` annotations with the correct type descriptor. All
methods must return `boolean`, `long`, `String`, `Instant`, `Set`, or `Tuple`
(and `Set` and `Tuple` may only contain more of the same).
1. Create a new class that extends `InputFormat<`_V_`>` and
provides the types to the constructor as well as a name that will be used in
the `Input` instruction. This class must be annotated with
`@MetaInfServices(InputFormatDefinition)`.

For each variable, Shesmu can try to infer the type from the return type of the
method. If the type is not `boolean`, `Instant`, `long`, or `String`, it must
be specified in the `type` property of the `@ShesmuVariable` annotation in the
type descriptor format.

By default, it will be possible to create files names with `.`_name_`-input`
that contains a JSON representation of the input format or `.`_name_`-remote`
containing a JSON object with two attributes `url` indicating where to download
the JSON representation and `ttl` indicating the number of minutes to cache the
input. Additionally, once a Shesmu server is active, it will provide the input
in the JSON format at `/input/` followed by the input format name.

## Writing a Plugin
Shesmu has many different systems that can be fed by plugins. Each plugin
requires two classes, a `PluginFileType` that defines the plugin itself and
`PluginFile` that is created for each matching configuration file discovered in
the Shesmu data directories.

As a general outline, for a plugin `Foo`, the two classes would be:

```
@MetaInfServices
public class FooPluginType extends PluginFileType<FooFile> {
  public FooPluginType() {
    super(MethodHandles.lookup(), FooFile.class, ".foo");
  }

  FooFile create(Path fileName, String instanceName, Definer definer) {
    return new FooFile(fileName, instanceName, definer);
  }
}

class FooFile extends PluginFile {
  FooFile(Path fileName, String instanceName, Definer definer) {
    super(fileName, instanceName);
  }
  public void configuration(SectionRenderer renderer) {
    // TODO: provide debugging output
  }
}
```

The class extending `PluginFileType` must have a public no-arguments
constructor.

The `configuration` method is displays configuration panels on the main status
page of the Shesmu server and are meant to report the configuration of a
plugin.

All plugin integration is provided by:

- overriding methods in `PluginFileType`
- overriding methods in `PluginFile`
- adding annotated methods in `PluginFileType`
- adding annotated methods in `PluginFile`
- using the `Definer`

### Source Linker
Source linkers convert local paths for `.shesmu` files into URLs for accessing
via the action dashboard. To create a new source linker, override `sourceUrl`
in either `PluginFileType` or `PluginFile`.

### Throttler
Throttlers temporarily block actions from starting. They can block actions and
olives by service names provided by plugins and these names are arbitrary
strings. To create a new throttler, override `isOverloaded` in either
`PluginFileType` or `PluginFile`.

###  Dumpers
Dumpers write intermediate values for debugging purposes.

1. Create a class that implements `Dumper`. The `stop()` will be called at the
end, even if an exception occurs.
1. Override `findDumper` in either `PluginFileType` or `PluginFile`.

Dumpers will get an array of values, one for each of the expressions provided
by the user, boxed as `Object`. They must unbox the objects appropriately for
themselves. The `Imyhat` type information can be used for this unboxing using
the `apply` and `accept` methods.

### Constants and Functions
Olive can consume functions and constants from the outside world.

A constant is a value that can be generated with no input. It need not actually
be constant (_e.g._, `now` is a constant). The Shesmu compiler will arbitrarily
copy the value of a constant, so a constant should be side-effect free.

A function is a transformation of input data. It matches with a call to a
method. Since there is no user-defined error-handling, these functions should
not throw. Also, since Shesmu has no null values, they should not return null
when an error occurs.

There are three ways to create a constant or function:

- create a static method in `PluginFileType` decorated with `@ShesmuMethod`
- create a virtual method in `PluginFile` decorated with `@ShesmuMethod`
- use the `Definer` interface

#### Annotated Methods
In classes derived from `PluginFileType` or `PluginFile` classes, create a
method and add the `@ShesmuMethod` annotation. This method will now be exported
to Shesmu automatically. If the method takes no arguments, it will be exported
as a constant, otherwise as a function.

The method must handle Shesmu-compatible types. Shesmu will try to determine
the matching Shesmu type from the type information provided by the JVM. If it
cannot determine the correct type, it will throw an error. The correct type can
be provided using a Shesmu type descriptor in the `type` parameter of the
`@ShesmuMethod` for return types or by adding a `@ShesmuParameter` annotation
to any parameters. The `@ShesmuParameter` annotation can also provide help
text.

The name can be provided two ways: from the name of the method itself or fro
the `name` parameter of `@ShemsuMethod` annotation. If the name is associated
with a `PluginFile` class, it must contain a `$` which will be replaced with
the name of the file. For instance, if the name of the method is `$_items` and
the file name is `foo.bar`, then the constant or variable will be available to
olives as `foo_items`.

The `@ShesmuMethod` annotation also has a `description` property that will be
shown in the definition page. In the description, `{instance}` and `{file}`
will be replace with the instance name and configuration file path,
respectively.

#### Using the Definer
The `Definer` can be used to create functions and constants. It can create as
many as desired and they can be updated or erased. For details, see the
`Definer` interface. The `Definer` interface has multiple versions of the same
methods for different needs.

- some methods take `TypeGuarantee` objects that ensure matching Java and
  Shesmu types; some take Imyhat objects directly and casting is done. If the
  types are incorrect, runtime errors will occur
- constants can be defined with fixed values or with a `Supplier` that produces
  the value when necessary
- functions with a fixed number of arguments can be supplied using Java's
  `Function` and `BiFunction` interfaces
- functions with an arbitrary number of arguments can be defined using the
  `VariadicFunction` interface. All arguments are provided as an array of
  `Object`

### Actions
Actions have a complicated set of restrictions. Shesmu pushes a number of
questions about how actions work onto the plugin.

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

    if (services.isOverloaded("x", "y", "z")) {
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
1. It must provide a unique _JSON action type_ to the superconstructor that will be available for searching via the REST API and the action dashboard. (_e.g._, `jira-open-ticket`, `nothing`, `fake`)
1. Override all the methods for the desired behaviour as described above.

To deliver an action to olives using `PluginFileType` or `PluginFile`:

1. Create a static method in `PluginFileType` or a virtual method in `PluginFile` that return _A_
1. Annotate this method with `@ShesmuAction`.
1. Name this method with a Shesmu-compatible name or set the `name` property in
	 the annotation. If the name is associated with an instance, it must contain
   a `$` which will be substituted for the instance name.
1. Return a new instance of _A_ from this method.

To deliver an action to olives using a `Definer`:

1. Call the `defineAction` method. This must take _A_`.class`, a
   `Supplier<`_A_`>` and additional parameters.

It is very important to use _A_ and not `Action`, since this type information
is used to discover the properties of the action.

For details on the parameters to an action, see below.

#### Action Parameters
An action needs to take some data from the Shesmu olive. To do this, there are multiple methods:

1. Put data in a field or setter method using the `@ActionParameter` annotation.
1. Put data in a JSON object using the `@JsonParameter` annotation. _A_ must extend `JsonParameterisedAction`.
1. Put data in a JSON object using the `JsonParameter` class and a `Definer`. _A_ must extend `JsonParameterisedAction`.
1. Use the `CustomActionParameter` class and a `Definer`.

When using the `@ActionParameter` annotation, it may be applied to any field or
virtual method taking one argument and returning void. The type will be
determined from the Java type where possible. If not possible, use the `type`
attribute of the annotation to provide the Shesmu type descriptor.

When using the `@JsonParameter` action, multiple annotations can be applied to
the class. The class must extend `JsonParameterisedAction` which returns an
`ObjectNode` into which the parameters will be written. The type information
_must_ be provided in the annotation.

Both of these methods created a fixed number of parameters for an action. If
the parameters cannot be determined ahead of time, then the `Definer` provides
a way to connect an arbitrary set of parameters to an action. When using the
definer, any parameters defined by the annotations are also used.

To define a parameter, the `CustomActionParameter` class provides a method to
write the parameter into the action. `JsonParameter` is an implementation that
writes parameters back as JSON values if the action extends
`JsonParameterisedAction`.

### Signature Variables
Signature variables are special variables that compute some kind of record
based on the input variable used by an olive.

There are two categories of signature variables: ones that are static (_i.e._,
the same for all inputs) and ones that vary for each input.

This might seem a contradiction, but the static case is useful for things that
depend only on the names and/or types of the signable variables. This is how
the `signable_names` works.

To create a signature variable:

1. Create a class that extends `StaticSigner` or `DynamicSigner`.
1. Implement the `addVariable` method to compute a value. If static, no input is
	 provided. If varying for each record, the input will be the only argument to
   the method.
1. Implement the `finish` method to produce a value.

Signatures can be made available to olives by:

1. Adding a static method decorated with `@ShesmuSigner` to `PluguinFileType`
   which returns `StaticSigner` or `DynamicSigner`.
1. Adding a virtual method decorated with `@ShesmuSigner` to `PluguinFile`
   which returns `StaticSigner` or `DynamicSigner`.
1. Use `Definer.defineStaticSigner` with a `Supplier` that returns new static
   signers.
1. Use `Definer.defineDynamicSigner` with a `Supplier` that returns new dynamic
   signers.

When using the `@ShesmuSigner` attribute, the Shesmu type descriptor must be
provided in the `type` parameter for the type that is returned with the
`finish()` method is called. The name of the signature will be taken from the
method name or it can be provided using the `name` parameter of the annotation.
If the method is attached to `PluginFile`, the name must contain a `$` which
will be replaced by the instance name.

### Input Sources
Plugins may provide data for each input format. A data format must already
exist. Suppose the type of the format is _T_. To add a source of data:

1. Create a static method in `PluginFileType` or a virtual method in
   `PluginFile`. It must take no arguments and it must return `Stream<`_T_`>`.
1. Add the `@ShesmuInputSource` to this method.

The method may also return a subclass of _T_, but not a wildcard (_e.g._,
`Stream<? extends `_T_`>`).
