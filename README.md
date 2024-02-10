# JarStrap
A Java Archive to executable tool (JAR -> EXE)
for Windows & Linux ``x86/64``.

## Features
- Fast to build & run
- Attempts to find or install a suitable Java version at runtime
  - Installs OpenJDK (not Oracle, which is proprietary)
- Recognizes Java 5+ and supports Java 8+
- Adds command-line QoL
  - Colors (pretty!)
  - Negotiates color support in unfriendly terminals
    - Can reliably use ANSI sequences in your Java application
      (see [JANSI](https://github.com/fusesource/jansi), ``systemInstall`` will not be required)
  - Sets the terminal title to the name of the app

## Usage
It's possible to build executables with this tool [manually](#manual-usage), but I
suggest using the Python CLI. Make sure you have Python 3 installed
and run ``python build.py`` or ``python3 build.py``.

The Python CLI will try to find CMake on your system, as well as
``make`` if on Linux or ``mingw32-make`` if on Windows. For MinGW
specifically, see [here](#build-deps-win-mingw).

### Manual Usage
The responsibilities of the build script are to place the archive to be
wrapped into ``archive/archive.jar``, and run ``cmake`` with the ``MinGW Makefiles``
or ``Unix Makefiles`` generator setting ``CMAKE_BUILD_TYPE`` and ``CMAKE_C_FLAGS`` appropriately in:
- ``cmake-build-release``
- ``cmake-build-release32``
- ``cmake-build-debug``
- ``cmake-build-debug32``

followed by ``make``, ``mingw32-make``, or whatever build tool is appropriate. At that point,
a binary named ``jarstrap``/``jarstrap.exe`` is produced for each target.

## Build Dependencies
### Linux
**Python 3**<br>
Install ``python3`` from your distro's package manager.
- Debian/Ubuntu: ``sudo apt install python3``
- Arch: ``sudo pacman -S python`` [*](https://wiki.archlinux.org/title/python#Other_versions)

**CMake**<br>
Install ``cmake`` from your distro's package manager.
- Debian/Ubuntu: ``sudo apt install cmake`` [*](https://cgold.readthedocs.io/en/latest/first-step/installation.html#ubuntu)
  - You may also choose to install ``cmake-qt-gui`` for a GUI
- Arch: ``sudo pacman -S cmake``
  - This includes ``cmake-gui``

**GNU Make**<br>
Install the base development kit from your distro's package manager.
You may choose to install only ``make``, but nobody will support this configuration.
- Debian/Ubuntu: ``sudo apt install build-essential``
- Arch: ``sudo pacman -S base-devel``

<br>

### Windows
**Python 3**<br>
Download [from python.org](https://www.python.org/downloads/windows/)

**CMake**<br>
Download the MSI installer at [cmake.org](https://cmake.org/download/)

**MinGW Make**<br>
<a id="build-deps-win-mingw"></a>
Download from your source of choice at [mingw-w64.org](https://www.mingw-w64.org/downloads/).
An easy option are the binaries on [this GitHub repo](https://github.com/niXman/mingw-builds-binaries/releases/latest).
If you so choose, look for a download labeled ``release-win32``, and then ``x86_64`` for 64-bit systems or ``i686`` for
32-bit systems. Extract to a location appropriate for program data (``C:`` is fine), and then add
the directory containing ``mingw32-make`` to PATH. If you are confused on how to do this,
[this article](https://www.howtogeek.com/118594/how-to-edit-your-system-path-for-easy-command-line-access/) sums it up
well enough.

## Roadmap
- Add automated modes to the CLI to easily embed JARStrap into other build tools
- Maven/Gradle plugin
- Optimize for memory
