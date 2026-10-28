import glob, re, shutil, zipfile

for whl in glob.glob("wheels/[uU]nity[pP]y-*.whl"):
    tmp = whl + ".tmp"
    removed = 0
    with zipfile.ZipFile(whl) as zin, zipfile.ZipFile(tmp, "w", zipfile.ZIP_DEFLATED) as zout:
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename.endswith(".dist-info/METADATA"):
                lines = data.decode("utf-8").split("\n")
                keep = [l for l in lines
                        if not re.match(r"Requires-Dist:\s*fmod[-_.]?toolkit", l, re.I)]
                removed = len(lines) - len(keep)
                data = "\n".join(keep).encode("utf-8")
            zout.writestr(item, data)
    shutil.move(tmp, whl)
    print(f"{whl}: removed {removed} fmod_toolkit requirement(s)")
    assert removed, "no fmod_toolkit line found - UnityPy metadata changed?"
