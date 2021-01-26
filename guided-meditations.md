# Guided Meditations

Once you have a production facility, stuff goes sideways and since Shesmu can
be integrated with other systems, it can be a tool to help diagnose problems.
The _guided meditations_ feature allows developing debugging procedures where
Shesmu can integrate data it has from different sources to help humans figure
out what's going on. It works a bit like a mechanics flow chart: asking
questions and suggesting things to check. To make this process easier, it can
also pull the information it recommends the user analyse using action searches,
alert searches, downloadable files, and simulations.

Each meditation can serve a different purpose and is stored in a `.meditation`
file. Once available on the server, they will appear under _Tools_, _Guided
Meditations_.

A meditation begins with information to be displayed followed by a next step.
Meditations are written in a modified form of the [olive
language](language.md), so all the expressions available to olives are usable
in meditations. Unfortunately, meditations run in the browser, so they cannot
make use of functions and constants from the plugins or olive files. The
standard functions and constants (_i.e._, `std::`...) are available.

## Displays
There are several displays supported.

### Text
Text can be displayed to the user. _expr_ is an expression that provides a
string containing text. There are additional formatting options.

- _expr_
- `Bold` _expr_
- `Italic` _expr_
- `Mono` _expr_
- `Strike` _expr_

Hyperlinks can also be displayed:

- `Link` _labelexpr_ `To` _urlexpr_

<a name="actiondisp">
### Action Searches
The actions currently present in Shesmu can be searched using the advanced
action search language. For details, see the _Actions_ page in _Advanced_ mode.
A search for actions might look something like:

    Actions type = "sftp-symlink"

The action queries can also include olive expressions using `{}`:

    Actions type = "sftp-symlink" and tag = {active_projects}

This only applies to values, not the variables. For example, this is not allowed:

    Actions {If something Then "type" Else "tag} = "whatever"

Lists versus single items are more forgiving than in the regular action
queries. For instance, the following is allowed:

    Actions status = {
      If std::date::now - start_time < 3hours
        Then [WAITING, THROTTLED]
        Else [THROTTLED]}
      and tag in (research, {project})

### Alert Searches
The alerts currently present in Shesmu can also be searched. There is no alert
search language, so it will be described below:

    Alerts project = * & LIVE

The supported operations are:
- _X_ `|` _Y_
Require either _X_ or _Y_ be true

- _X_ `&` _Y_
Require both _X_ and _Y_ be true

- `!` _X_
Invert the value of _X_ (_i.e._, if _X_ is true, then false)

- `[` _L_ `] = *`
Check that an alert has a label _L_ associated with it. If it is present with
any value, including the empty string, the it will match. _L_ may be a valid
Shesmu identifier, any string quoted, or an expression surrounded by `{}`.

- `[` _L_ `] =` _V_
Check that an alert has a label _L_ and its value is equal to _V_. _L_ and _V_
may be a valid Shesmu identifier, any string quoted, or an expression
surrounded by `{}`.

- `[` _L_ `] ~ /`_pattern_`/`
Check that an alert has a label _L_ and its value matches the regular
expression _pattern_ . If it is present with any value, including the empty
string, the it will match. _L_ may be a valid
Shesmu identifier, any string quoted, or an expression surrounded by `{}`.

- `LIVE`
Check that an alert is currently firing.

### File Downloads
A meditation can provide prepared text to download to feed into another
program:

- `Download` _contents_ `To` _filename_ [`MimeType` _mimetype_]

This will create a button to download _contents_ as a file with the name
_filename_ and the MIME type _mimetype_. Both  _filename_ and _mimetype_ must
be strings and _contents_ can be a string or JSON value. If the MIME type is
omitted, `text/plain` will be used for strings and `application/json` will be
used for JSON values.

### Repeat Information
Any of these can be repeated over a collection:

- `Repeat` _name_ `In` _source_`:` [ _transforms_ ]\* `Begin` _display_ `End`

The _source_ and _transforms_ are as exactly as `For` expressions. Rather than
collect into a list, the display elements between `Begin` and `End` will be
displayed multiple times.

<a name="simulation">
### Simulations
To gather more information about the state of Shesmu, it is also possible to
run a simulation from a script, just as with the _Olive Simulator_, and to
rerun an existing olive script present on the server. The meditation is capable
of passing values into the simulation.


- `Simulate` [`Let` _n1_ `=` _v1_`,` ...] `Existing "` _path_ `"`
This simulates an existing script on the server with the complete path to the
script provided. This might seem useless since the script's information is
already on the _Olives_ page. However, passing in parameters makes it possible
to change the way this script operates. For instance, suppose the simulation is
set as:

    Simulate Let run = run_name Existing "/srv/shesmu/bcl2fastq.shesmu"

That script could be modified to use the constant, shielded by `IfDefined`, in
a clause like this:

    Where IfDefined shesmu::simulated::run
       Then shesmu::simulated::run == run_name
       Else True

In normal operation, `shesmu::simulated::run` will not be defined and this
clause will collapse into `Where True`. However, when invoked by the simulation
request, it allows the extra condition to be activated and filter the results
in a more interesting way.

- `Simulate` [`Let` _n1_ `=` _v1_`,` ...]  [`Refiller` _name_ `=` _type_`,` ... `As` _refiller_]  _script_
This simulates an entirely new script, just as in the olive simulator. Again,
values can be provided using the `Let` and will be available in the
`shesmu::simulated::` namespace. Additionally, refillers can be defined to
allow outputting data in custom formats. A fake refiller is defined by listing
the parameters the refiller requires and the types of those parameters. For
example, a refiller could be defined as:

    Refiller
      run = string,
      lane = integer,
      barcode = string,
      library_name = string As libraries

and then used in the script:

    Olive
      ...
      Refiller libraries With
        {run, lane, barcode} = ius,
        library_name = library_name;

Any code that can be normally written in an olive script is allowed and the
script will have full access to functions and constants available on the
server.

The script will be checked as part of the meditation's compilation, so values
injected via `Let` will be checked for correctness in the script.

### Table Display
Any of these can be repeated over a collection:

- `Table` _name_ `In` _source_`:` [ _transforms_ ]\* `Column` _header_ _values_ ...

The _source_ and _transforms_ are as exactly as `For` expressions. Rather than
collect into a list, the items will be placed in rows in a table. Each column
starts with a string _header_ and then the text display information to fill
that cell in _contents_.


## Next Steps
As the meditation is run, the user can select the next step along the way and
this will determine the behaviour of the rest of the meditation.

### User-Interactive
- `Stop`
Ends the meditation. No further steps follow

- `Choice` `When "` _description_ `" Then`  _step_, ...
Allows the user to choose between several options. _step_ is a list of displays followed by another step.

- `Form` _entry1_[`,` _entry2_ ...] `Then` _step_
Allows collecting information from the user and then proceed to the next step
with that information available. _step_ is a list of displays followed by
another step. For details on the form entires, see the next section.

#### Form Entires
To collect data from the user, a form entry will display a prompt that is stored in a variable.

- _name_ `=` _type_ `Label` _labelexpr_
Creates an input box of some kind. The _type_ will determine both the UI input
widget and the output type. The output will be assigned to _name_ in subsequent
steps. _labelexpr_ is text display elements to show to the left of the input
widget.

| Type       | Display                           | Variable Type             |
|------------|-----------------------------------|---------------------------|
| `Text`     | a free-form text input box        | `string`                  |
| `Number`   | a number spinner box              | `integer`                 |
| `Offset`   | a number box + time unit selector | `integer` as milliseconds |
| `Checkbox` | a check box                       | `boolean`                 |

- _name_ `= Select` _optionvalue_ `As` _optionlabel_ ... `Label` _labelexpr_
Creates a drop down list. The _optionlabel_ is display text that will be shown.
There is no restriction on the type of _optionvalue_, though they all must be
the same. The selected value will be assigned to _name_ in subsequent steps.
_labelexpr_ is text display elements to show to the left of the input widget.

- _name_ `= Subset` _values_ `Label` _labelexpr_
Allows selecting a subset of items. A list of strings must be provided in
_values_ and the ones selected by the user will be assigned to _names_, also as
a list of strings.  _labelexpr_ is text display elements to show to the left of
the input widget.

### Automatic Flow Control
If there is information provided that can be used to determine what decision to
make next, the `Flow By Switch` and `Flow By Match` steps allow selecting it.
They are structured similarly to `Switch` and `Match` expressions. These cannot
be preceded by information to display.

- `Flow By If` _testexpr_ `Then` _truestep_ `Else` _falsestep_

Computes the value _testexpr_, which must be a Boolean, and, if it is true,
_truestep_ will be performed; otherwise, _falsestep_ is performed.

- `Flow By Match` _refexpr_ (`When` _algmatch_ `Then` _step_)\* (`Else` _altstep_  `Remainder (`_name_`)` _altstep_)?

Computes the algebraic value returned by _refexpr_ and access its contents.  A
`When` branch can be provided for every possible algebraic type returned by
_refexpr_. If all possible types are matched, the matching is _exhaustive_. If
the matching is not exhaustive, the remaining cases can be
access to the case being handled.

For details on algebraic type matching, see [Algebraic Values without
Algebra](algebraicguide.md).

- `Flow By Switch` _refexpr_ (`When` _testexpr_ `Then` _step_)\* `Else` _altstep_

Computes the value _refexpr_ and if it is equal to any _textexpr_, the matching
_step_ will be performed. If no value match, _altstep_ is performed instead.

- `Fork` _name_ `In` _source_`:` [ _transforms_ ]\* `Title` _title_ _step_

Splits the journey into several parallel journeys.  _source_ and _transforms_
are as exactly as `For` expressions. Each resulting item will be split into a
new path handled by _step_. They will be labelled to avoid confusion, using
_title_, which must be a string.

### Gathering Server Data
The information blocks can display information from the server, but this
information isn't available to make decision. The `Fetch` element allows
gathering data from the server to make decisions.

- `Fetch` _data1_[`,` _data2_ ...] `Then` _step_

Each of the items described in _data_ is fetched from the server and then is
available for decision making.

#### Server Data Collection Methods
The following sources of data can be collected from the server:

- _name_ `= ActionCount` _query_

Counts the number of actions that match the provided query (see [Actions
Searches](#actiondisp) for details on the query format). The resulting count
will be available as an integer stored in _name_ .

- _name_ `= ActionIdentifiers` _query_

Copies all the identifiers of actions that match the provided query (see
[Actions Searches](#actiondisp) for details on the query format). The resulting
identifiers will be available as a list of strings stored in _name_ .

- _name_ `= ActionTags` _query_

Copies all the tags of actions that match the provided query (see [Actions
Searches](#actiondisp) for details on the query format). The resulting tags
will be available as a list of strings stored in _name_ .

- _name_ `= Olive Input`  _format_ _clauses_

This runs an olive and collects the output of that olive into a list. This uses
the _Olive Simulator_ and so has access to all functions and constants on the
server. The values defined are passed in to the simulation in the
`shesmu::simulated::` namespace and the this namespace is imported, same as the
[`Simulate`](#simulations) information display (_i.e._, if you have previous
set `Entry ... To foo`, then you can use `foo` and/or `shesmu::simulated::foo`
in this olive). _format_ specifies the input format that should be used
(normally in the `Input` line of an olive script). A list of clauses can be
specified. The data from the last clause will be converted into a list of
objects and stored in the variable _name_.

**This operation can download large amounts of data to the client.** If used
directly, it could download the entire dataset into the browser and the browser
may not appreciate that. There are three important things to do:

- Restrict the number of records using filter (_i.e._, use plenty of `Where`, `Reject`, and `Require` clauses to take only the required rows)
- Restrict the number of variables required (_i.e._, have a `Let` clause that limits the variables to only the ones necessary)
- Remove duplicate rows. Normally, Shesmu handles duplicates gracefully, but implementation details here make duplicate rows more of a problem. If selecting a small number of variables that will be mostly duplicated, use a `Group By` to ensure that duplicates are collapsed.

     "Peer in the file system!"
     Form
       owner = Text Label "What user are you interested in?"
     Then
       Fetch
         files = Olive
           Input unix_file
             Where user == owner Let file, size
       Then
         Repeat {; file,  size} In files:
           Begin Bold "{file}" " ({size}) " End
       Stop

### Define and Go-to
It can be useful to reuse steps in different contents. At the beginning of a
file, a reuable step can be defined:

- `Define` _name_ `(` _type1_ _name1_`,` ... `)` _step_ `;`

This creates a definition for a step that can then be referenced elsewhere as:

- `GoTo` _name_ `(` _expr1_`,` ...`)`

Only previously declared steps are available, so they must be declared in
dependency order and cycles are not permitted.

## An Example

There is a functional, though somewhat useless meditation:


     "Hello. How do you feel?"

     Choice
       When "I am tried" Stop
       When "I am confused"
          Actions type = "sftp-symlink"
          Stop
       When "I'm looking for something more conventional"
         Form
           Entry text name Label "What is your name?"
         Then
           Download  "Hello, {name}! Does this meet your expectations"
             To "example.txt" MimeType "text/plain"
           Stop
       When "I yearn for knowledge"
         Simulate
           Refiller type = string, count = integer As action_stats
             Version 1;
             Input shesmu;
             Olive
               Group By type Into count = Count
               Refill action_stats With type = type, count = count;

         Stop
