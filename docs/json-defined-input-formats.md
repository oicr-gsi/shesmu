# JSON-Defined Input Formats
Shesmu olives can access user-defined input formats. Normally, data is provided from Java plugins,
so the input format itself is also defined in Java. If that is the case,
consult [Plugin Implementation Guide](implementation.md) to see how to do that. If a plugin is not
required, it is possible to define an input format without Java. This format can consume input from
JSON, either using a local file or from a remote endpoint.

Unlike other Shesmu configuration files, JSON-defined input formats are read _only on startup_.
Adding a new input format will require a restart and Shesmu will scan the `SHESMU_DATA`
for `.shesmuschema` files. The format of this file is:

```
{
  "timeFormat": "ISO8660_STRING",
  "variables": {
    "x": {
      "gangs": [
        {
          "dropIfDefault": false,
          "gang": "hello",
          "order": 0
        }
      ],
      "signable": true,
      "type": "i"
    },
    "y": {
      "gangs": [
        {
          "dropIfDefault": false,
          "gang": "hello",
          "order": 1
        }
      ],
      "signable": true,
      "type": "s"
    },
    "z": {
      "gangs": [],
      "signable": false,
      "type": "as"
    }
  }
}
```

First, a `"timeFormat"` must be specified to indicate how dates will be encoded, even if no dates
are used in the format. The supported formats are:

- `MILLIS_NUMERIC` - number of milliseconds since the UNIX epoch
- `SECONDS_NUMERIC` - number of seconds since the UNIX epoch; since Shesmu allows milliseconds,
  precision is lost
- `ISO8660_STRING` - store the date as an ISO-8660 string with milliseconds

The `"variables"` property lists every variable available to the olive. Each one must specify:

- `"type"`: the type for this column using the standard Shesmu type descriptor. See [types in the
  language description](language.md#types).
- `"signable"`: a Boolean value indicating whether this field should be included in signatures or
  not
- `"gangs"`: is a list of gangs this variable belongs to. Each one specifies:
  - `"gang"`: the name of the gang
  - `"order"`: the position in the gang
  - `"dropIfDefault"`: a Boolean value indicating that if this variable is its _default_ value (0,
    empty string, epoch), then it should be omitted from the gang

Note that gang ordering is taken as relative; that is, for a gang with `x` and `y`,
specifying `"order"` to be 1 and 2, respectively, is the same as 0 and 1.

Once a _name_`.shesmuschema` file is found on startup, it will be possible to create file names
with `.`_name_`-input` that contains a JSON representation of the input format or `.`_name_`-remote`
containing a JSON object with two attributes `url` indicating where to download the JSON
representation and `ttl` indicating the number of minutes to cache the input. Additionally, once a
Shesmu server is active, it will provide the input in the JSON format at `/input/` followed by the
input format name.

The `.shesmuschema` are read exactly once during startup and will never be read
again nor will additional schema files be scanned. If a schema changes or a
new schema is introduced, the server must be restarted. The reason for this is
that the olive compiler bakes a bunch of input format data into global state,
so if it were to change, olives using the schema would break in unpredictable
ways and olives coupled to other olives through `Export Define` would have
consistency problems. Single loading was fine with the Java input formats,
because there isn't a way to add a new class after start up. Unfortunately,
JSON schemas have to be treated the same way.


