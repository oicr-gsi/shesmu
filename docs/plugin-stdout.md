# Standard Output Logger
The Standard Output Logger plugin provides an alternative to the [Loki plugin](plugin-loki.md) 
which is useful in debugging scenarios where sending a development environment's logging output to Loki
would be too noisy. **This plugin is not meant for production use.**

To configure the Standard Output Logger, create a file ending with `.stdout` as follows:

    {
      "level": "INFO"
    }

The `"level"` property is one of:
1. FATAL
2. ERROR
3. WARN
4. INFO
5. DEBUG

All messages at or above the setting in severity will be logged to standard output. 