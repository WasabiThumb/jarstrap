import os
from dataclasses import dataclass
import re
import struct

recognized_c_vars = ["APP_NAME[]", "MIN_JAVA_VERSION", "PREFERRED_JAVA_VERSION", "LAUNCH_FLAGS[]"]


@dataclass
class Args:
    """Holds parameters"""
    app_name: str = "JARStrap"
    archive: str = "archive/sample.jar"
    min_java_ver: int = 8
    preferred_java_ver: int = 17
    launch_flags: str = ""
    debug: bool = False
    build_i386: bool = True

    def load_defaults(self, code_file):
        in_cfg = False
        if os.name == 'nt':
            # TODO: Fix x86 build
            self.build_i386 = struct.calcsize("P") == 4
        with open(code_file, 'r') as f:
            for line in f.readlines():
                if in_cfg:
                    if "CONFIG END" in line:
                        break
                    if line.startswith("static const"):
                        self.load_defaults0(line[13:])
                elif "CONFIG START" in line:
                    in_cfg = True
                    continue

    def load_defaults0(self, line: str):
        line_search = re.search("^(char|unsigned int)\\s([^\\s]+)\\s=\\s([^;]+);", line)
        if not line_search:
            return
        line_type = line_search.group(1)
        line_name = line_search.group(2)
        line_value = line_search.group(3)
        if line_name not in recognized_c_vars:
            return
        match line_type:
            case 'char':
                line_value = line_value[1:-1]
            case 'unsigned int':
                line_value = int(line_value)
        match line_name:
            case "APP_NAME[]":
                self.app_name = line_value
            case "MIN_JAVA_VERSION":
                self.min_java_ver = line_value
            case "PREFERRED_JAVA_VERSION":
                self.preferred_java_ver = line_value
            case "LAUNCH_FLAGS[]":
                self.launch_flags = line_value

    def validate(self) -> bool:
        if self.preferred_java_ver < self.min_java_ver:
            print(
                f"Preferred Java Version ({self.preferred_java_ver}) is less than Minimum Java Version (${self.min_java_ver})")
            return False
        if self.min_java_ver < 5:
            print(f"Minimum Java Version ({self.min_java_ver}) is less than 5")
            return False
        if self.preferred_java_ver < 8:
            print(f"Preferred Java Version (${self.preferred_java_ver}) is less than 8")
            return False
        self.archive = os.path.abspath(self.archive)
        if not os.path.isfile(self.archive):
            print("JAR File is not a file!")
            return False
        return True

    def print_help(self):
        print()
        print(f"[1] App Name ({self.app_name})")
        print(f"[2] JAR File ({'None' if self.archive is None else self.archive})")
        print(f"[3] Minimum Java Version ({self.min_java_ver})")
        print(f"[4] Preferred Java Version ({self.preferred_java_ver})")
        print(f"[5] Launch Flags ({'None' if len(self.launch_flags) < 1 else self.launch_flags})")
        print(f"[6] Debug ({'true' if self.debug else 'false'})")
        print(f"[7] Build 32-Bit ({'true' if self.build_i386 else 'false'})")
        print()
        print("[b] Build")
        print("[x] Exit")
        print()

    def set_value_indexed(self, index: int, value: str):
        match index:
            case 1:
                self.app_name = value
                print(f"App Name set to {value}")
            case 2:
                self.archive = value
                print(f"JAR File set to {value}")
            case 3:
                try:
                    self.min_java_ver = int(value)
                    print(f"Minimum Java Version set to {value}")
                except ValueError:
                    print("Not a Number!")
            case 4:
                try:
                    self.preferred_java_ver = int(value)
                    print(f"Preferred Java Version set to {value}")
                except ValueError:
                    print("Not a Number!")
            case 5:
                self.launch_flags = value
                print(f"Launch Flags set to {value}")
            case 6:
                self.debug = value.lower() in ['true', '1', 't', 'y']
                print(f"Debug set to {self.debug}")
            case 7:
                self.build_i386 = value.lower() in ['true', '1', 't', 'y']
                print(f"Build 32-Bit set to {self.build_i386}")
            case _:
                print("Unknown option code (must be 1-6)")

    def gui(self) -> bool:
        self.print_help()
        opt = input("Choose an option: ").lower()

        if opt == 'b':
            return True
        elif opt == 'x':
            return False
        else:
            try:
                n = int(opt)
            except ValueError:
                print("Not a Number!")
                return self.gui()
            v = input("Enter a new value: ")
            self.set_value_indexed(n, v)
            return self.gui()
