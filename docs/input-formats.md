# Configuring Input Formats
An _input format_ is the type of data that Shesmu olives process--that is, the
variables that are available to Shesmu programs. The actual data comes from a
matching _input data repository_ and many repositories can provide the same format.

Plugins will define the input format and may provide special configuration to read it.
 Additionally, Shesmu provides two standard ways to access this data in JSON format:

- JSON files
- JSON URLs

For every input format, Shesmu will serve all the data it knows on the URL
`/input/`_format_. It will be provided as an array of objects, where the keys
of the objects are the names of the variables and the values are a standard
conversion scheme described in [the plugin implementation
guide](implementation.md) or [JSON-Defined Input
Formats](json-defined-input-formats.md).

To provide a set of fixed data, create a JSON file ending in
`.`_format_`-input` containing this array of objects. This can be copied from a
running Shesmu instance at `/input/`_format_.

To access data remotely, create a file ending in `.`_format_`-remote` as follows:

    {
       "authentication": null,
       "url": "http://some.url/format/endpoint",
       "ttl": 10,
       "timeout": 20
    }

where `url` is the URL to download the data, `ttl` is the number of minutes
to cache the data for, and `timeout` is the number of minutes to permit the download to continue. 
A `timeout` of `-1` will cause requests to download indefinitely. 
If no authentication is required, `null` can be used for
`"authentication"`. Alternatively, it can be one of the following
authentication methods.

## Basic Authentication
Basic authentication sends a user name and password to the remote server. The
password can be stored in the configuration file like this:

    {
       "authentication": {
         "type": "basic",
         "username": "jrhacker",
         "password": "s3cr3t"
       },
       "url": "http://some.url/format/endpoint",
       "ttl": 10
    }

or it can be stored separately using `basic-file`:

    {
       "authentication": {
         "type": "basic-file",
         "username": "jrhacker",
         "passwordFile": "/home/shesmu/secret-password"
       },
       "url": "http://some.url/format/endpoint",
       "ttl": 10
    }


## Bearer Authentication
Bearer authentication sends a single token to the remote server. The
token can be stored in the configuration file like this:

    {
       "authentication": {
         "type": "bearer",
         "token": "01234567890ABCDEF"
       },
       "url": "http://some.url/format/endpoint",
       "ttl": 10
    }

or it can be stored separately using `bearer-file`:

    {
       "authentication": {
         "type": "bearer-file",
         "tokenFile": "/home/shesmu/secret-token"
       },
       "url": "http://some.url/format/endpoint",
       "ttl": 10
    }

## API Key Authentication
API key authentication sends a single token to the remote server. The
token can be stored in the configuration file like this:

    {
       "authentication": {
         "type": "apikey",
         "tokenFile": "01234567890ABCDEF"
       },
       "url": "http://some.url/format/endpoint",
       "ttl": 10
    }

or it can be stored separately using `apikey-file`:

    {
       "authentication": {
         "type": "apikey-file",
         "tokenFile": "/home/shesmu/secret-token"
       },
       "url": "http://some.url/format/endpoint",
       "ttl": 10
    }
