# Mongo DB Plugin
The Mongo DB plugin allows Shesmu to get perform pre-defined queries on a Mongo
DB instance.

To configure a Mongo server, create a file ending in `.mongodb` as follows:

    {
      "uri": "mongodb://...",
      "functions": {}
    }

Shesmu will establish a connection to the remote server and then export some
functions to the olives. There are two kinds of functions: `find` and
`aggregate`.

## Find Functions
Here is an example `find` function:

    "historic_lane_lims_keys": {
      "collection": "lanes",
      "criteria": {
        "laneProvenanceId": {
          "$$parameter": 1
        },
        "provider": {
          "$$parameter": 0
        },
        "version": {
          "$$parameter": 2
        }
      },
      "database": "provenance",
      "description": "Find any lanes LIMS attributes for a particular version",
      "operations": [
        {
          "comparator": {
            "lastModified": -1
          },
          "type": "sort"
        },
        {
          "projection": {
            "laneProvenanceId": false,
            "lm": false,
            "provider": false,
            "version": false
          },
          "type": "projection"
        }
      ],
      "parameters": [
        "string",
        "string",
        "string"
      ],
      "resultType": "keyvalue",
      "selector": "FIRST",
      "ttl": 86400,
      "type": "find"
    }

The `database` and `collection` specify where to search for the data. The
initial matching requirements on the `find` operation are specified in
`criteria`. Additional transformation can be specified using the `operations`
list. Each operation has a type and another value to specify the parameters in
the usual Mongo format:

| Operator (`type`) | Parameters | Description |
|--- |--- |--- |
| `"filter"` | `filter` | A JSON structure to filter on. |
| `"limit"` | `limit` | A fixed number to limit the record count. |
| `"max"` | `comparator` | A JSON structure to sort on. |
| `"min"` | `comparator` | A JSON structure to sort on. |
| `"projection"` | `projection` | A JSON structure to project on. |
| `"skip"` | `skip` | A fixed number of records to skip. |
| `"sort"` | `comparator` | A JSON structure to sort on. |

Any JSON structure will be included as per usual Mongo semantics. The olive
will need to provide parameters to the query. The `parameters` specifies the
type of each parameter. These can be the inserted into the JSON structures
using `{"$$parameter": `_x_`}` where _x_ is the zero-based parameter index. No
type checking is done on the query. See details about parameter types below.

Finally, the query will be performed and converted using `resultType`. The
conversion is described below. Mongo will return, potentially, a collection of
results. Therefore, two `selector` options are available:

| Selector | Behaviour |
|--- |--- |
| `FIRST` | Choose the first result. An optional version of `resultType` will be used in case no results are available. |
| `ANY` | Collect all results into a set. |

All returned data is cached for `ttl` minutes.

## Aggregate Functions
Here is an example `aggregate` function:

    "things": {
      "database": "provenance",
      "description": "Find any lanes LIMS attributes for a particular version",
      "operations": [
        {
          "$match": {
            "id": { "$$parameter": 0 }
          }
        }
      ],
      "parameters": [
        "string"
      ],
      "resultType": "keyvalue",
      "selector": "FIRST",
      "ttl": 86400,
      "type": "find"
    }

The `database` specifies where to search for the data. The condition on the
`aggregate` operation are specified in `operations`.

Any JSON structure will be included as per usual Mongo semantics. The olive
will need to provide parameters to the query. The `parameters` specifies the
type of each parameter. These can be the inserted into the JSON structures
using `{"$$parameter": `_x_`}` where _x_ is the zero-based parameter index. No
type checking is done on the query. See details about parameter types below.

Finally, the query will be performed and converted using `resultType`. The
conversion is described below. Mongo will return, potentially, a collection of
results. Therefore, two `selector` options are available:

| Selector | Behaviour |
|--- |--- |
| `FIRST` | Choose the first result. An optional version of `resultType` will be used in case no results are available. |
| `ANY` | Collect all results into a set. |

All returned data is cached for `ttl` minutes.

## Parameter Types
Parameters from Shesmu will be converted to Mongo's BSON format.

The following formats can be converted easily:

* `boolean`
* `date`
* `float`
* `integer`
* `path` (written as a string)
* `string`

There are also a few complex conversions.

A list can be made as follows:

    {
       "is": "list",
       "of": ...
    }

An optional type can be made as follows:

    {
       "is": "optional",
       "of": ...
    }

If the optional is empty, `null` will be sent to Mongo.

An object type can be made as follows:

    {
       "is": "object",
       "of": {
         "field_name1:": ...,
         "field_name2:": ...,
         "field_nameN:": ...
       }
    }

## Return Types
Return types specify what kind of data will be returned from Mongo and how to
convert it to a format Shesmu can return to the olive. Mongo has certain rules
that mean that not all types can be top-level.

The following formats are converted easily:

* `boolean`
* `date`
* `float`
* `integer`
* `path` (converted from a string)
* `string`

None of these can be top level.

A list can be made as follows:

    {
       "is": "list",
       "of": ...
    }

The type inside a list must be top-level. The list itself is not top level.

An optional type can be made as follows:

    {
       "is": "optional",
       "of": ...
    }

If `null` is returned in any field not marked as optional, an exception will
occur. This type cannot be top level.

An object type can be made as follows:

    {
       "is": "object",
       "of": {
         "field_name1:": ...,
         "field_name2:": ...,
         "field_nameN:": ...
       }
    }

An object type can be top-level.

An unwrap type works a bit like an object with only one field and discards the
intermediate object. It is made as follows:

    {
       "is": "unwrap",
       "name": "fieldname",
       "of": ...
    }

A unwrap can be top-level.

The special type `"keyvalue"` will convert an object to a list of key-value
tuples. The object's values are converted to strings in a Mongo-defined format.
A key-value type can be top-level.
