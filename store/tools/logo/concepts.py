"""Logo concepts, written as SVG. Each function returns a complete 1024x1024 SVG document.

A: Family tree   - a tree whose branches end in letters from many scripts, rooted in a glowing
                   asterisk (the mark for a reconstructed proto-language word).
B: Asterisk bloom - the reconstruction asterisk as a glossy 3D object; each arm is a descendant
                   language carrying a letter of its script.
C: Strata        - a cut through the ground showing the ancestry of the letter A, layer by layer:
                   A, archaic Greek alpha, Phoenician aleph, an ox-head pictogram, and the asterisk.
"""
import math
import random

from glyphs import glyph

W = 1024
C = W / 2


def doc(defs, body, background):
    return f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {W}" width="{W}" height="{W}">
<defs>
  <filter id="shadow" x="-30%" y="-30%" width="160%" height="160%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="20"/>
    <feOffset dy="12" result="b"/>
    <feComponentTransfer><feFuncA type="linear" slope="0.32"/></feComponentTransfer>
    <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="softshadow" x="-30%" y="-30%" width="160%" height="160%">
    <feGaussianBlur in="SourceAlpha" stdDeviation="4"/>
    <feOffset dy="3" result="b"/>
    <feComponentTransfer><feFuncA type="linear" slope="0.18"/></feComponentTransfer>
    <feMerge><feMergeNode/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  <filter id="glow" x="-80%" y="-80%" width="260%" height="260%">
    <feGaussianBlur stdDeviation="22" result="g"/>
    <feMerge><feMergeNode in="g"/><feMergeNode in="g"/><feMergeNode in="SourceGraphic"/></feMerge>
  </filter>
  {defs}
</defs>
{background}
{body}
</svg>'''


def asterisk_path(cx, cy, r, arm_w, arms=6, rot=0):
    """A solid asterisk made of rounded bars."""
    out = []
    for i in range(arms // 2):
        a = math.radians(rot + i * 180 / (arms // 2))
        dx, dy = math.sin(a) * r, -math.cos(a) * r
        out.append(
            f'<line x1="{cx - dx:.1f}" y1="{cy - dy:.1f}" x2="{cx + dx:.1f}" y2="{cy + dy:.1f}" '
            f'stroke-width="{arm_w}" stroke-linecap="round"/>'
        )
    return "\n".join(out)


def sphere_gradient(gid, base, light, dark):
    return f'''<radialGradient id="{gid}" cx="0.36" cy="0.32" r="0.75">
    <stop offset="0" stop-color="{light}"/>
    <stop offset="0.45" stop-color="{base}"/>
    <stop offset="1" stop-color="{dark}"/>
  </radialGradient>'''


# Saturated, warm palette in the spirit of current "3D" app icons.
PALETTE = [
    ("#FF5A5F", "#FFB3B0", "#C2303A"),  # coral
    ("#FFA41B", "#FFE0A3", "#C46A00"),  # saffron
    ("#12B5A6", "#9DF0E4", "#067A6F"),  # teal
    ("#8B6CFF", "#D6CBFF", "#5537C9"),  # violet
    ("#38C172", "#B5F0CC", "#1D8248"),  # leaf
    ("#2F8CFF", "#B9D8FF", "#1557B8"),  # sky
]

# One letter from each of several living scripts: Latin, Greek, Cyrillic, Devanagari, Arabic, Han.
SCRIPTS = [("serif", "a"), ("maya", "kin"), ("syllabics", "\u1583"), ("poppins", "अ"), ("serif", "ع"), ("cjk", "字")]


def kin_glyph(cx, cy, size, fill, cut):
    """The Maya logogram K'IN ("sun", "day"): a four-petalled flower in a rounded cartouche,
    standing on the small pedestal that Maya day signs sit on. Drawn by hand, since no installed
    font has Maya signs."""
    h = size / 2
    stroke = size * 0.08
    top = cy - h * 0.9            # cartouche top
    ch = h * 1.42                 # cartouche height
    fcy = top + ch / 2            # flower centre
    r = size * 0.13
    o = size * 0.15
    petals = "".join(f'<circle cx="{cx + dx * o:.1f}" cy="{fcy + dy * o:.1f}" r="{r:.1f}"/>' for dx, dy in ((0, -1), (1, 0), (0, 1), (-1, 0)))
    base_y = top + ch + stroke * 0.9
    feet = "".join(f'<circle cx="{cx + dx * h * 0.42:.1f}" cy="{base_y + size * 0.07:.1f}" r="{size * 0.07:.1f}"/>' for dx in (-1, 0, 1))
    return (f'<rect x="{cx - h * 0.86:.1f}" y="{top:.1f}" width="{h * 1.72:.1f}" height="{ch:.1f}" '
            f'rx="{h * 0.55:.1f}" fill="none" stroke="{fill}" stroke-width="{stroke:.1f}"/>'
            f'<g fill="{fill}">{petals}<circle cx="{cx}" cy="{fcy:.1f}" r="{r * 0.9:.1f}"/>{feet}</g>'
            f'<circle cx="{cx}" cy="{fcy:.1f}" r="{r * 0.42:.1f}" fill="{cut}"/>')


def mix(hex_color, other, t):
    a = [int(hex_color[i:i + 2], 16) for i in (1, 3, 5)]
    b = [int(other[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * t):02X}" for x, y in zip(a, b))


def petal(cx, cy, length, tip_r, neck):
    """A petal pointing up from (cx, cy): narrow at the centre, a full circle at the tip."""
    ty = cy - length
    return (f"M {cx - neck / 2} {cy} "
            f"C {cx - neck / 2} {cy - length * 0.35} {cx - tip_r} {ty + tip_r * 1.2} {cx - tip_r} {ty} "
            f"A {tip_r} {tip_r} 0 0 1 {cx + tip_r} {ty} "
            f"C {cx + tip_r} {ty + tip_r * 1.2} {cx + neck / 2} {cy - length * 0.35} {cx + neck / 2} {cy} Z")


def concept_b():
    """Asterisk bloom."""
    defs = []
    body = []
    arms = 6
    length, tip_r, neck = 300, 104, 70
    lx, ly = -0.55, -0.75  # light from the top left, the same for every petal
    for i in range(arms):
        base, lt, dk = PALETTE[i]
        ang = i * 360 / arms
        a = math.radians(ang)
        # Light direction expressed in the petal's rotated frame.
        local_x = lx * math.cos(a) + ly * math.sin(a)
        local_y = -lx * math.sin(a) + ly * math.cos(a)
        tipx, tipy = C, C - length
        fx, fy = tipx + local_x * tip_r * 0.75, tipy + local_y * tip_r * 0.75
        defs.append(f"""<radialGradient id="pet{i}" gradientUnits="userSpaceOnUse" cx="{fx:.0f}" cy="{fy:.0f}" r="{length * 1.1:.0f}">
    <stop offset="0" stop-color="{mix(base, '#FFFFFF', 0.16)}"/>
    <stop offset="0.5" stop-color="{base}"/>
    <stop offset="1" stop-color="{mix(base, '#000000', 0.14)}"/>
  </radialGradient>""")
        body.append(f"""<g transform="rotate({ang} {C} {C})">
  <path d="{petal(C, C, length, tip_r, neck)}" fill="url(#pet{i})" filter="url(#shadow)"/>
</g>""")
    letters = []
    for i, (font, ch) in enumerate(SCRIPTS):
        a = math.radians(i * 360 / arms)
        gx, gy = C + math.sin(a) * length, C - math.cos(a) * length
        if font == "maya":
            letters.append(f'<g filter="url(#softshadow)">{kin_glyph(gx, gy + 2, 112, "#fff", PALETTE[i][0])}</g>')
        else:
            letters.append(f'<path d="{glyph(font, ch, gx, gy + 4, 112)}" fill="#fff" filter="url(#softshadow)"/>')
    defs.append("""<radialGradient id="bead" cx="0.4" cy="0.35" r="0.8">
    <stop offset="0" stop-color="#FFD66B"/>
    <stop offset="0.6" stop-color="#F4B63B"/>
    <stop offset="1" stop-color="#D99A22"/>
  </radialGradient>""")
    bead = f"""<circle cx="{C}" cy="{C}" r="92" fill="url(#bead)" filter="url(#shadow)"/>
<g stroke="#B07812" stroke-opacity="0.7">{asterisk_path(C, C, 40, 16)}</g>"""
    defs.append("""<radialGradient id="bgB" cx="0.5" cy="0.45" r="0.75">
    <stop offset="0" stop-color="#3B2C86"/>
    <stop offset="0.6" stop-color="#21195A"/>
    <stop offset="1" stop-color="#110D33"/>
  </radialGradient>""")
    rnd = random.Random(7)
    dust = "\n".join(
        f'<circle cx="{rnd.uniform(0, W):.0f}" cy="{rnd.uniform(0, W):.0f}" r="{rnd.uniform(1.5, 3.2):.1f}" fill="#fff" opacity="{rnd.uniform(0.12, 0.4):.2f}"/>'
        for _ in range(40)
    )
    rings = "\n".join(
        f'<circle cx="{C}" cy="{C}" r="{r}" fill="none" stroke="#fff" stroke-opacity="{0.07 - k * 0.012:.3f}" stroke-width="3"/>'
        for k, r in enumerate([440, 470, 500])
    )
    bg = f'<rect width="{W}" height="{W}" fill="url(#bgB)"/>\n{rings}\n{dust}'
    return doc("\n".join(defs), "\n".join(body) + "\n" + "\n".join(letters) + "\n" + bead, bg)


def concept_a():
    """Family tree of words."""
    defs = ['''<linearGradient id="sky" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFE9C7"/>
    <stop offset="1" stop-color="#FFBE98"/>
  </linearGradient>
  <linearGradient id="soil" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#7A4128"/>
    <stop offset="1" stop-color="#2E1610"/>
  </linearGradient>
  <linearGradient id="bark" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#9C5A2E"/>
    <stop offset="0.45" stop-color="#C98547"/>
    <stop offset="1" stop-color="#6B3519"/>
  </linearGradient>
  <linearGradient id="leaf" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#8EE07A"/>
    <stop offset="1" stop-color="#2E9447"/>
  </linearGradient>
  <linearGradient id="root" x1="0" y1="1" x2="0" y2="0">
    <stop offset="0" stop-color="#FFD25A"/>
    <stop offset="1" stop-color="#C98547"/>
  </linearGradient>''']
    horizon = 640
    bg = f'''<rect width="{W}" height="{W}" fill="url(#sky)"/>
<circle cx="{C}" cy="330" r="300" fill="#fff" opacity="0.35"/>
<path d="M0 {horizon} Q {C} {horizon - 34} {W} {horizon} L {W} {W} L 0 {W} Z" fill="url(#soil)"/>
<path d="M0 {horizon + 90} Q 300 {horizon + 70} {C} {horizon + 96} T {W} {horizon + 86}" fill="none" stroke="#000" stroke-opacity="0.18" stroke-width="10"/>
<path d="M0 {horizon + 200} Q 280 {horizon + 180} {C} {horizon + 206} T {W} {horizon + 196}" fill="none" stroke="#000" stroke-opacity="0.18" stroke-width="10"/>'''
    # "Fossil" letters in the soil: Phoenician aleph, a rune, cuneiform wedges.
    fossils = [
        f'<path d="{glyph("sans-regular", chr(0x10900), 175, horizon + 150, 88)}" fill="#E8B48A" opacity="0.35"/>',
        '<g fill="#E8B48A" opacity="0.35" transform="translate(805 %d)">' % (horizon + 140)
        + '<path d="M0 -40 L 14 -40 L 7 -26 Z M 7 -26 L 7 30" stroke="#E8B48A" stroke-width="8"/>'
        + '<path d="M22 -14 L 40 -14 L 31 -2 Z M 31 -2 L 31 30" stroke="#E8B48A" stroke-width="8"/>'
        + '<path d="M-28 6 L -10 6 L -19 18 Z M -19 18 L -19 40" stroke="#E8B48A" stroke-width="8"/></g>',
        '<path d="M 268 %d L 268 %d M 268 %d L 300 %d M 268 %d L 300 %d" stroke="#E8B48A" stroke-width="9" stroke-linecap="round" opacity="0.35"/>'
        % (horizon + 250, horizon + 330, horizon + 262, horizon + 240, horizon + 292, horizon + 270),
    ]
    seed_y = 880
    roots = f'''<path d="M {C} {horizon + 8} C {C - 10} {horizon + 90} {C + 14} {seed_y - 110} {C} {seed_y - 40}" stroke="url(#root)" stroke-width="30" fill="none" stroke-linecap="round"/>
<path d="M {C - 6} {horizon + 50} C {C - 90} {horizon + 80} {C - 120} {horizon + 120} {C - 170} {horizon + 130}" stroke="#C98547" stroke-width="14" fill="none" stroke-linecap="round" opacity="0.8"/>
<path d="M {C + 6} {horizon + 70} C {C + 80} {horizon + 100} {C + 120} {horizon + 140} {C + 175} {horizon + 160}" stroke="#C98547" stroke-width="12" fill="none" stroke-linecap="round" opacity="0.8"/>'''
    seed = f'''<g filter="url(#glow)" stroke="#FFD84D">{asterisk_path(C, seed_y, 54, 30)}</g>'''
    trunk_top = (C, 470)
    fruit = [(C, 168), (330, 250), (694, 250), (220, 410), (804, 410), (C, 330)]
    branches = []
    leaves = []
    rnd = random.Random(3)
    for (fx, fy) in fruit:
        mx = (trunk_top[0] + fx) / 2
        sx, sy = trunk_top[0], trunk_top[1] + 20
        # A thick lower part and a thinner upper part, so branches taper towards the fruit.
        thirds = ((sx + 2 * fx) / 3, (sy + 2 * fy) / 3)
        branches.append(f'<path d="M {sx} {sy} Q {mx} {trunk_top[1] - 20} {thirds[0]:.0f} {thirds[1]:.0f}" stroke="url(#bark)" stroke-width="44" fill="none" stroke-linecap="round"/>')
        branches.append(f'<path d="M {sx} {sy} Q {mx} {trunk_top[1] - 20} {fx} {fy}" stroke="url(#bark)" stroke-width="24" fill="none" stroke-linecap="round"/>')
        for t in (0.55, 0.78):
            px = (1 - t) ** 2 * sx + 2 * (1 - t) * t * mx + t * t * fx
            py = (1 - t) ** 2 * sy + 2 * (1 - t) * t * (trunk_top[1] - 20) + t * t * fy
            ang = rnd.uniform(-60, 60) + (-90 if fx >= C else 90)
            leaves.append(f'<ellipse cx="{px:.0f}" cy="{py - 18:.0f}" rx="30" ry="13" fill="url(#leaf)" transform="rotate({ang:.0f} {px:.0f} {py - 18:.0f})"/>')
    trunk = f'<path d="M {C - 92} {horizon + 10} C {C - 46} 600 {C - 50} 540 {C - 30} {trunk_top[1] + 10} L {C + 30} {trunk_top[1] + 10} C {C + 50} 540 {C + 46} 600 {C + 92} {horizon + 10} Z" fill="url(#bark)" filter="url(#shadow)"/>'
    bubbles = []
    for i, ((fx, fy), (font, ch)) in enumerate(zip(fruit, SCRIPTS)):
        base, lt, dk = PALETTE[i]
        defs.append(sphere_gradient(f"fr{i}", base, lt, dk))
        bubbles.append(f'''<circle cx="{fx}" cy="{fy}" r="82" fill="url(#fr{i})" filter="url(#shadow)"/>
<ellipse cx="{fx - 26}" cy="{fy - 34}" rx="26" ry="15" fill="#fff" opacity="0.55" transform="rotate(-30 {fx - 26} {fy - 34})"/>
{kin_glyph(fx, fy + 2, 92, "#fff", base) if font == "maya" else f'<path d="{glyph(font, ch, fx, fy + 4, 92)}" fill="#fff" filter="url(#softshadow)"/>'}''')
    body = "\n".join(fossils) + roots + seed + "\n".join(branches) + trunk + "\n".join(leaves) + "\n".join(bubbles)
    return doc("\n".join(defs), body, bg)


def concept_c():
    """Strata: the ancestry of the letter A."""
    bands = [  # top edge y, colour light, colour dark
        (0, "#FFE6B0", "#FFD07A"),
        (300, "#FFB27A", "#F58F57"),
        (500, "#E8624E", "#C9463A"),
        (680, "#8E3B6B", "#6C2754"),
        (840, "#3A1F55", "#22113A"),
    ]
    defs = []
    shapes = []
    for i, (top, lt, dk) in enumerate(bands):
        defs.append(f'''<linearGradient id="band{i}" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="{lt}"/>
    <stop offset="1" stop-color="{dk}"/>
  </linearGradient>''')
        if i == 0:
            shapes.append(f'<rect width="{W}" height="{W}" fill="url(#band0)"/>')
            continue
        amp = 26
        d = f"M 0 {top} C 220 {top - amp} 380 {top + amp} {C} {top} S 820 {top - amp} {W} {top} L {W} {W} L 0 {W} Z"
        shapes.append(f'<path d="{d}" fill="url(#band{i})" filter="url(#shadow)"/>')
        shapes.append(f'<path d="M 0 {top + 4} C 220 {top - amp + 4} 380 {top + amp + 4} {C} {top + 4} S 820 {top - amp + 4} {W} {top + 4}" fill="none" stroke="#fff" stroke-opacity="0.35" stroke-width="6"/>')
    # The thread of descent, from the asterisk up to A.
    thread = f'<path d="M {C} 930 C {C + 30} 820 {C - 30} 720 {C} 600 S {C + 20} 420 {C} 250" stroke="#FFE08A" stroke-width="10" fill="none" stroke-dasharray="2 22" stroke-linecap="round" opacity="0.9"/>'
    glyphs = [
        f'<path d="{glyph("serif", "A", C, 175, 230)}" fill="#3A1F55" filter="url(#shadow)"/>',
        f'<g transform="rotate(-28 {C} 402)"><path d="{glyph("serif", "Α", C, 402, 150)}" fill="#fff" filter="url(#softshadow)"/></g>',
        f'<path d="{glyph("sans-regular", chr(0x10900), C, 592, 130)}" fill="#fff" filter="url(#softshadow)"/>',
        # Ox head (the pictogram the alphabet's first letter descends from).
        f'''<g transform="translate({C} 762)" fill="none" stroke="#fff" stroke-width="16" stroke-linecap="round" stroke-linejoin="round" filter="url(#softshadow)">
  <path d="M -36 -20 C -36 30 -18 52 0 52 C 18 52 36 30 36 -20 Z"/>
  <path d="M -36 -20 C -70 -24 -84 -48 -80 -72"/>
  <path d="M 36 -20 C 70 -24 84 -48 80 -72"/>
</g>''',
        f'<g filter="url(#glow)" stroke="#FFD84D">{asterisk_path(C, 935, 46, 26)}</g>',
    ]
    return doc("\n".join(defs), "\n".join(shapes) + thread + "\n".join(glyphs), "")


CONCEPTS = {"a-family-tree": concept_a, "b-asterisk-bloom": concept_b, "c-strata": concept_c}
