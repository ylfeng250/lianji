#!/usr/bin/env python3
"""把 workout-guide 素材转换成练迹的中文目录和 VectorDrawable。"""

from __future__ import annotations

import json
import re
import shutil
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "catalog-work/manifest.json"
MATCHED = ROOT / "catalog-work/matched.json"
INSTRUCTIONS = ROOT / "catalog-tools/src/main/resources/instructions_zh.json"
NAMES = ROOT / "catalog-tools/names_zh.json"
SVG_ROOT = ROOT / "third_party/workout-guide-src/packages/workout-guide/assets"
DRAWABLE = ROOT / "app/src/main/res/drawable"
CATALOG = ROOT / "app/src/main/assets/catalog/exercises_zh.json"
AUDIT = ROOT / "catalog/match_audit.json"
LICENSES = ROOT / "app/src/main/assets/licenses"
SVG_NS = "{http://www.w3.org/2000/svg}"

MUSCLES = {
    "Core": "核心",
    "Glutes": "臀",
    "Quads": "股四头",
    "Chest": "胸",
    "Shoulders": "肩",
    "Back": "背",
    "Triceps": "肱三头",
    "Hamstrings": "腘绳肌",
    "Lats": "背阔肌",
    "Legs": "腿",
    "Biceps": "肱二头",
    "Upper Back": "上背",
    "Calves": "小腿",
    "Forearms": "前臂",
    "Rear Delts": "肩后束",
    "Posterior Chain": "后链",
    "Mobility": "活动度",
    "Lower Back": "下背",
    "Adductors": "髋内收",
    "Hips": "髋",
    "Cardio": "心肺",
    "Grip": "握力",
    "Groin": "腹股沟",
}
EQUIPMENT = {
    "Bodyweight": "自重",
    "Dumbbell": "哑铃",
    "Machine": "固定器械",
    "Barbell": "杠铃",
    "Cable": "绳索",
    "Resistance Band": "弹力带",
    "Cardio": "有氧器械",
    "Pull-up Bar": "引体杆",
    "Wall": "墙",
    "Towel": "毛巾",
    "Plate": "杠铃片",
    "Kettlebell": "壶铃",
    "Doorway": "门框",
    "Box": "箱子",
    "Bench": "训练凳",
    "Chair": "椅子",
    "Stability Ball": "稳定球",
}
TYPES = {
    "weight_reps": "负重次数",
    "bodyweight_reps": "自重次数",
    "assisted_bodyweight": "辅助自重",
    "duration": "时长",
    "distance_duration": "距离与时长",
}


def drawable_name(slug: str, index: int) -> str:
    return f"ex_{slug.replace('-', '_')}_{index}"


PATH_LIMIT = 30000
COMMANDS = set("MmLlHhVvCcSsQqTtAaZz")


def tokenize_path(data: str) -> list:
    tokens = []
    index = 0
    while index < len(data):
        char = data[index]
        if char in COMMANDS:
            tokens.append(char)
            index += 1
        elif char in " ,\n\t\r":
            index += 1
        else:
            match = re.match(r"[+-]?(?:\d*\.\d+|\d+)(?:[eE][+-]?\d+)?", data[index:])
            if not match:
                raise ValueError(f"无法解析路径：{data[index:index + 24]}")
            tokens.append(float(match.group()))
            index += match.end()
    return tokens


def absolute_subpaths(tokens: list) -> list[list]:
    x = y = 0.0
    start = None
    last_cubic = None
    last_quad = None
    subpaths: list[list] = []
    current: list | None = None
    index = 0

    def take(count: int) -> list[float]:
        nonlocal index
        values = tokens[index:index + count]
        if len(values) != count or any(isinstance(value, str) for value in values):
            raise ValueError("路径参数不足")
        index += count
        return values

    def point(raw_x: float, raw_y: float, relative: bool) -> tuple[float, float]:
        return (x + raw_x, y + raw_y) if relative else (raw_x, raw_y)

    while index < len(tokens):
        command = tokens[index]
        if not isinstance(command, str):
            raise ValueError("路径命令错位")
        index += 1
        relative = command.islower()
        kind = command.upper()
        if kind == "M":
            first = True
            while index < len(tokens) and not isinstance(tokens[index], str):
                raw_x, raw_y = take(2)
                nx, ny = point(raw_x, raw_y, relative)
                if first:
                    current = ["M", nx, ny]
                    subpaths.append(current)
                    start = (nx, ny)
                    first = False
                else:
                    current.extend(["L", nx, ny])
                x, y = nx, ny
                last_cubic = last_quad = None
        elif kind == "L":
            while index < len(tokens) and not isinstance(tokens[index], str):
                raw_x, raw_y = take(2)
                x, y = point(raw_x, raw_y, relative)
                current.extend(["L", x, y])
                last_cubic = last_quad = None
        elif kind == "H":
            while index < len(tokens) and not isinstance(tokens[index], str):
                raw = take(1)[0]
                x = x + raw if relative else raw
                current.extend(["L", x, y])
                last_cubic = last_quad = None
        elif kind == "V":
            while index < len(tokens) and not isinstance(tokens[index], str):
                raw = take(1)[0]
                y = y + raw if relative else raw
                current.extend(["L", x, y])
                last_cubic = last_quad = None
        elif kind == "C":
            while index < len(tokens) and not isinstance(tokens[index], str):
                values = take(6)
                c1 = point(values[0], values[1], relative)
                c2 = point(values[2], values[3], relative)
                x, y = point(values[4], values[5], relative)
                current.extend(["C", *c1, *c2, x, y])
                last_cubic = c2
                last_quad = None
        elif kind == "S":
            while index < len(tokens) and not isinstance(tokens[index], str):
                values = take(4)
                c1 = (2 * x - last_cubic[0], 2 * y - last_cubic[1]) if last_cubic else (x, y)
                c2 = point(values[0], values[1], relative)
                x, y = point(values[2], values[3], relative)
                current.extend(["C", *c1, *c2, x, y])
                last_cubic = c2
                last_quad = None
        elif kind == "Q":
            while index < len(tokens) and not isinstance(tokens[index], str):
                values = take(4)
                control = point(values[0], values[1], relative)
                x, y = point(values[2], values[3], relative)
                current.extend(["Q", *control, x, y])
                last_quad = control
                last_cubic = None
        elif kind == "T":
            while index < len(tokens) and not isinstance(tokens[index], str):
                values = take(2)
                control = (2 * x - last_quad[0], 2 * y - last_quad[1]) if last_quad else (x, y)
                x, y = point(values[0], values[1], relative)
                current.extend(["Q", *control, x, y])
                last_quad = control
                last_cubic = None
        elif kind == "A":
            while index < len(tokens) and not isinstance(tokens[index], str):
                values = take(7)
                x, y = point(values[5], values[6], relative)
                current.extend(["A", abs(values[0]), abs(values[1]), values[2], 1 if values[3] else 0, 1 if values[4] else 0, x, y])
                last_cubic = last_quad = None
        elif kind == "Z":
            if current is not None:
                current.append("Z")
            if start:
                x, y = start
            last_cubic = last_quad = None
        else:
            raise ValueError(f"不支持的路径命令 {command}")
    return [item for item in subpaths if item]


def path_bounds(tokens: list) -> tuple[float, float, float, float, float]:
    xs = []
    ys = []
    index = 0
    while index < len(tokens):
        command = tokens[index]
        index += 1
        count = {"M": 2, "L": 2, "C": 6, "Q": 4, "A": 7, "Z": 0}[command]
        while index < len(tokens) and not isinstance(tokens[index], str):
            values = tokens[index:index + count]
            index += count
            if command == "A":
                xs.append(values[5])
                ys.append(values[6])
            elif command != "Z":
                xs.extend(values[0::2])
                ys.extend(values[1::2])
            if command == "M":
                command = "L"
                count = 2
    if not xs:
        return (0, 0, 0, 0, 0)
    min_x, max_x, min_y, max_y = min(xs), max(xs), min(ys), max(ys)
    return (min_x, min_y, max_x, max_y, max(1.0, (max_x - min_x) * (max_y - min_y)))


def format_number(value: float, places: int) -> str:
    if places <= 0:
        return str(int(round(value)))
    text = f"{value:.{places}f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def serialize_path(tokens: list, places: int) -> str:
    parts = []
    for token in tokens:
        parts.append(token if isinstance(token, str) else format_number(token, places))
    rendered = []
    for part in parts:
        if rendered:
            previous = rendered[-1][-1]
            current = part[0]
            needs_gap = current not in "-+" and not current.isalpha() and (previous.isdigit() or previous == ".")
            needs_gap = needs_gap or (current == "." and (previous.isdigit() or previous == "."))
            if needs_gap:
                rendered.append(" ")
        rendered.append(part)
    return "".join(rendered)


def pack_subpaths(subpaths: list[list]) -> list[str]:
    bounds = [path_bounds(item) for item in subpaths]
    parent = [-1] * len(subpaths)
    for index, box in enumerate(bounds):
        best = None
        for other, outer in enumerate(bounds):
            if index == other or outer[4] <= box[4]:
                continue
            contains = outer[0] - 1 <= box[0] and outer[1] - 1 <= box[1] and outer[2] + 1 >= box[2] and outer[3] + 1 >= box[3]
            if contains and (best is None or outer[4] < bounds[best][4]):
                best = other
        if best is not None:
            parent[index] = best
    children: list[list[int]] = [[] for _ in subpaths]
    roots = []
    for index, owner in enumerate(parent):
        if owner < 0:
            roots.append(index)
        else:
            children[owner].append(index)

    def collect(index: int) -> list:
        tokens = list(subpaths[index])
        for child in children[index]:
            tokens.extend(collect(child))
        return tokens

    def emit(tokens: list) -> list[str]:
        for places in (3, 1, 0):
            rendered = serialize_path(tokens, places)
            if len(rendered) <= PATH_LIMIT:
                return [rendered]
        starts = [index for index, token in enumerate(tokens) if token == "M"]
        if len(starts) <= 1:
            raise ValueError("单个轮廓仍然超过 Android 路径长度限制")
        rendered = []
        for position, start in enumerate(starts):
            end = starts[position + 1] if position + 1 < len(starts) else len(tokens)
            rendered.extend(emit(tokens[start:end]))
        return rendered

    chunks = []
    for root in roots:
        chunks.extend(emit(collect(root)))
    return chunks


def vector_paths(data: str, fill_rule: str) -> list[tuple[str, str]]:
    if len(data) <= PATH_LIMIT:
        return [(data, fill_rule)]
    chunks = pack_subpaths(absolute_subpaths(tokenize_path(data)))
    return [(chunk, fill_rule) for chunk in chunks]


def convert_svg(source: Path, target: Path) -> None:
    tree = ET.parse(source)
    root = tree.getroot()
    tag = root.tag.split("}")[-1]
    if tag != "svg":
        raise ValueError(f"{source} 不是 SVG")
    paths = []
    for element in root.iter():
        name = element.tag.split("}")[-1]
        if name == "svg":
            continue
        if name != "path":
            raise ValueError(f"{source} 含有不支持的元素 {name}")
        data = element.attrib.get("d")
        if not data:
            raise ValueError(f"{source} 缺少路径")
        if re.search(r"[0-9]e[+-]", data, re.I):
            raise ValueError(f"{source} 含科学计数法")
        fill_rule = element.attrib.get("fill-rule", "nonzero")
        paths.append((data, fill_rule))
    if not paths:
        raise ValueError(f"{source} 没有路径")
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="200dp"',
        '    android:height="200dp"',
        '    android:viewportWidth="512"',
        '    android:viewportHeight="512">',
    ]
    for data, fill_rule in paths:
        fill_type = "evenOdd" if fill_rule == "evenodd" else "nonZero"
        for chunk, _rule in vector_paths(data, fill_rule):
            if len(chunk) > PATH_LIMIT:
                raise ValueError(f"{source} 的路径仍然超过 {PATH_LIMIT}")
            escaped = chunk.replace("&", "&amp;").replace("<", "&lt;")
            lines.append(
                f'    <path android:fillColor="#FF000000" android:fillType="{fill_type}" android:pathData="{escaped}" />'
            )
    lines.append("</vector>\n")
    target.write_text("\n".join(lines), encoding="utf-8")


def main() -> None:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    names = json.loads(NAMES.read_text(encoding="utf-8"))
    matched = json.loads(MATCHED.read_text(encoding="utf-8")) if MATCHED.exists() else []
    translations = json.loads(INSTRUCTIONS.read_text(encoding="utf-8")) if INSTRUCTIONS.exists() else {}
    sources = {item["slug"]: item for item in matched}
    missing_names = [item["slug"] for item in manifest if item["slug"] not in names]
    extra_names = sorted(set(names) - {item["slug"] for item in manifest})
    if missing_names or extra_names:
        raise SystemExit(f"中文名称不完整 missing={missing_names[:8]} extra={extra_names[:8]}")

    DRAWABLE.mkdir(parents=True, exist_ok=True)
    for old in DRAWABLE.glob("ex_*.xml"):
        old.unlink()

    exercises = []
    audit = []
    for item in manifest:
        slug = item["slug"]
        label, *aliases = names[slug]
        if re.search(r"[A-Za-z]", label):
            raise SystemExit(f"中文名称仍含英文：{slug} {label}")
        frames = []
        for frame in item["frames"]:
            index = int(frame["index"])
            source = SVG_ROOT / slug / f"frame-{index}.svg"
            if not source.exists():
                raise SystemExit(f"缺少素材 {source}")
            resource = drawable_name(slug, index)
            convert_svg(source, DRAWABLE / f"{resource}.xml")
            frames.append(resource)
        if len(frames) != 3:
            raise SystemExit(f"{slug} 帧数不是 3")
        source_match = sources.get(slug)
        steps = translations.get(slug) or []
        source_id = None
        status = "无可靠文字匹配"
        if source_match and steps:
            if len(steps) != len(source_match["steps"]):
                raise SystemExit(f"{slug} 中文步骤数量与原文不一致")
            if any(re.search(r"https?://|\.jpg|\.png|\.svg", step, re.I) for step in steps):
                raise SystemExit(f"{slug} 步骤混入了图片或链接")
            source_id = source_match["sourceId"]
            status = "已采用公有领域文字并译为中文"
        elif steps:
            if not 1 <= len(steps) <= 8:
                raise SystemExit(f"{slug} 原创说明的步骤数量异常：{len(steps)}")
            if any(re.search(r"https?://|\.jpg|\.png|\.svg", step, re.I) for step in steps):
                raise SystemExit(f"{slug} 原创说明混入了图片或链接")
            status = "原创中文说明"
        elif source_match:
            status = "有文字匹配，但中文译文尚未写入"
            steps = []
        secondary = []
        for muscle in item["secondaryMuscles"]:
            if muscle not in MUSCLES:
                raise SystemExit(f"未知次肌群 {muscle}")
            secondary.append({"en": muscle, "zh": MUSCLES[muscle]})
        if item["primaryMuscle"] not in MUSCLES:
            raise SystemExit(f"未知主肌群 {item['primaryMuscle']}")
        if item["equipment"] not in EQUIPMENT:
            raise SystemExit(f"未知器械 {item['equipment']}")
        if item["exerciseType"] not in TYPES:
            raise SystemExit(f"未知类型 {item['exerciseType']}")
        exercises.append(
            {
                "id": item["id"],
                "slug": slug,
                "nameEn": item["name"],
                "nameZh": label,
                "aliasesZh": aliases,
                "equipment": item["equipment"],
                "equipmentZh": EQUIPMENT[item["equipment"]],
                "primaryMuscle": item["primaryMuscle"],
                "primaryMuscleZh": MUSCLES[item["primaryMuscle"]],
                "secondaryMuscles": secondary,
                "exerciseType": item["exerciseType"],
                "exerciseTypeZh": TYPES[item["exerciseType"]],
                "isStretch": bool(item["isStretch"]),
                "frames": frames,
                "instructionsZh": steps,
                "instructionSourceId": source_id,
            }
        )
        audit.append(
            {
                "slug": slug,
                "nameZh": label,
                "status": status,
                "sourceId": source_id,
                "stepCount": len(steps),
            }
        )

    if len(exercises) != 302 or len({item["id"] for item in exercises}) != 302:
        raise SystemExit("动作数量或编号不唯一")
    payload = {
        "revision": 1,
        "workoutGuideCommit": "aac599224bb9780305239607ef98540b7e0ce389",
        "freeExerciseDbCommit": "f00c92c7dcf1216a928a52c3706c7ce8e2f71ed5",
        "exercises": exercises,
    }
    CATALOG.parent.mkdir(parents=True, exist_ok=True)
    CATALOG.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    AUDIT.parent.mkdir(parents=True, exist_ok=True)
    AUDIT.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    LICENSES.mkdir(parents=True, exist_ok=True)
    for name in ("LICENSE", "LICENSE-ASSETS", "ATTRIBUTION.md"):
        shutil.copyfile(ROOT / "third_party/workout-guide-src" / name, LICENSES / name)
    shutil.copyfile(ROOT / "catalog-work/UNLICENSE.md", LICENSES / "UNLICENSE.md")
    translated = sum(1 for item in audit if item["stepCount"] and item["sourceId"])
    original = sum(1 for item in audit if item["stepCount"] and not item["sourceId"])
    missing = [item["slug"] for item in audit if not item["stepCount"]]
    if missing:
        raise SystemExit(f"以下动作没有中文说明：{missing[:8]}{' 等' if len(missing) > 8 else ''}")
    print(
        f"已生成 {len(exercises)} 个动作、{len(list(DRAWABLE.glob('ex_*.xml')))} 帧，"
        f"{translated} 个译自 free-exercise-db，{original} 个为原创中文说明"
    )


if __name__ == "__main__":
    main()
