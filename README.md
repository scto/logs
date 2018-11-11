# Logs

A simble, pretty and flexible log library for android.

## Setup

Download
```groovy
repositories {
    jcenter()
}

dependencies {
    implementation 'com.github.kxfeng:logs:1.0.0'
}
```

Initialize
```kotlin
Logs.defaultConfig("MyApp", 2)
    .addPrinters(
        PrettyFormatPrinter(
            level = LogLevel.VERBOSE,
            border = true,
            threadInfo = true,
            printer = AndroidPrinter()
            
        )
    )
```

Then use
```kotlin
Logs.d("hello")
```

## Parameterized log and lambda

```kotlin
Logs.d("time: %s %s", System.currentTimeMillis(), "ms")
Logs.d("time: %s %s", Supplier { System.currentTimeMillis() }, "ms")
```

## Temporary config

```kotlin
Logs.tag("Crash").e("Crash error")
Logs.config("Crash", 10).e("Crash error")
```

## Log to file

`LogFile` is a class for writing logs to files which ensure multi-thread and multi-process safety. You can easily archive log files for uploading to your server without losing any logs which are writing concurrently.

Initialize printer
```kotlin
Logs.config("MyApp", 0)
    .addPrinters(
        PrettyFormatPrinter(
            level = LogLevel.VERBOSE,
            border = false,
            threadInfo = false,
            printer = FilePrinter(
                LogFile(
                    directory = getExternalFilesDir("logs")!!.path,
                    name = "app_log",
                    maxSize = 1024 * 1024,
                    maxCount = 5
                )
            )
        )
    )
```

Archive log files
```kotlin
//  You can use different instance for writing and archiving log.
val archivedFiles: Array<File> = LogFile(
    directory = getExternalFilesDir("logs")!!.path,
    name = "app_log",
    maxSize = 1024 * 1024,
    maxCount = 5
).archive()
```
