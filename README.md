# JarStrap
A Java Archive to executable tool (JAR -> EXE)
for Windows & Linux ``x86/64``.

## Features
- Fast to build & run
- Attempts to find or install a suitable Java version at runtime
  - Installs OpenJDK
- Recognizes Java 5+ and supports Java 8+
- Adds command-line QoL
  - Colors (pretty!)
  - Negotiates color support in unfriendly terminals
    - Can reliably use ANSI sequences in your Java application
      (see [JANSI](https://github.com/fusesource/jansi), ``systemInstall`` will not be required)
  - Sets the terminal title to the name of the app
