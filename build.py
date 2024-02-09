import os
from shutil import which, copyfile
from buildSrc.args import Args
from buildSrc.patcher import ConfigSourcePatcher, ArchiveSourcePatcher

home = os.path.dirname(os.path.abspath(__file__))


def check_preconditions():
    if which("cmake") is None:
        print("CMake is not installed!")
        exit(1)
    if os.name == 'nt':
        if which("mingw32-make") is not None:
            return
    if which("make") is None:
        print(f"{'MinGW' if os.name == 'nt' else 'GNU Make'} is not installed!")
        exit(1)


if __name__ == '__main__':
    check_preconditions()
    args = Args()
    args.load_defaults(os.path.join(home, "main.c"))
    if not args.gui():
        exit(0)
    if not args.validate():
        exit(1)
    print()

    print("Patching main.c")
    main_patcher = ConfigSourcePatcher(os.path.join(home, "main.c"), args)
    main_patcher.run()
    main_patcher.close()

    print("Patching archive")
    copyfile(args.archive, os.path.join(home, "archive", "archive.jar"), follow_symlinks=True)

    print()
    yn = input("Files patched. Use CMake to build? (Y/n) ")

    if yn.lower() == "n":
        exit(0)

    print()
    print("Building ")
