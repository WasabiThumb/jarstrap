from shutil import move
from pathlib import Path
from typing import TextIO, BinaryIO
from buildSrc.args import Args
import re
import struct

HEX_CHARS = "0123456789ABCDEF"


class SourcePatcher:
    file: str
    dest: str
    read: TextIO
    write: TextIO

    def __init__(self, file: str):
        self.file = file + ".bak"
        move(file, self.file)
        self.dest = file
        Path(file).touch()

        self.read = open(self.file, "r")
        self.write = open(self.dest, "w")

    def run(self):
        active = False
        for line in self.read.readlines():
            if active:
                if self.is_deactivator_line(line):
                    active = False
            if active:
                new_line = self.transform_line(line)
                if new_line is not None:
                    self.write.writelines([new_line])
                continue
            else:
                if self.is_activator_line(line):
                    active = True
            self.write.writelines([line])
            if active:
                self.on_activated()

    def close(self):
        self.read.close()
        self.write.close()
        Path(self.file).unlink()

    def is_activator_line(self, line: str) -> bool:
        return False

    def is_deactivator_line(self, line: str) -> bool:
        return True

    def transform_line(self, line: str) -> str | None:
        return None

    def on_activated(self):
        pass


class ConfigSourcePatcher(SourcePatcher):
    args: Args

    def __init__(self, file: str, args: Args):
        super().__init__(file)
        self.args = args

    def is_activator_line(self, line: str) -> bool:
        return "// CONFIG START" in line

    def is_deactivator_line(self, line: str) -> bool:
        return "// CONFIG END" in line

    def transform_line(self, line: str) -> str | None:
        if not line.startswith("static const"):
            return line

        line_search = re.search("^(char|unsigned int)\\s([^\\s]+)\\s=\\s([^;]+);", line[13:])
        if not line_search:
            return line

        line_type = line_search.group(1)
        line_name = line_search.group(2)

        match line_name:
            case "APP_NAME[]":
                v = self.args.app_name
            case "MIN_JAVA_VERSION":
                v = self.args.min_java_ver
            case "PREFERRED_JAVA_VERSION":
                v = self.args.preferred_java_ver
            case "LAUNCH_FLAGS[]":
                v = self.args.launch_flags
            case _:
                return line

        match line_type:
            case "char":
                v = f"\"{v}\""
            case "unsigned int":
                v = str(v)

        return f"static const {line_type} {line_name} = {v};\n"


class ArchiveSourcePatcher(SourcePatcher):
    archive: BinaryIO

    def __init__(self, file: str, archive: str):
        super().__init__(file)
        self.archive = open(archive, 'rb')

    def is_activator_line(self, line: str) -> bool:
        return "// ARCHIVE DATA START" in line

    def is_deactivator_line(self, line: str) -> bool:
        return "// ARCHIVE DATA END" in line

    def transform_line(self, line: str) -> str | None:
        return None

    def on_activated(self):
        self.write.write('static const unsigned char ARCHIVE_DATA[] = {\n')
        on_line = 0
        while byte := self.archive.read(1):
            if on_line == 16:
                self.write.write("\n")
                on_line = 0
            if on_line == 0:
                self.write.write("       ")
            self.write.write(" 0x")
            value = struct.unpack('B', byte[0:1])[0]
            self.write.write(HEX_CHARS[value >> 4])
            self.write.write(HEX_CHARS[value & 0xF])
            self.write.write(",")
            on_line += 1
        if on_line != 0:
            self.write.write('\n')
        self.write.write('};\n')

    def close(self):
        super().close()
        self.archive.close()
