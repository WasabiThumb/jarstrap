import os
from shutil import which, copyfile
from buildSrc.args import Args
from buildSrc.make import MakeHandler, MakeArch, MakeType
from buildSrc.patcher import ConfigSourcePatcher, ArchiveSourcePatcher
import struct
from pathlib import Path
import glob

home = os.path.dirname(os.path.abspath(__file__))


def check_preconditions():
    if which("cmake") is None:
        print("CMake is not installed/on PATH!")
        exit(1)
    if os.name == 'nt':
        if which("mingw32-make") is not None:
            return
    if which("make") is None:
        print(f"{'MinGW' if os.name == 'nt' else 'GNU Make'} is not installed/on PATH!")
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

    handlers = list()
    if struct.calcsize("P") == 8:
        handlers.append(MakeHandler(home, MakeType.RELEASE, MakeArch.AMD64))
        if args.debug:
            handlers.append(MakeHandler(home, MakeType.DEBUG, MakeArch.AMD64))
    else:
        print("32-bit system detected, skipping 64-bit targets")

    if args.build_i386:
        handlers.append(MakeHandler(home, MakeType.RELEASE, MakeArch.I386))
        if args.debug:
            handlers.append(MakeHandler(home, MakeType.DEBUG, MakeArch.I386))

    success = set()
    for h in handlers:
        print()
        print(f"Building {os.path.basename(h.id)}")
        print(f"Generating make files...")
        if not h.cmake_generate():
            print(f"Failed to generate make files for {h.id}")
            continue
        print(f"Making...")
        if not h.make():
            print(f"Failed to make {os.path.basename(h.id)}")
            continue
        success.add(h.id)

    os.remove(os.path.join(home, "archive", "archive.jar"))
    succeeded = len(success)
    total = len(handlers)

    print()
    print(f"Build Complete ({succeeded}/{total} succeeded)")
    for h in handlers:
        print(f"[{'✓' if h.id in success else 'x'}] {h.id}")
    if succeeded < total:
        exit(1)
    if succeeded == 0:
        exit(0)

    print()
    yn = input("Move binaries to an output directory (y/N)? ")
    if yn.lower() not in ['yes', 'y', 'true']:
        exit(0)

    out_dir = input("Output directory (out): ")
    if len(out_dir) == 0 or out_dir.isspace():
        out_dir = "out"
    out_dir = Path(out_dir)
    out_dir.mkdir(parents=True, exist_ok=True)
    out_dir = str(out_dir.absolute())
    for f in glob.glob(os.path.join(out_dir, '*')):
        print(f"Cleaning old file {os.path.basename(f)}")
        os.remove(f)

    bin_name_default = args.app_name.lower().replace(" ", "_")
    bin_name = input(f"Binary name ({bin_name_default}): ")
    if len(bin_name) == 0 or bin_name.isspace():
        bin_name = bin_name_default

    for h in handlers:
        print(f"Moved {h.get_binary_full_name(bin_name)} to output directory")
        h.move_binary_to(bin_name, out_dir)
