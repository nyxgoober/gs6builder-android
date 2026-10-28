"""Headless API for the GridlessSekai6 custom chart builder (no Tkinter).

Ported from upstream builder/main.py (AGPL-3.0). All inputs/outputs are JSON-able so a
Kotlin/Chaquopy UI (or tests, or a CLI) can drive it. Upstream charts.py / encode.py /
untitledcharts.py / ccarchive.py are used unmodified.
"""
from __future__ import annotations

import json
import os
import re
import shutil
import subprocess
import tempfile
import uuid
import zipfile

import ccarchive
import charts
import deps
import encode
import untitledcharts

DIFFICULTIES = ["easy", "normal", "hard", "expert", "master", "append"]
MV_TYPES = ["ogmv", "2dmv"]
VOCAL_TYPES = ["sekai", "original_song", "another_vocal"]
DEFAULT_VOCAL_TYPE = "original_song"
MIN_LEVEL, MAX_LEVEL, MAX_CHARTS = 1, 99, 6
MAX_PREVIEW_SECONDS = 45.0
PREVIEW_FADE_SECONDS = 2.0
FORMAT_VERSION = 1

_BASES = deps.BASES


def init(ffmpeg: str, bases: str) -> None:
    """Point the upstream modules at the Android ffmpeg binary and extracted bases dir."""
    global _BASES
    deps.FFMPEG = ffmpeg
    deps.FFPROBE = ffmpeg  # unused here: probing goes through `ffmpeg -i`
    deps.BASES = _BASES = bases


# ---------------------------------------------------------------- media helpers
def _ff(args: list[str]) -> subprocess.CompletedProcess:
    return subprocess.run([deps.FFMPEG, "-hide_banner", *args], capture_output=True, text=True)


def probe(path: str) -> dict:
    """{'duration': seconds|None, 'audio': bool, 'video': bool} via `ffmpeg -i`."""
    text = _ff(["-i", path]).stderr
    m = re.search(r"Duration:\s*(\d+):(\d+):(\d+(?:\.\d+)?)", text)
    dur = int(m[1]) * 3600 + int(m[2]) * 60 + float(m[3]) if m else None
    return {
        "duration": dur,
        "audio": bool(re.search(r"Stream #.*Audio:", text)),
        "video": bool(re.search(r"Stream #.*Video:", text)),
    }


def fit_preview(src: str, folder: str) -> str:
    dur = probe(src)["duration"]
    if dur is None:
        raise ValueError(f"could not read the duration of {os.path.basename(src)}")
    if dur <= MAX_PREVIEW_SECONDS:
        return src
    dst = os.path.join(folder, "track_pre.flac")
    fade_at = MAX_PREVIEW_SECONDS - PREVIEW_FADE_SECONDS
    cp = _ff(["-loglevel", "error", "-y", "-i", src, "-vn", "-t", f"{MAX_PREVIEW_SECONDS:g}",
              "-af", f"afade=t=out:st={fade_at:g}:d={PREVIEW_FADE_SECONDS:g}", dst])
    if cp.returncode != 0:
        raise RuntimeError(f"ffmpeg could not cut the preview: {cp.stderr.strip()}")
    return dst


def validate_image(path: str) -> tuple[bool, str]:
    from PIL import Image
    try:
        with Image.open(path) as im:
            im.verify()
        return True, ""
    except Exception as e:  # noqa: BLE001
        return False, f"Not a readable image: {e}"


_TIME_SIG_RE = re.compile(r"^#\d{3}02:", re.IGNORECASE)


def validate_sus_text(text: str) -> tuple[bool, str]:
    lines = [ln.strip() for ln in text.splitlines()]
    sig = [ln for ln in lines if _TIME_SIG_RE.match(ln)]
    if len(sig) != 1:
        return False, f"must have exactly one time-signature line, found {len(sig)}"
    if not any(ln.replace(" ", "") == "#00002:4" for ln in lines):
        return False, "missing the 4/4 time signature at measure 0 (#00002: 4)"
    return True, ""


def gen_double_uuid() -> str:
    return f"{uuid.uuid4()}_{uuid.uuid4()}"


def validate_double_uuid(s: str) -> bool:
    parts = s.strip().split("_")
    if len(parts) != 2:
        return False
    for p in parts:
        try:
            u = uuid.UUID(p)
        except (ValueError, AttributeError):
            return False
        if u.version != 4 or str(u) != p.lower():
            return False
    return True


def _ext(path: str) -> str:
    return os.path.splitext(path)[1].lower()


def new_project() -> dict:
    return {
        "jacket": "", "track": "", "track_pre": "",
        "mvs": [],  # [{"kind": "ogmv"|"2dmv", "path": str}]
        "charts": [{"difficulty": "master", "level": 1, "path": "", "lenient": False}],
        "title": "", "charter": "", "lyricist": "", "composer": "", "arranger": "",
        "artist": "", "vocals": "", "collab": "", "original": "",
        "is_full": True, "offset_ms": 0, "mv_offset_ms": 0,
        "default_vocal_name": "", "default_vocal_type": DEFAULT_VOCAL_TYPE,
        "alt_vocals": [],  # [{name,vocals,vocal_type,audio,preview,jacket}]
        "identifier": gen_double_uuid(),
    }


# ---------------------------------------------------------------- charts
def check_chart(path: str, lenient: bool = False) -> dict:
    """Load a chart; returns combo, or the list of unsupported features to confirm."""
    try:
        score = charts.load_score(path, strict=not lenient)
        return {"status": "ok", "combo": charts.note_count(score),
                "offset_ms": charts.offset_ms_for(score)}
    except charts.UnsupportedFeatures as e:
        return {"status": "unsupported", "format": e.format_name, "features": e.features}
    except Exception as e:  # noqa: BLE001
        return {"status": "error", "message": str(e)}


# ---------------------------------------------------------------- validate / collect
def collect(p: dict) -> dict:
    """Validate a project. Returns {'errors': [...]} or {'errors': [], 'data': {...}}."""
    errs: list[str] = []

    def need(path, label):
        if not path or not os.path.isfile(path):
            errs.append(f"{label} is required.")
            return False
        return True

    if need(p["jacket"], "Jacket"):
        ok, msg = validate_image(p["jacket"])
        if not ok:
            errs.append(f"Jacket: {msg}")
    if need(p["track"], "Audio track") and not probe(p["track"])["audio"]:
        errs.append("Audio track has no audio stream.")
    if need(p["track_pre"], "Preview audio"):
        dur = probe(p["track_pre"])["duration"]
        if dur is None:
            errs.append("Preview audio: could not read duration.")
        elif dur > MAX_PREVIEW_SECONDS + 0.05:
            errs.append(f"Preview audio is {dur:.1f}s (max {MAX_PREVIEW_SECONDS:.0f}s).")

    diffs, seen = [], set()
    rows = [r for r in p["charts"] if (r.get("path") or "").strip()]
    if not rows:
        errs.append("At least one chart is required.")
    for r in rows:
        d, path = r["difficulty"], r["path"]
        if d in seen:
            errs.append(f"Duplicate difficulty '{d}'.")
        seen.add(d)
        if not os.path.isfile(path):
            errs.append(f"{d}: chart file not found.")
            continue
        try:
            fmt = charts.chart_format(path)
        except (OSError, ValueError) as e:
            errs.append(f"{d}: {e}.")
            continue
        try:
            lvl = int(r["level"])
        except (ValueError, TypeError):
            errs.append(f"{d}: level must be a number.")
            continue
        if not (MIN_LEVEL <= lvl <= MAX_LEVEL):
            errs.append(f"{d}: level must be {MIN_LEVEL}-{MAX_LEVEL}.")
        if fmt == "sus":
            try:
                ok, msg = validate_sus_text(charts.sus_text(path))
            except Exception as e:  # noqa: BLE001
                errs.append(f"{d}: cannot read the chart ({e}).")
                continue
            if not ok:
                errs.append(f"{d}: {msg}.")
        res = check_chart(path, bool(r.get("lenient")))
        if res["status"] == "unsupported":
            errs.append(f"{d}: the chart uses unsupported {res['format']} features.")
            continue
        if res["status"] == "error":
            errs.append(f"{d}: cannot load the chart ({res['message']}).")
            continue
        diffs.append({"difficulty": d, "level": lvl, "notes": res["combo"],
                      "_path": path, "_lenient": bool(r.get("lenient"))})

    mvs = [m for m in p["mvs"] if (m.get("path") or "").strip()]
    og_mv = next((m["path"] for m in mvs if m["kind"] == "ogmv"), None)
    two_d_mv = next((m["path"] for m in mvs if m["kind"] == "2dmv"), None)
    for label, mp in (("ogmv", og_mv), ("2dmv", two_d_mv)):
        if mp:
            if not os.path.isfile(mp):
                errs.append(f"{label}: file not found.")
            elif not probe(mp)["video"]:
                errs.append(f"{label}: no video stream.")
    kinds = [m["kind"] for m in mvs]
    for k in MV_TYPES:
        if kinds.count(k) > 1:
            errs.append(f"Only one '{k}' music video is allowed.")

    for key, lab in [("title", "Title"), ("charter", "Charter"), ("lyricist", "Lyricist"),
                     ("composer", "Composer"), ("arranger", "Arranger"),
                     ("artist", "Artist"), ("vocals", "Vocals")]:
        if not str(p.get(key, "")).strip():
            errs.append(f"{lab} is required.")

    def to_int(key, label):
        try:
            return int(str(p.get(key, 0)).strip() or 0)
        except ValueError:
            errs.append(f"{label} must be an integer.")
            return 0

    offset, mv_offset = to_int("offset_ms", "offset_ms"), to_int("mv_offset_ms", "additional_mv_offset_ms")

    cover_files, alt_entries = [], []
    alts = [v for v in p["alt_vocals"] if v["name"].strip() or v["audio"].strip() or v["vocals"].strip()]
    if alts and not p["default_vocal_name"].strip():
        errs.append("Default vocal name is required when alt vocals are present.")
    for idx, v in enumerate(alts, start=1):
        nm, vo, au, pv, jk = (v[k].strip() for k in ("name", "vocals", "audio", "preview", "jacket"))
        if not nm:
            errs.append(f"Alt vocal {idx}: name is required.")
        if not vo:
            errs.append(f"Alt vocal {idx}: vocals are required.")
        if not au or not os.path.isfile(au):
            errs.append(f"Alt vocal {idx}: audio file is required.")
        elif not probe(au)["audio"]:
            errs.append(f"Alt vocal {idx}: audio has no audio stream.")
        if not pv or not os.path.isfile(pv):
            errs.append(f"Alt vocal {idx}: preview is required.")
        else:
            d = probe(pv)["duration"]
            if d is not None and d > MAX_PREVIEW_SECONDS + 0.05:
                errs.append(f"Alt vocal {idx}: preview is {d:.1f}s (max {MAX_PREVIEW_SECONDS:.0f}s).")
        if jk:
            if not os.path.isfile(jk):
                errs.append(f"Alt vocal {idx}: jacket not found.")
            else:
                ok, msg = validate_image(jk)
                if not ok:
                    errs.append(f"Alt vocal {idx}: jacket {msg}")
        alt_entries.append({"id": idx, "name": nm, "vocals": vo,
                            "vocal_type": v["vocal_type"], "has_jacket": bool(jk)})
        if au:
            cover_files.append((au, f"covers/cover_{idx}" + _ext(au)))
        if pv:
            cover_files.append((pv, f"covers/cover_pre_{idx}" + _ext(pv)))
        if jk:
            cover_files.append((jk, f"covers/cover_jacket_{idx}" + _ext(jk)))

    song_duration = None
    if p["track"].strip():
        d = probe(p["track"])["duration"]
        song_duration = round(d, 3) if d is not None else None
    if not validate_double_uuid(p["identifier"]):
        errs.append("Identifier must be two valid v4 UUIDs joined by '_'.")
    if errs:
        return {"errors": errs}

    def tr(key):
        v = str(p[key]).strip()
        return {"jp": v, "en": v}

    info = {
        "identifier": p["identifier"].strip(), "format_version": FORMAT_VERSION,
        "title": tr("title"), "charter": p["charter"].strip(),
        "difficulties": [{"difficulty": d["difficulty"], "level": d["level"], "notes": d["notes"]} for d in diffs],
        "lyricist": tr("lyricist"), "composer": tr("composer"), "arranger": tr("arranger"),
        "artist": tr("artist"), "vocals": tr("vocals"),
        "isFullLength": bool(p["is_full"]), "vocaloid_or_other": "other",
        "offset_ms": offset, "additional_mv_offset_ms": mv_offset,
        "song_duration": song_duration, "original": p["original"].strip() or None,
        "original_music_video": og_mv is not None, "2d_music_video": two_d_mv is not None,
    }
    if p["collab"].strip():
        info["collab"] = tr("collab")
    if alt_entries:
        d2 = lambda s: {"jp": s, "en": s}  # noqa: E731
        covers = [{"default": True, "name": d2(p["default_vocal_name"].strip()),
                   "vocal_type": p["default_vocal_type"]}]
        for a in alt_entries:
            covers.append({"default": False, "id": a["id"], "name": d2(a["name"]),
                           "vocals": d2(a["vocals"]), "vocal_type": a["vocal_type"],
                           "has_jacket": a["has_jacket"]})
        info["covers"] = covers
    return {"errors": [], "data": {
        "info": info, "diffs": diffs, "jacket": p["jacket"], "track": p["track"],
        "track_pre": p["track_pre"], "ogmv": og_mv, "2dmv": two_d_mv, "cover_files": cover_files}}


# ---------------------------------------------------------------- outputs
def _safe_name(title: str) -> str:
    return re.sub(r"[^\w\-. ]+", "_", title).strip() or "custom_chart"


def build_zip(p: dict, out_dir: str) -> dict:
    """Write the editable project .zip into out_dir. {'errors':[..]} or {'path': ...}."""
    res = collect(p)
    if res["errors"]:
        return res
    data = res["data"]
    out = os.path.join(out_dir, _safe_name(data["info"]["title"]["en"]) + ".zip")
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("ChartInfo.json", json.dumps(data["info"], ensure_ascii=False, indent=2))
        z.write(data["jacket"], "jacket" + _ext(data["jacket"]))
        z.write(data["track"], "track" + _ext(data["track"]))
        z.write(data["track_pre"], "track_pre" + _ext(data["track_pre"]))
        for d in data["diffs"]:
            z.write(d["_path"], d["difficulty"] + charts.chart_ext(d["_path"]))
        if data["ogmv"]:
            z.write(data["ogmv"], "mv" + _ext(data["ogmv"]))
        if data["2dmv"]:
            z.write(data["2dmv"], "2dmv" + _ext(data["2dmv"]))
        for src, name in data["cover_files"]:
            z.write(src, name)
    return {"errors": [], "path": out}


def compile_chart(p: dict, out_dir: str, log=print, include_ios: bool = False) -> dict:
    """Compile .chart.gs6 files into out_dir (Android only unless include_ios)."""
    res = collect(p)
    if res["errors"]:
        return res
    data = res["data"]
    want_mv = bool(data["ogmv"] or data["2dmv"])
    missing = encode.missing_bases(_BASES, want_mv=want_mv)
    if missing:
        return {"errors": ["Missing base bundles: " + ", ".join(missing)]}
    encode.PLATFORMS = ("android", "ios") if include_ios else ("android",)
    base = os.path.join(out_dir, _safe_name(data["info"]["title"]["en"]))
    outputs = encode.compile_chart(data, _BASES, base, log=log)
    return {"errors": [], "outputs": outputs, "identifier": data["info"]["identifier"]}


# ---------------------------------------------------------------- imports
def import_zip(zip_path: str, extract_dir: str) -> dict:
    shutil.rmtree(extract_dir, ignore_errors=True)
    os.makedirs(extract_dir)
    with zipfile.ZipFile(zip_path) as z:
        for member in z.namelist():  # zip-slip guard
            if not os.path.realpath(os.path.join(extract_dir, member)).startswith(os.path.realpath(extract_dir)):
                raise ValueError("unsafe path in zip")
        z.extractall(extract_dir)
    ip = os.path.join(extract_dir, "ChartInfo.json")
    if not os.path.isfile(ip):
        raise ValueError("ChartInfo.json not found in the zip.")
    with open(ip, encoding="utf-8") as f:
        info = json.load(f)

    def find(stem):
        for root, _d, files in os.walk(extract_dir):
            for fn in files:
                if fn.split(".", 1)[0] == stem:
                    return os.path.join(root, fn)
        return ""

    def one(v):
        return (v.get("en") or v.get("jp") or "") if isinstance(v, dict) else (v or "")

    p = new_project()
    p.update(jacket=find("jacket"), track=find("track"), track_pre=find("track_pre"))
    if find("mv"):
        p["mvs"].append({"kind": "ogmv", "path": find("mv")})
    if find("2dmv"):
        p["mvs"].append({"kind": "2dmv", "path": find("2dmv")})
    p["charts"] = [{"difficulty": d.get("difficulty", "expert"), "level": d.get("level", 1),
                    "path": find(d.get("difficulty", "expert")), "lenient": False}
                   for d in info.get("difficulties", [])] or p["charts"]
    for k in ("title", "lyricist", "composer", "arranger", "artist", "vocals", "collab"):
        p[k] = one(info.get(k))
    p.update(charter=info.get("charter", "") or "", is_full=bool(info.get("isFullLength", True)),
             offset_ms=info.get("offset_ms", 0), mv_offset_ms=info.get("additional_mv_offset_ms", 0),
             original=info.get("original") or "")
    if info.get("identifier"):
        p["identifier"] = info["identifier"]
    for c in info.get("covers", []):
        if c.get("default"):
            p["default_vocal_name"] = one(c.get("name"))
            p["default_vocal_type"] = c.get("vocal_type", "sekai")
        else:
            cid = c.get("id")
            p["alt_vocals"].append({
                "name": one(c.get("name")), "vocals": one(c.get("vocals")),
                "vocal_type": c.get("vocal_type", DEFAULT_VOCAL_TYPE),
                "audio": find(f"cover_{cid}"), "preview": find(f"cover_pre_{cid}"),
                "jacket": find(f"cover_jacket_{cid}")})
    return p


_SITES = {"untitledcharts": untitledcharts, "ccarchive": ccarchive}


def import_level(site: str, url: str, folder_root: str, log=print) -> dict:
    """Download a level from untitledcharts/ccarchive and return a prefilled project."""
    src = _SITES[site]
    name = src.level_name(url)  # ValueError on a bad link
    folder = os.path.join(folder_root, site, name)
    level = src.download_level(name, folder, log)
    if not probe(level.bgm)["audio"]:
        raise ValueError("the song file has no audio stream")
    ok, msg = validate_image(level.cover)
    if not ok:
        raise ValueError(f"cover: {msg}")
    preview = fit_preview(level.preview or level.bgm, folder)
    score = charts.load_score(level.chart, strict=False)
    p = new_project()
    p.update(title=level.title, artist=level.artists, charter=level.author, jacket=level.cover,
             track=level.bgm, track_pre=preview, offset_ms=charts.offset_ms_for(score))
    p["charts"] = [{"difficulty": "master",
                    "level": min(max(level.rating or MIN_LEVEL, MIN_LEVEL), MAX_LEVEL),
                    "path": level.chart, "lenient": True}]
    p["_notes"] = {"preview_cut": preview != (level.preview or level.bgm), "folder": level.folder}
    return p
