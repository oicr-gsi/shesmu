# SFTP Plugin
The SFTP plugin allows Shesmu to get metadata from and check if files exist on 
a remote file system.

To configure an SFTP server, create a file ending in `.sftp` as follows:

    {
      "host": "myserver.local",
      "port": 22,
      "user": "myuser",
      "refillers": {}
    }

Shesmu uses passwordless public key authentication on the remote server. An
unencrypted private key must be provided in `$HOME/.ssh/id_rsa`. In this
example, from the user that Shesmu runs as, `ssh -p 22 myuser@myserver.local`
must work without any user interaction.

This will provide several functions to access the existence, size, and
modification time of remote files. It will also provide an action to create
symlinks on the remote system.

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
is a Shesmu type descriptor. When the olive is ready, Shesmu will compute an
order-independent hash from the data. Then, over SSH, `"command"` will be run
with the hash (as a hexadecimal string) after it.

This program can then decide if the hash matches the last version it has
consumed. If so, it should print: `OK` and exit 0. If it has stale data, it
should print `UPDATE` and it will then receive a JSON array of objects
containing of all the data (in arbitrary order) via standard input.

It can then process the data and should return 0 if the processing was
successful; non-zero otherwise.

If the program exits non-zero, Shesmu will retry with the same data until
success or the data is updated.

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
