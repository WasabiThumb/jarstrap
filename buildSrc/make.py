import os
from enum import Enum
from pathlib import Path
import subprocess
from shutil import which, move


class MakeType(Enum):
    RELEASE = 1
    DEBUG = 2


class MakeArch(Enum):
    AMD64 = 1
    I386 = 2


class MakeHandler:
    id: str
    dir: str
    make_type: MakeType
    make_arch: MakeArch

    def __init__(self, home: str, make_type: MakeType, make_arch: MakeArch):
        self.id = f"cmake-build-{make_type.name.lower()}{'32' if make_arch == MakeArch.I386 else ''}"
        self.dir = os.path.join(home, self.id)
        Path(self.dir).mkdir(exist_ok=True)
        self.make_type = make_type
        self.make_arch = make_arch
        # -DCMAKE_BUILD_TYPE=Release
        # -DCMAKE_C_FLAGS="-m32"

    def cmake_generate(self) -> bool:
        if os.name == 'nt':
            generator = "MinGW Makefiles"
        else:
            generator = "Unix Makefiles"

        args = ["cmake", "-G", generator, "..", f"-DCMAKE_BUILD_TYPE={self.make_type.name.capitalize()}"]
        if self.make_arch == MakeArch.I386:
            args.append("-DCMAKE_C_FLAGS=\"-m32\"")

        p = subprocess.Popen(args, cwd=self.dir)
        return p.wait() == 0

    def make(self) -> bool:
        tool_name = "make"
        tool = which(tool_name)
        if tool is None and os.name == 'nt':
            tool_name = "mingw32-make"
            tool = which(tool_name)
        if tool is None:
            return False
        p = subprocess.Popen(tool_name, cwd=self.dir)
        return p.wait() == 0

    def get_binary_full_name(self, name: str):
        if self.make_arch == MakeArch.I386:
            kernel = f"{name}32"
        else:
            kernel = f"{name}"
        if self.make_type == MakeType.DEBUG:
            kernel = f"{kernel}-debug"
        if os.name == 'nt':
            return f"{kernel}.exe"
        else:
            return f"{kernel}"

    def move_binary_to(self, name: str, dest: str):
        src = os.path.join(self.dir, "jarstrap.exe" if os.name == 'nt' else "jarstrap")
        dest = os.path.join(dest, self.get_binary_full_name(name))
        move(src, dest)
