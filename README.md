# Logs

A simple, pretty and flexible log library for Android.

## Setup

Download
```groovy
repositories {
    jcenter()
}
dependencies {
    implementation 'com.github.kxfeng:logs:1.1.1'
}
```

Initialize
```kotlin
Logs.init("TAG", 0)
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
Logs.d("Hello")
Logs.e(NullPointerException(),"Error")
```

Output

![](https://github.com/kxfeng/logs/blob/master/images/logs_output.png)

## Lambda parameter

```kotlin
Logs.d("time: %s %s", Supplier { System.currentTimeMillis() }, "ms")
```

## Temporary config 

```kotlin
Logs.tmp("TAG2", 10).e("Error")
```

## Logger instance

```kotlin
val logger1 = Logs.asInstance()
val logger2 = Logs.newInstance()

logger1.d("Hello")
logger2.d("Hello")
```

## Log to file

`LogFile` is a class for writing logs to files which ensure multi-thread and multi-process safety. You can easily archive log files for uploading to your server without losing any logs which are writing concurrently.

Initialize printer
```kotlin
Logs.init("TAG", 0)
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

## License

    Copyright 2019 kxfeng

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.