# Shesmu APIs

## Remote Action API
This is the API used to connect to a remote server to get a list of actions and launch them.

### Fetch Catalog
- Method: GET
- Endpoint: `/actioncatalog`

Get a list of actions that can be performed by this server.

#### Success
- Response: 200
- Body: JSON array of ActionDefinition

### Launch Action
- Method: POST
- Endpoint: `/launchaction`
- Body: JSON of ActionRequest

Causes an action to be launched. The status code indicates whether the job has
successfully finished running. If completed successfully, an optional URL may
be provided where the result may be viewed.

#### Competed and Successful
- Response: 200
- Body: JSON of ActionResult or empty object

The action has been completed and the results are available

#### Not Complete
- Response: 202
- Body: JSON of empty object

The action has been started but is still in progress

#### Parameter Error
- Response: 400
- Body: JSON of empty object

The names of the parameters or the types do not match.

#### Action Failed
- Response: 500
- Body: JSON of ActionResult or empty object

The action failed to complete. If a URL provided, it is assumed to have some
debugging output.

#### Overload
- Response: 503
- Body: JSON of empty object

The server has a backlog of requests and cannot queue the action for execution.

## Cached Remote Action API
This allows using actions from a remote server while storing the catalog offline.

The file should contain a FileDefinition. The `url` property is the URL at
which the remote service should exist.

## Data Types

### ActionDefinition

    {
      name: String,
      parameters: {
        [parameterName]: Shesmu signature string
      }
    }
### ActionRequest

    {
      name: String,
      arguments: {
        [parameterName]: value
      }
    }

The types of the values must the types in the corresponding ActionDefinition.

### ActionResult

    {
      url: String
    }

### FileDefinition

    {
      definitions: array of ActionDefinition,
      url: String
    }
