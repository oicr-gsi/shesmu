# Built-In
Shesmu provides only a small handful of built-in services.

## Actions
These actions are available on any instance:

- `std::nothing` action: an action that collects a string parameter and does nothing. This can be useful for debugging.

## Constants
These constants are available on any instance:

- `epoch` constant: date at the zero UNIX time
- `now` constant: the current timestamp

## Functions
These functions are available on any instance:

- `std::boolean::parse` Convert a string containing into a Boolean.
- `std::date::to_millis`: get the number of milliseconds since the UNIX epoch for this date.
- `std::date::to_seconds`: get the number of seconds since the UNIX epoch for this date.
- `std::float::is_infinite`: check if a floating-point number is infinite.
- `std::float::is_nan`: check if a floating-point number is not-a-number.
- `std::float::parse` Convert a string containing digits and a decimal point into a float.
- `std::integer::parse` Convert a string containing digits into an integer.
- `std::json::array_from_dict`: convert a dictionary to an array of arrays. If a dictionary has strings for keys, it will normally be encoded as a JSON object. For other key types, it will be encoded as a JSON array of two element arrays. This function forces conversion of a dictionary with string keys to the array-of-arrays JSON encoding. Shesmu will be able to convert either back to dictionary.
- `std::json::object`: create a JSON object from fields.
- `std::json::parse` Convert a string containing JSON data into a JSON value.
- `std::path::change_prefix`: allow rewriting path prefixes to undo directory symlinking.
- `std::path::dir`: Extracts all but the last elements in a path (_i.e._, the containing directory).
- `std::path::file`: extracts the last element in a path.
- `std::path::normalize`: Normalize a path (_i.e._, remove any `./` and `../` in the path).
- `std::path::relativize`: Creates a new path of relativize one path as if in the directory of the other.
- `std::path::replace_home`: Replace any path that starts with `$HOME` or `~` with the provided home directory.
- `std::string::eq`: Compares two strings ignoring case.
- `std::string::lower`: Convert a string to lower case.
- `std::string::trim`: Remove white space from a string.
- `std::string::upper`: Convert a string to upper case.
- `std::url::decode`: Convert a URL-encoded string back to a normal string.
- `std::url::encode`: Convert a string to a URL-encoded string (also escaping `*`, even though that is not standard).
- `std::version_at_least`: Checks whether the supplied version tuple is the same or greater than version numbers provided.

Note that paths can be joined with the `+` operator and strings can be joined using interpolation (_e.g._, `"{x}{y}"`).

## Input Formats
These input formats are available on any instance:

- `shesmu` input format: information about the actions current running inside Shesmu

## Signatures
These signatures are available on any instance:

- `std::json::signature` signer: all used signable variables and their values as a JSON object
- `std::signature::sha1` signer: a SHA1 hash of all the used signable variables and their values
- `std::signature::count` signer: the number of all the used signable variables
- `std::signature::names` signer: the names of all the used signable variables

## Constants from JSON
Simple boolean, integer, strings, and sets of the former can be stored as
simple constants in JSON files. Create a JSON file ending in `.constants` as
follows:

    {
      "life_the_universe_and_everything": 42
    }

This will provide the constant `life_the_universe_and_everything` to olives.
Updating the file will update the value seen by the olives if the type is the
same.

## Fake Actions
For debugging, it's often useful to have a simulating server that has the same
actions as production, but doesn't do anything with them.

To configure this, create a file ending in `.fakeactions` as follows:

    {
      "url": "http://shesmu-prod:8081,
      "allow": ".*",
      "prefix": ""
    }

where `url` is the Shesmu server to copy and `allow` is a regular expression of
which actions to copy. An optional `prefix` can be applied to the names of all
the actions.

If the remote server is not accessible, download the `/actions` endpoint to a
file ending in `.fakeactiondefs`. This will create a similar set of fake
actions, though statically.
