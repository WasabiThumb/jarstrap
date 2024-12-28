# JarStrap
A Java Archive to executable tool (JAR to EXE / JAR to ELF)
for Windows & Linux ``x86/64``. Made in Java 17 with support for Java 5+ binaries. Requires some
[common dependencies for building the C stub](#dependencies). **This was previously a Python
project! For the old code, go [here](https://github.com/WasabiThumb/jarstrap/tree/python).**

## Features
- Fast to build & run
- Attempts to find or install a suitable Java version at runtime
  - Installs OpenJDK
- Adds command-line QoL
  - Negotiates color support in unfriendly terminals
  - Sets the terminal title to the name of the app

## Using
### Gradle (Kotlin)
```kotlin
dependencies {
    implementation("io.github.wasabithumb:jarstrap:0.2.0")
}
```

### Gradle (Groovy)
```groovy
dependencies {
    implementation 'io.github.wasabithumb:jarstrap:0.2.0'
}
```

### Maven
```xml
<dependencies>
  <dependency>
    <groupId>io.github.wasabithumb</groupId>
    <artifactId>jarstrap</artifactId>
    <version>0.2.0</version>
    <scope>compile</scope>
  </dependency>
</dependencies>
```

## API
The entry point of this package is ``JARStrap.createPackager()``.
To build an executable, set the desired options on the ``Packager`` and then use ``execute()`` to run all the
[stages](#stages) in turn.

```java
File out;
try (Packager p = JARStrap.createPackager()) {
    p.setAppName("Your Awesome App");
    p.setSource(new File("your_awesome_app.jar"));
    p.setMinJavaVersion(8);
    p.setPreferredJavaVersion(21);
    p.setRelease(true);
    p.execute();
    out = p.getOutputFile();
}
```

## Stages
- ``init``
  - Populates the working directory
- ``inject``
  - Creates a symlink or copy of the source JAR, to be read by the make process
- ``manifest``
  - Reads the [manifest](https://docs.oracle.com/javase/tutorial/deployment/jar/manifestindex.html) of the source JAR and re-writes it if necessary.
    Ensures that it is runnable and reads the [major version](https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html#jvms-4.1-200-B.2) of the main class.
    This stage will catch many broken configurations that would not produce useful executables.
- ``vars``
  - Updates constants in ``main.c`` to reflect the packager's configuration
- ``mingw`` (Windows)
  - Locates or installs the MinGW toolchain
- ``cmake``
  - Uses CMake to generate a Makefile
- ``make``
  - Uses Make (GNU Make or MinGW Make) to build the Makefile
- ``export``
  - Copies the built executable to the configured output location

## Dependencies
### Linux Host
- CMake
  - **Debian/Ubuntu**: ``sudo apt install cmake``
  - **Arch**: ``sudo pacman -S cmake``
- GNU Make
  - **Debian/Ubuntu**: ``sudo apt install build-essential``
  - **Arch**: ``sudo pacman -S base-devel``

### Windows Host
- CMake
  - **MSI**: https://cmake.org/download/
  - **Chocolatey**: ``choco install cmake``
- MinGW
  - **Automatic**: Use ``setAutoInstall(true)`` on the ``Packager`` to install MinGW to a known location. You should not
    set it back to ``false`` after this point.
  - **Chocolatey**: ``choco install mingw``
    - ⚠️ Seems to break for building 32-bit apps on 64-bit hosts
  
## License
```text
Copyright 2024 Wasabi Codes

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```