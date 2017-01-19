#
# Copyright 2015-2016 IBM Corporation
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

import sys
import subprocess
import codecs
import json
sys.path.append('../actionProxy')
from actionproxy import ActionRunner, main, setRunner

SRC_EPILOGUE_FILE = "./epilogue.swift"
DEST_SCRIPT_FILE = "/swift3Action/spm-build/main.swift"
DEST_SCRIPT_DIR = "/swift3Action/spm-build"
DEST_BIN_FILE = "/swift3Action/spm-build/.build/release/Action"

BUILD_PROCESS = [ "./swiftbuildandlink.sh"]

class Swift3Runner(ActionRunner):

    def __init__(self):
        sys.stdout.write("swiftrunner.init: calling action runner init()")
        ActionRunner.__init__(self, DEST_SCRIPT_FILE, DEST_BIN_FILE)
        sys.stdout.write("swiftrunner.init: action runner init() returned, exiting init")

    def epilogue(self, fp, init_message):
        sys.stdout.write("swiftrunner.epilogue: creating main function by appending epilogue.swift")
        if "main" in init_message:
            sys.stdout.write("swiftrunner.epilogue: main in init message")
            main_function = init_message["main"]
        else:
            sys.stdout.write("swiftrunner.epilogue: main not in init message")
            main_function = "main"

        sys.stdout.write("swiftrunner.epilogue: writing epilogue file")
        with codecs.open(SRC_EPILOGUE_FILE, "r", "utf-8") as ep:
            fp.write(ep.read())

        sys.stdout.write("swiftrunner.epilogue: appending main function")
        fp.write("_run_main(mainFunction:%s)\n" % main_function)
        sys.stdout.write("swiftrunner.epilogue: donem exiting epilogue")

    def build(self):
        sys.stdout.write("swiftrunner.build: calling subprocess to build action")
        p = subprocess.Popen(BUILD_PROCESS, cwd=DEST_SCRIPT_DIR)
        (o, e) = p.communicate()
        sys.stdout.write("swiftrunner.build: subprocess complete")

        if o is not None:
            sys.stdout.write(o)
            sys.stdout.flush()

        if e is not None:
            sys.stderr.write(e)
            sys.stderr.flush()
        sys.stdout.write("swiftrunner.build: exiting build")

    def env(self, message):
        sys.stdout.write("swiftrunner.env: calling action runner")
        env = ActionRunner.env(self, message)
        args = message.get('value', {}) if message else {}
        env['WHISK_INPUT'] = json.dumps(args)
        sys.stdout.write("swiftrunner.env: done, exiting env")
        return env

if __name__ == "__main__":
    setRunner(Swift3Runner())
    main()
