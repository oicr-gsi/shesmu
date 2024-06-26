# Implementing Plugins for Shesmu

Shesmu does not provide much out-of-the-box. Every environment is going to be
very different, so Shesmu is designed to be very modular. If the available
plugins are suitable, great. If not, it was designed to be easy to extend.

## General Plugin Infrastructure
Shesmu uses
[`ServiceLoader`](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html)
to find plugins. For service loaders to work, a plugin should extend the
service interface, be included in a `provides` line in the `module-info` for
the service interface, and placed into a JAR on the module path. The Java
libraries will take care of the rest.

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
    </dependencies>

Shesmu provides all monitoring output via
[Prometheus](https://github.com/prometheus/client_java). All the infrastructure
is wired in, so simply building instruments and using `.register()` will add
them to the monitoring output.

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
The class
[`AutoUpdatingDirectory`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/files/AutoUpdatingDirectory.html)
provides a mechanism to scan for new configuration files in the Shesmu data
directory and update the files when they change on disk.

## Types and Erasure
The correct handling of types in Shesmu is complicated. There are different but
interlocking type systems:

1. The Java type system
1. The JVM type system
1. The Shesmu type system
1. The JSON type system

Shesmu's type system describe the types of every type that can be used in an
olive. Because the name `Type` is used by the ASM library, the library used by
Shesmu to generate JVM bytecode, Shesmu's types are represented by `Imyhat`
objects (this is an Ancient Egyptian word for _mould of conduct_).

Java's type system is similar to, but more sophisticated than, the JVM type
system. In particular, generic types in Java are [erased on the
JVM](https://docs.oracle.com/javase/tutorial/java/generics/index.html).

Shesmu's types are also erased in a way that is similar to Java's. This matters
for Shesmu's lists, which are Java's `Set` underneath. Shesmu's tuples are
erased to `Object` values. Although some of these JVM types may be null, Shesmu
olives cannot handle null values. If you need nullable values, use
`java.util.Optional`. `Optional` can be empty, but not `null`.

| Name       | JVM Type                                   | Syntax                   | Signature  |
|---         |---                                         |---                       |---         |
| Integer    | `long` / `J`                               | `integer`                | `i`        |
| Float      | `double` / `D`                             | `float`                  | `f`        |
| String     | `java.lang.String`                         | `string`                 | `s`        |
| Boolean    | `boolean` / `z`                            | `boolean`                | `b`        |
| Date       | `java.time.Instant`                        | `date`                   | `d`        |
| JSON       | `com.fasterxml.jackson.databind.JsonNode`  | `json`                   | `j`        |
| List       | `java.lang.Set`                            | `[`_inner_`]`            | `a`_inner_ |
| Empty List | `java.lang.Set`                            | `[]`                     | `A`        |
| Tuple      | `ca.on.oicr.gsi.shesmu.Tuple`              | `{`_t1_`,`_t2_`,` ...`}` | `t` _n_ _t1_ _t2_ Where _n_ is the number of elements in the tuple. |
| Object     | `ca.on.oicr.gsi.shesmu.Tuple`              | `{`_f1_` = `_t1_`,`_f2_` = `_t2_`,` ...`}` | `o` _n_ _f1_`$`_t1_ _f2_`$`_t2_ Where _n_ is the number of elements in the object. Fields must be sorted alphabetically. |
| Algebraic  | `ca.on.oicr.gsi.shesmu.AlgebraicValue`     | _NAME_ | `u1`_NAME_`$t0` |
| Algebraic  | `ca.on.oicr.gsi.shesmu.AlgebraicValue`     | _NAME_ `{`_t1_`,`_t2_`,` ...`}` | `u1`_NAME_`$t` _n_ _t1_ _t2_ Where _n_ is the number of elements in the tuple. |
| Algebraic  | `ca.on.oicr.gsi.shesmu.AlgebraicValue`     | _NAME_ `{`_f1_` = `_t1_`,`_f2_` = `_t2_`,` ...`}` | `u1`_NAME_`$o` _n_ _f1_`$`_t1_ _f2_`$`_t2_ Where _n_ is the number of elements in the object. Fields must be sorted alphabetically. |
| Optional   | `java.util.Optional`                       | _inner_`?`               | `q`_inner_ |
| Optional   | `java.util.Optional`                       | `nothing`                | `Q`        |
| Dict       | `java.util.Map`                            | _k_` -> `_v_             | `m` _k_ _v_ |

If you require a type as part of your configuration, `Imyhat` can be serialised
and unserialised by Jackson with JSON-enhanced descriptors. See [types in the
language description](language.md#types).

<a name="json"></a>
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
| Algebraic | object; see [Algebraic Values without Algebra](algebraicguide.md) |
| Dict      | object (if key is a string) or array of arrays containing key-value pairs |

## Writing an Input Format Plugin
Creating a new source format is meant to be easy, but convoluted in order to be
type safe. To create a new source format:

1. Create a class, _V_, that will hold all the data for a “row” in the incoming
data. It must be a class and not an interface.
1. Create a parameterless method in _V_ for every variable to be exposed. The
method names must be valid Shesmu names (lowercase with underscores) and
decorated with
[`@ShesmuVariable`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/input/ShesmuVariable.html)
annotations with the correct type descriptor. All methods must return
`boolean`, `long`, `String`, `Instant`, `Set`, or `Tuple` (and `Set` and
`Tuple` may only contain more of the same).
1. Create a new _F_ class that extends
[`InputFormat<`_V_`>`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/input/InputFormat.html)
and provides the types to the constructor as well as a name that will be used
in the `Input` instruction.
1. In `module-info` add `provides InputFormat with `_F_`;`.

For each variable, Shesmu can try to infer the type from the return type of the
method. If the type is `Tuple` or an erased `Optional`, `Map`, or `Set`, it
must be specified in the `type` property of the `@ShesmuVariable` annotation in
the type descriptor format.

By default, it will be possible to create files names with `.`_name_`-input`
that contains a JSON representation of the input format or `.`_name_`-remote`
containing a JSON object with two attributes `url` indicating where to download
the JSON representation and `ttl` indicating the number of minutes to cache the
input. Additionally, once a Shesmu server is active, it will provide the input
in the JSON format at `/input/` followed by the input format name.

### Variable Gangs
Variables in an input format can be attached to _gang_ to provide
convenient grouping criteria. Please see the [language reference](language.md)
for the purpose and uses of gangs. In the
[`@ShesmuVariable`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/input/ShesmuVariable.html),
set:

    gangs = { @Gang(name = "useful_stuff", order = 0) })

Since a variable can be part of multiple groups, `@Gang` can be
specified multiple times. All variables using the same _name_ will be bound to
the same group. When converting the group to a string or tuple, the _order_
determines the order for this variable. For example:

    @ShesmuVariable(gangs = { @Gang(name = "group_a", order = 0) }))
    public String foo();
    @ShesmuVariable(gangs = {
        @Gang(name = "group_a", order = 1),
        @Gang(name = "group_b", order = 0)
      }))
    public String bar();
    @ShesmuVariable(gangs = { @Gang(name = "group_b", order = 1) }))
    public String baz();

In this example `{@group_a}` would be equivalent to `{foo, bar}` and
`{group_b}` would be equivalent to `{bar, baz}`. In `By` clauses, the order is
irrelevant.

## Writing a Grouper Plugin
Creating a new grouper is thorny since a lot of generic types are required. The
implementation-specific grouping logic is well-isolated from the rest of the
system. Effectively, the grouper is a service parameterised over two generic
type variables: _I_, the type of the input rows, and _O_, the type of the
output rows. These are opaque to the grouper and Shesmu will fill in the gaps
using the olive. The grouper may request that the olive provide data, of any
Shesmu-compatible type, and export extra values to the olive.

Groupers can take parameters from the olive and provide output variables. Both
of these can be fixed (the same across all rows) or dynamic (a function that
takes an input row as a parameter). There are a number of overrides and
super-constructors to handle different numbers of input and output variables.
The exact configuration will depend on what information the grouper requires.
It may be easiest to implement the grouper and see what information is necessary
and then work backward to the grouper definition.

1. Create a class _G_ that implements
   [`Grouper`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/grouper/Grouper.html)
   parameterised over _I_ and _O_.
1. Create a class _D_ that extends
   [`GrouperDefinition`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/grouper/GrouperDefinition.html)
   and is exposed with `provides GrouperDefinition with `_G_`;` in `module-info`.
1. In _D_, call the appropriate super constructor. They vary by the number of
   input parameters the grouper requires. Use
   [`GrouperParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/grouper/GrouperParameter.html)
   to fill in each parameter required.
1. In _D_, select a
   [`GrouperOutputs`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/grouper/GrouperOutputs.html)
   to use. This sets the number of variables exported to the olive for each
   group.
1. Create a constructor that fits in the pattern of input and outputs in _G_.
1. Perform the grouping operation and create a
   [`Subgroup`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/grouper/Subgroup.html)
   for each subgroup.

Note that a grouper will be reused arbitrarily many times by an olive, so do
not store any state in fields.

## Writing a Plugin
Shesmu has many different systems that can be fed by plugins. Each plugin
requires two classes, a
[`PluginFileType`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/PluginFileType.html)
that defines the plugin itself and
[`PluginFile`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/PluginFile.html)
that is created for each matching configuration file discovered in the Shesmu
data directories.

As a general outline, for a plugin `Foo`, the two classes would be:

```
public final class FooPluginType extends PluginFileType<FooFile> {
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

A number of plugin features can be added to the `PluginFileType` or the
`PluginFile`. Anything placed in the `PluginFileType` will be a global
definition. When Shesmu administrator creates configuration file, Shesmu will
spawn an instance of `PluginFile` to read that file. Any annotated methods
attached the `PluginFile` will be created per-instance. The name of the
configuration file will be included in the definition if it is created
per-instance.

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

1. Create a class that implements
   [`Dumper`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/dumper/Dumper.html).
   The `stop()` will be called at the end, even if an exception occurs.
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
method and add the
[`@ShesmuMethod`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/functions/ShesmuMethod.html)
annotation. This method will now be exported to Shesmu automatically. If the
method takes no arguments, it will be exported as a constant, otherwise as a
function.

The method must handle Shesmu-compatible types. Shesmu will try to determine
the matching Shesmu type from the type information provided by the JVM. If it
cannot determine the correct type, it will throw an error. The correct type can
be provided using a Shesmu type descriptor in the `type` parameter of the
`@ShesmuMethod` for return types or by adding a
[`@ShesmuParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/functions/ShesmuParameter.html)
annotation to any parameters. The `@ShesmuParameter` annotation can also
provide help text.

The name can be provided two ways: from the name of the method itself or from
the `name` parameter of `@ShesmuMethod` annotation. If the name is associated
with a `PluginFile` class, it must contain a `$` which will be replaced with
the name of the file. For instance, if the name of the method is `$_items` and
the file name is `foo.bar`, then the constant or variable will be available to
olives as `foo_items`.

The `@ShesmuMethod` annotation also has a `description` property that will be
shown in the definition page. In the description, `{instance}` and `{file}`
will be replaced with the instance name and configuration file path,
respectively.

#### Using the Definer
The
[`Definer`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/Definer.html)
can be used to create functions and constants. It can create as many as desired
and they can be updated or erased. For details, see the `Definer` interface.
The `Definer` interface has multiple versions of the same methods for different
needs.

- some methods take
  [`TypeGuarantee`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/types/TypeGuarantee.html)
  objects that ensure matching Java and Shesmu types; some take Imyhat objects
  directly and casting is done. If the types are incorrect, runtime errors will
  occur
- constants can be defined with fixed values or with a `Supplier` that produces
  the value when necessary
- functions with a fixed number of arguments can be supplied using Java's
  `Function` and `BiFunction` interfaces
- functions with an arbitrary number of arguments can be defined using the
  [`VariadicFunction`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/functions/VariadicFunction.html)
  interface. All arguments are provided as an array of `Object`

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
The set deduplicates actions from olives based on their `hashCode` and `equals`
method.  Since the same olive will regenerate the same action many, many times
during the life of a Shesmu server instance, the deduplication must work
properly to decide that two actions with the “same” parameters are identical.
Which parameters must be considered for two actions to be identical is entirely
chosen by the implementer.

When the system is going to perform actions, it sorts them by how long it has 
been since the
[`Action`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/Action.html)
was last checked, an `Action`'s `priority` (smaller numbers are higher 
priority), and prioritizes `Action`s in certain `ActionState`s. If two actions
are going to use the same
resource, then priority is a good way to allocate the resource to the most
appropriate action. Since new actions are being generated constantly, priority
inversion may occur. An item can also return a different priority over its
life.
A plugin may choose to expose `priority` as an
[`@ActionParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/ActionParameter.html)
so it can be set procedurally by an olive. (See [Action
Parameters](#action-parameters) for details.)

At some point, an action will be given time to `perform`. There is a limited CPU pool
for actions to run in, so blocking is strongly discouraged. An action should
always start its `perform` method with:

    if (services.isOverloaded("x", "y", "z")) {
      return ActionState.THROTTLED;
    }

where _x_, _y_, and _z_ are service names this action should throttle on. These
might come from the service configuration (_e.g._, for a JIRA project _ABC_, the
JIRA ticketing action will throttle on `jira` and _ABC_).

After running, it must return an
[`ActionState`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/ActionState.html)
to indicate its current status.  If the action throws an exception, it will be
caught, reported, and given `ActionState.UNKNOWN`.

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
1. Annotate this method with [`@ShesmuAction`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/ShesmuAction.html).
1. Name this method with a Shesmu-compatible name or set the `name` property in
	 the annotation. If the name is associated with an instance, it must contain
   a `$` which will be substituted for the instance name.
1. Return a new instance of _A_ from this method.

To deliver an action to olives using a `Definer`:

1. Call the `defineAction` method. This must take _A_`.class`, a
   `Supplier<`_A_`>` and additional parameters.

It is very important to use _A_ and not `Action`, since this type information
is used to discover the properties of the action.

For details on the parameters to an action, see [Action
Parameters](#action-parameters) section.

These two methods are a bit different in how they operate:

- `@ShesmuAction` will create one instance per plugin configuration (or one globally, if on `PluginFileType`)
- `Definer` will dynamically create as many action definitions as requested

For instance, the SFTP plugin allows creating file deletion commands. There's
only one way to delete a file and each plugin is connected to one remote
server, so it is in a method that creates one action on `PluginType`, resulting
in one action definition per instance.

On the other side, the Vidarr plugin will scan the available workflows on the
Vidarr server listed in its configuration and create one action definition for
each workflow version. The `Definer` allows creating custom parameters, so in
the case of creating actions for Vidarr workflow, some parameters are baked
into the Vidarr submit action, but many are also dynamically created from the
information provided by the Vidarr server.

A plugin can freely create both kinds of actions. In fact, the Vidarr plugin
has fixed unload actions and dynamically creates actions for workflows.

Typically, dynamically created actions need extra information, so the `Definer`
can capture extra information needed by the action's constructor as part of the
`Supplier`.

<a id="action-parameters"></a>
#### Action Parameters
An action needs to take some data from the Shesmu olive. Since the number of
parameters an action might require can be very large, they are not passed to
the constructor. Instead, Shesmu will create a new action instance, populate it
with data from the olive (in an arbitrary order), and then send the action on
to the scheduler. This works a bit like Jackson deserialization or Hibernate
mapping, where an empty object is created and then populated from the data
being loaded. There are multiple methods to import data from an olive:

1. Put data in a field or setter method using the `@ActionParameter` annotation.
1. Put data in a JSON object using the [`@JsonParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/json/JsonParameter.html) annotation. _A_ must extend [`JsonParameterisedAction`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/JsonParameterisedAction.html).
1. Put data in a JSON object using the [`JsonActionParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/JsonActionParameter.html) class and a `Definer`. _A_ must extend [`JsonParameterisedAction`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/JsonParameterisedAction.html).
1. Use the [`CustomActionParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/action/CustomActionParameter.html) class and a `Definer`.

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

### Refill
`Refill` olives write the output to an external store. This is similar to a
dumper, but the dumper must accept any data format given to it, while the
refiller gets to decide the schema, much like an action. Refillers are designed
to allow an olive to upsert or overwrite data in a tabular database. The
parameters provide the column values and the nature of olives will deliver a
list of entries.

To create a refiller:

1. Create a class _F_`<T>` that extends
   [`Refiller<T>`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/refill/Refiller.html).
   `T` is the input row type and will be used throughout.
1. Decide on the schema and set up parameters to collect the readers.
1. Implement the `consume` method that uses the readers to consume the data in
   the provided input stream.

When an olive uses the refiller, a new instance of the refiller will be
created. Just as olives populate actions with parameter values, actions
populate refillers with parameter functions and a stream of values. The
functions can be used to extract the appropriate columns on each incoming
stream. Any olive has a different internal data format, so the refiller cannot
know the types of these values; it must deal with arbitrary types and the olive
will provide it with the functions it requires to manipulate that type.

To deliver a database to olives using `PluginFileType` or `PluginFile`:

1. Create a static method in `PluginFileType` or a virtual method in `PluginFile`, parameterized by `<T>`, that return _F_`<T>`. The returned type is used to discover parameters, so it must be the correct subtype.
1. Annotate this method with [`@ShesmuRefill`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/refill/ShesmuRefill.html).
1. Name this method with a Shesmu-compatible name or set the `name` property in
	 the annotation.
1. Return a new instance of _F_ from this method.

To deliver a database to olives using a `Definer`:

1. Call the `defineRefiller` method. This must take an implementation of [`RefillerDefiner`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/Definer.RefillDefiner.html) which returns an implementation of [`RefillerInfo`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/Definer.RefillInfo.html). These interfaces are there to ensure type safety.
1. The `RefillerInfo` interface returns _F_`.class`, which is used to discover parameters, so it must be the correct subtype.
1. The `RefillerInfo` interface must return stream of non-annotated parameters.

#### Refiller Parameters and Readers
A refiller will receive an object for each row from the olive. To extract the
column values for each row, a `Function<T,`_C_`>` or reader is supplied by the
olive. _C_ is the type determined by the refiller, but `T` is the type of the
row and determined by the olive. The refiller implementation may make no
assumptions about `T`.  There are multiple ways to accept readers:

1. Create a public field of type `Function<T,`_C_`>` annotated with
   [`@RefillerParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/refill/RefillerParameter.html).
   The name of the field will be used unless `name` is set in the annotation.
1. Create a public method that takes a single argument of type
   `Function<T,`_C_`>` annotated with `@RefillerParameter`. The name of the method
   will be used unless `name` is set in the annotation.
1. Create a subclass of
   [`CustomRefillerParameter`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/refill/CustomRefillerParameter.html)
   that defines the name and type of the parameter and provides a mechanism to
   store it in the database.

When using `@RefillerParameter`, Shesmu will attempt to determine the correct
type from the annotation; raw types (_i.e._ `Function`) are not allowed. `T`
must be the first parameter type to `Function`. If the Shesmu type of _C_
cannot be determined automatically, the `type` property of the annotation must
be set.

### Signature Variables
Signature variables are special variables that compute some kind of record
based on the input variable used by an olive.

There are two categories of signature variables: ones that are static (_i.e._,
the same for all inputs) and ones that vary for each input.

This might seem a contradiction, but the static case is useful for things that
depend only on the names and/or types of the signable variables. This is how
the `signable_names` works.

To create a signature variable:

1. Create a class that extends [`StaticSigner`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/signature/StaticSigner.html) or [`DynamicSigner`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/signature/DynamicSigner.html).
1. Implement the `addVariable` method to compute a value. If static, no input is
	 provided. If varying for each record, the input will be the only argument to
   the method.
1. Implement the `finish` method to produce a value.

Signatures can be made available to olives by:

1. Adding a static method decorated with [`@ShesmuSigner`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/signature/ShesmuSigner.html) to `PluguinFileType`
   which returns `StaticSigner` or `DynamicSigner`.
1. Adding a virtual method decorated with `@ShesmuSigner` to `PluginFile`
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

### Input Sources (Direct)
Plugins may provide data for each input format. A data format must already
exist. Suppose the type of the format is _T_. To add a source of data:

1. Create a static method in `PluginFileType` or a virtual method in
   `PluginFile`. It must take no arguments or one `boolean` argument and it
   must return `Stream<`_T_`>`.
1. Add the [`@ShesmuInputSource`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/input/ShesmuInputSource.html) to this method.

The method may also return a subclass of _T_, but not a wildcard (_e.g._,
`Stream<? extends `_T_`>`).

The Shesmu server will call the `@ShesmuInputSource` in different situations,
including at the start of olive execution, and when a simulator request
is made. Different situations have different needs with regards to how 
fresh the input data should be.
The `boolean` argument, if included, is sent by the Shesmu server to 
indicate to the plugin whether 
the fetch should trigger a refresh. If true, the server has indicated that
it needs fresh data. If false, the server is happy to 
receive data cached from a previous fetch and the input source is 
free to not make the effort to refresh it. 

Input source data is not cached by the server between calls to the 
`@ShesmuInputSource` method. Caching data is the plugin's responsibility.
The Shesmu plugin API package provides `ValueCache` and implementations
for assisting with this task.
 

### Input Sources (JSON)
Shesmu will automatically create a JSON representation for every input format
that can be accessed via `/input/`_format_. Since Shesmu already knows how to
demarshall data in this format, it is possible to provide data as a stream of
bytes and leave Shesmu to extract the data from the JSON representation.

There are two ways to do this:

- using a method on a `PluginFile` or `PluginFileType`
- using `Definer.defineSource`

Ensuring the stream contains correctly encoded data is left to the plugin.
Corrupt data will be discarded.

#### Streaming JSON Using a Method
To provide a stream of JSON data:

1. Create a static method in `PluginFileType` or a virtual method in
   `PluginFile`. It must take no arguments and it must return
   `java.io.InputStream` or a subtype. It may throw any exceptions.
1. Add the
   [`@ShesmuJsonInputSource`](javadoc/ca.on.oicr.gsi.shesmu/ca/on/oicr/gsi/shesmu/plugin/input/JsonInputSource.html)
   to this method. Set `format` to the name of the format, and, optionally,
   `ttl` to adjust the cache time (in minutes).

#### Stream JSON Using a Definer
Using a definer will allow registering sources of JSON data dynamically. Many
sources of the same data type can be sent and Shesmu will concatenate all of
them.

To add a source, call `Definer.defineSource(`_n_`, `_t_`, `_s_`)` where `_n_`
is the name of the format, (_e.g._, `"cerberus_fp"`), and _t_ is the TTL for
the cache in minutes and _s_ is a function which provides a
`java.io.InputStream`. It is permitted to throw.

Sources can be removed using `Definer.clearSources(`_n_`)` to remove any
associated with a particular format or `Definer.clearSources()` to clear all
formats.
