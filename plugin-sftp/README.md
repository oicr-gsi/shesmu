# SFTP Plugin
The SFTP plugin allows Shesmu to get metadata from and check if files exist on 
a remote file system.

To configure an SFTP server, create a file ending in `.sftp` as follows:

    {
      "host": "myserver.local",
      "port": 22,
      "user": "myuser"
    }

Shesmu uses passwordless public key authentication on the remote server. An
unencrypted private key must be provided in `$HOME/.ssh/id_rsa`. In this
example, from the user that Shesmu runs as, `ssh -p 22 myuser@myserver.local`
must work without any user interaction.

This will provide several functions to access the existence, size, and
modification time of remote files. It will also provide an action to create
symlinks on the remote system.
