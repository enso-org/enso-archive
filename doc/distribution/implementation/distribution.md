# Enso Distribution Implementation Notes

## Deeply Nested Directory Trees on Windows
Given that we use a fairly deeply nested structure for our directories,
we must take extra care to support it properly on Windows, due to the 256
characters in a path limitation.
There is no special action required as long as we're using the JVM for handling
paths, as it automatically inserts the `\\?\ ` prefix for Windows paths.