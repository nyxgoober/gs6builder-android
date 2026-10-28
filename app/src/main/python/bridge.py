"""String-in/string-out wrappers so Kotlin only passes primitives and JSON text."""
import json

import gs6_api as api


def init(ffmpeg, bases):
    api.init(ffmpeg, bases)


def new_project():
    return json.dumps(api.new_project())


def new_uuid():
    return api.gen_double_uuid()


def check_chart(path, lenient):
    return json.dumps(api.check_chart(path, bool(lenient)))


def build_zip(project_json, out_dir):
    return json.dumps(api.build_zip(json.loads(project_json), out_dir))


def compile_chart(project_json, out_dir, include_ios, log):
    return json.dumps(api.compile_chart(json.loads(project_json), out_dir,
                                        log=lambda m: log.accept(str(m)),
                                        include_ios=bool(include_ios)))


def import_zip(zip_path, extract_dir):
    return json.dumps(api.import_zip(zip_path, extract_dir))


def import_level(site, url, root, log):
    return json.dumps(api.import_level(site, url, root, log=lambda m: log.accept(str(m))))
