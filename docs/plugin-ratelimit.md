# Rate Limit Throttling Plugin
This plugin allows limiting the rate of contacting a service using a [token
bucket](https://en.wikipedia.org/wiki/Token_bucket). To create a bucket that
throttles _service_, create a file _service_`.ratelimit` as follows:

    {
      "capacity": 1000,
      "delay": 50
    }

where `capacity` is the maximum number of tokens that can be held in the bucket
and `delay` is the number of milliseconds to generate a new token.
