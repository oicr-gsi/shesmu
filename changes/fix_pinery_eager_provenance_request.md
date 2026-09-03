Pinery requested sample provenance before it was ready to read it
* both arguments to `Stream.concat` are evaluated before either is read, so the sample provenance request was issued while lane provenance was still being consumed, leaving a response body unread and its connection idle
* the request is now deferred until the lane provenance has been read
