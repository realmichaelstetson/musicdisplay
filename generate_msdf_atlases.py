# Generates the MSDF font atlases in src/client/resources/assets/halo/fonts/msdf.
#
# Product Sans only covers ASCII + Latin-1 (no ő/ű, no Latin Extended-A, Cyrillic or Greek),
# so every glyph of custom_charset.txt it lacks is taken from the Inter font of the same
# weight and packed into the same atlas. The output JSON is flattened back into the single
# font format MsdfFont expects.
#
# Usage: python generate_msdf_atlases.py
import json
import os
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.abspath(__file__))
GEN = os.path.join(ROOT, "msdf-atlas-gen", "msdf-atlas-gen", "msdf-atlas-gen.exe")
FONTS = os.path.join(ROOT, "src", "client", "resources", "assets", "halo", "fonts")
OUT = os.path.join(FONTS, "msdf")
CHARSET = os.path.join(ROOT, "custom_charset.txt")

# (atlas name, primary font, fallback font, size, pxrange) - size/pxrange match the original atlases
ATLASES = [
    ("productsans-semibold", "productsans-semibold.ttf", "inter-semibold.ttf", 48, 4),
    ("productsans-medium", "productsans-medium.ttf", "inter-medium.ttf", 48, 4),
    ("productsans-bold", "productsans-bold.ttf", "inter-bold.ttf", 64, 8),
]


def run(args):
    subprocess.run([GEN] + args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def charset_codepoints():
    # custom_charset.txt uses msdf-atlas-gen syntax: one [start, end] range per line
    cps = set()
    with open(CHARSET, encoding="utf-8") as f:
        for line in f:
            line = line.strip().strip("[]")
            if line:
                start, end = (int(x) for x in line.split(","))
                cps.update(range(start, end + 1))
    return cps


def common_args(size, pxrange):
    return ["-type", "msdf", "-format", "png", "-size", str(size), "-pxrange", str(pxrange), "-yorigin", "bottom"]


def main():
    wanted = charset_codepoints()
    with tempfile.TemporaryDirectory() as tmp:
        for name, primary, fallback, size, pxrange in ATLASES:
            # 1) find out which glyphs the primary font actually has
            probe_json = os.path.join(tmp, name + "-probe.json")
            run(["-font", os.path.join(FONTS, primary), "-charset", CHARSET,
                 "-json", probe_json, "-imageout", os.path.join(tmp, name + "-probe.png")] + common_args(size, pxrange))
            with open(probe_json, encoding="utf-8") as f:
                have = {g["unicode"] for g in json.load(f)["glyphs"]}
            missing = sorted(wanted - have)

            # 2) build one atlas: primary font + the missing glyphs from the fallback font
            fallback_charset = os.path.join(tmp, name + "-fallback.txt")
            with open(fallback_charset, "w", encoding="utf-8") as f:
                f.write(", ".join(str(cp) for cp in missing))
            out_json = os.path.join(tmp, name + ".json")
            run(["-font", os.path.join(FONTS, primary), "-charset", CHARSET,
                 "-and", "-font", os.path.join(FONTS, fallback), "-charset", fallback_charset,
                 "-json", out_json, "-imageout", os.path.join(OUT, name + ".png")] + common_args(size, pxrange))

            # 3) flatten the multi-font JSON into the single-font format MsdfFont reads
            with open(out_json, encoding="utf-8") as f:
                data = json.load(f)
            variants = data["variants"]
            glyphs = list(variants[0]["glyphs"])
            present = {g["unicode"] for g in glyphs}
            for g in variants[1]["glyphs"]:
                if g["unicode"] not in present:
                    glyphs.append(g)
                    present.add(g["unicode"])
            flat = {"atlas": data["atlas"], "metrics": variants[0]["metrics"], "glyphs": glyphs,
                    "kerning": variants[0].get("kerning", [])}
            with open(os.path.join(OUT, name + ".json"), "w", encoding="utf-8") as f:
                json.dump(flat, f, separators=(",", ":"))

            print(f"{name}: {len(have)} glyphs from {primary}, {len(present) - len(have)} from {fallback}, "
                  f"atlas {data['atlas']['width']}x{data['atlas']['height']}")


if __name__ == "__main__":
    main()
