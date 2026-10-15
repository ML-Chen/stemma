"""Turns characters from installed fonts into SVG path data, so icons don't depend on fonts."""
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

FONTS = {
    "serif": ("/usr/share/fonts/truetype/freefont/FreeSerifBold.ttf", None),
    "sans": ("/usr/share/fonts/truetype/freefont/FreeSansBold.ttf", None),
    "sans-regular": ("/usr/share/fonts/truetype/freefont/FreeSans.ttf", None),
    "poppins": ("/usr/share/fonts/truetype/google-fonts/Poppins-Bold.ttf", None),
    "inter": ("/usr/share/fonts/opentype/inter/Inter-Bold.otf", None),
    "cjk": ("/usr/share/fonts/opentype/noto/NotoSerifCJK-Black.ttc", "SC"),
    "syllabics": ("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", None),
}
_cache = {}


def _font(key):
    if key not in _cache:
        path, region = FONTS[key]
        if path.endswith(".ttc"):
            n = 0
            while True:
                f = TTFont(path, fontNumber=n)
                if region in f["name"].getDebugName(1):
                    break
                n += 1
        else:
            f = TTFont(path)
        _cache[key] = f
    return _cache[key]


def glyph(font_key, text, cx, cy, size):
    """Path data for `text`, `size` px per em, with the ink's bounding box centred on (cx, cy)."""
    font = _font(font_key)
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    scale = size / font["head"].unitsPerEm
    names = [cmap[ord(c)] for c in text]
    # Measure ink bounds in font units, laying glyphs out by advance width.
    bounds = BoundsPen(gs)
    x = 0
    for n in names:
        gs[n].draw(TransformPen(bounds, (1, 0, 0, 1, x, 0)))
        x += gs[n].width
    xmin, ymin, xmax, ymax = bounds.bounds
    ox = cx - (xmin + xmax) / 2 * scale
    oy = cy + (ymin + ymax) / 2 * scale
    pen = SVGPathPen(gs)
    x = 0
    for n in names:
        gs[n].draw(TransformPen(pen, (scale, 0, 0, -scale, ox + x * scale, oy)))
        x += gs[n].width
    return pen.getCommands()


if __name__ == "__main__":
    for k, t in [("serif", "a"), ("serif", "λ"), ("serif", "ж"), ("poppins", "अ"), ("serif", "ع"), ("cjk", "字"), ("serif", "א"), ("sans-regular", "\U00010900")]:
        print(k, t, len(glyph(k, t, 0, 0, 100)))
