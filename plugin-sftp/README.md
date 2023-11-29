# SFTP Plugin
The SFTP plugin allows Shesmu to get metadata from and check if files exist on 
a remote file system.

To configure an SFTP server, create a file ending in `.sftp` as follows:

    {
      "host": "myserver.local",
      "port": 22,
      "user": "myuser",
      "jsonSources": [],
      "listCommand": null,
      "fileRoots": [],
      "fileRootsTtl": null,
      "functions": {},
      "refillers": {}
    }

Shesmu uses passwordless public key authentication on the remote server. An
unencrypted private key must be provided in `$HOME/.ssh/id_rsa`. In this
example, from the user that Shesmu runs as, `ssh -p 22 myuser@myserver.local`
must work without any user interaction.

This will provide several functions to access the existence, size, and
modification time of remote files. It will also provide an action to create
symlinks on the remote system.

## Functions
A remote program can be used to provide functions to olives. To create one add
an entry to the `"functions"` section as follows:

    "some_function": {
      "command": "/usr/local/bin/some_function",
      "parameters": [
        "s",
        "i"
      ],
      "returns": "b",
      "ttl": 60
    }

This will provide `some_function(string, integer)` to olives. When this
function is called, it will run `/usr/local/bin/some_function` and write a JSON
array with the parameters to standard input. It will then wait to read standard
output which should contain only a JSON value (a Boolean in this case); that
is, it should write `true` or `false` to standard output.

As Shesmu will wait to read the standard output, the function should run in a
reasonable amount of time. Long-running functions can have serious performance 
implications for Shesmu.

As a fun example, if `cat` is the command used, all the arguments are returned
as a tuple:

    "to_tuple": {
      "command": "cat",
      "parameters": [
        "s",
        "i"
      ],
      "returns": "t2si"
    }

The parameter and return types are JSON-enhanced descriptors. See [types
in the language description](../language.md#types) for details.

## Refillers
A remote server can provide programs that will ingest data from a `Refill`
olive. To create one, add an entry in the `"refillers"` section as follows:

    "example": {
      "command": "/opt/refill/bin/example",
      "parameters": {
        "count": "i",
        "value": "s"
      }
    }

This will create `example` as a refiller available to olives. It will take
parameters as defined in the `"parameters"` block; the value of each parameter
is a JSON-enhanced Shesmu type descriptors (see [types in the language
description](../language.md#types) for details).  When the olive is ready,
Shesmu will compute an order-independent hash from the data. Then, over SSH,
`"command"` will be run with the hash (as a hexadecimal string) after it.

This program can then decide if the hash matches the last version it has
consumed. If so, it should print: `OK` and exit 0. If it has stale data, it
should print `UPDATE` and it will then receive a JSON array of objects
containing of all the data (in arbitrary order) via standard input.

It can then process the data and should return 0 if the processing was
successful; non-zero otherwise.

If the program exits non-zero, Shesmu will retry with the same data until
success or the data is updated.

The program should run in a reasonable amount of time. Long-running programs
will have serious performance implications for Shesmu.

As an example, this shell script read the data and places it in a file (in the
same directory):


    #!/bin/sh
    cd $(dirname $0)
    if [ -f current_hash ] && [ "${1}" = "$(cat current_hash)" ]; then
      echo OK
      exit 0
    fi
    echo UPDATE
    cat >current_data
    echo "${1}" >current_hash

A more sophisticated version of this script is provided as
`shesmu-json-refiller` if it suits your needs.

## JSON Sources
It is possible to extract data over SSH by remotely executing a command that
streams this data in JSON format to standard output. This data should be in the
same format as `/input/`_format_.

To create a source, add an object as follows in the `"jsonSources"` array:

    {
      "command": "command_to_produce_data",
      "format": "cerberus_fp",
      "ttl": 60
    }

This will run the command specified in `"command"` to generate the data. Data
will be cached for the number of minutes specified by `"ttl"`. The `"format"`
property gives the name of the format. If the name is unknown, this source will
be ignored.

## File Roots
It is possible to gather file information in the `unix_file` format from a
remote file system via SSH. `"fileRoots"` list the paths to scan.

`"fileRootsTtl"` sets the number of minutes to cache the results. If null, a
default value of 60 minutes is used.

Shesmu tries to use GNU findutils to explore the remote directory. This is
convenient because it is standard with Linux. However, if file have names which
are not allowed in JSON strings, it falls apart rather quickly. If this is the
case, use the [JSON directory listing tool](../json-dir-list) to ensure the
output is always correctly encoded. Install it on the target system and then
set `"listCommand"` to the path to the program or just `"json-dir-list"` if
it's installed into a location on the `PATH`.
