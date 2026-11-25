"""Builds Relay Ribbon, the app's display face, from the SVG paths in glyphs.py.

    uv run --no-project --with fonttools --with skia-pathops python android/tools/relay-ribbon/build.py

Writes android/app/src/main/res/font/relay_ribbon.ttf.
"""

from __future__ import annotations

from pathlib import Path

import pathops
from fontTools.agl import UV2AGL
from fontTools.pens.cu2quPen import Cu2QuPen
from fontTools.feaLib.builder import addOpenTypeFeaturesFromString
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.svgLib.path import SVGPath
from fontTools.ttLib import newTable
from fontTools.ttLib.tables._k_e_r_n import KernTable_format_0

from glyphs import ACCENTED_SPECS, BASE_GLYPHS, KERNING_PAIRS, MARKS

UPM = 1000
BASELINE_SVG = 800
ASCENT = 960
DESCENT = -240
CAP_HEIGHT = 700
X_HEIGHT = 522

FONT = Path(__file__).parents[2] / 'app/src/main/res/font/relay_ribbon.ttf'


def glyph_name_for(ch: str) -> str:
    cp = ord(ch)
    if cp in UV2AGL:
        return UV2AGL[cp]
    if cp <= 0xFFFF:
        return f'uni{cp:04X}'
    return f'u{cp:05X}'


def svg_path_to_pathops(d: str, dx: float = 0.0, dy: float = 0.0) -> pathops.Path:
    """Parse an SVG path string (y-down) into y-up TrueType coords and simplify even-odd."""
    p = pathops.Path()
    p.fillType = pathops.FillType.EVEN_ODD
    if not d.strip():
        return p
    pen = p.getPen()
    # Map (x, y) -> (x + dx, BASELINE_SVG - (y + dy))
    tpen = TransformPen(pen, (1, 0, 0, -1, dx, BASELINE_SVG - dy))
    SVGPath.fromstring(f'<path d="{d}"/>').draw(tpen)
    return pathops.simplify(p, fix_winding=True, clockwise=True)


def build_base_pathops(spec: dict) -> pathops.Path:
    if 'parts' in spec:
        paths = [svg_path_to_pathops(part) for part in spec['parts'] if part.strip()]
        if not paths:
            return pathops.Path()
        merged = pathops.Path()
        pathops.union(paths, merged.getPen())
        return pathops.simplify(merged, fix_winding=True, clockwise=True)
    return svg_path_to_pathops(spec.get('d', ''))


def pathops_to_ttglyph(skia_path: pathops.Path):
    tt_pen = TTGlyphPen(None)
    if len(skia_path) > 0:
        cu2qu_pen = Cu2QuPen(tt_pen, max_err=0.75, reverse_direction=False)
        skia_path.draw(cu2qu_pen)
    return tt_pen.glyph()


def build_notdef() -> tuple[int, pathops.Path]:
    d = 'M 70 100 H 470 V 800 H 70 Z M 130 160 V 740 H 410 V 160 Z'
    return 540, svg_path_to_pathops(d)


def expand_kerning(all_chars: set[str], base_of: dict[str, str]) -> dict[tuple[str, str], int]:
    """Propagate kerning from base letters to accented forms safely."""
    tall_marks = {'í', 'ì', 'î', 'ï', 'ď', 'ť', 'ľ', 'ĺ'}
    overhanging_left = {'T', 'F', 'V', 'W', 'Y'}
    by_base: dict[str, list[str]] = {}
    for ch in all_chars:
        b = base_of.get(ch, ch)
        by_base.setdefault(b, []).append(ch)
        if b == '\u0131':
            by_base.setdefault('i', []).append(ch)

    expanded: dict[tuple[str, str], int] = {}
    for (l_base, r_base), val in KERNING_PAIRS.items():
        lefts = by_base.get(l_base, [l_base] if l_base in all_chars else [])
        rights = by_base.get(r_base, [r_base] if r_base in all_chars else [])
        for lch in lefts:
            for rch in rights:
                if lch in overhanging_left and rch in tall_marks:
                    continue
                if lch in all_chars and rch in all_chars:
                    expanded[(lch, rch)] = val
    return expanded


def compile_font():
    glyph_order = ['.notdef']
    cmap: dict[int, str] = {}
    tt_glyphs: dict[str, object] = {}
    metrics: dict[str, tuple[int, int]] = {}

    notdef_w, notdef_path = build_notdef()
    tt_glyphs['.notdef'] = pathops_to_ttglyph(notdef_path)
    metrics['.notdef'] = (notdef_w, 70)

    base_paths: dict[str, pathops.Path] = {}
    all_chars: dict[str, tuple[int, pathops.Path]] = {}
    base_of: dict[str, str] = {}

    for ch, spec in BASE_GLYPHS.items():
        p = build_base_pathops(spec)
        base_paths[ch] = p
        all_chars[ch] = (spec['w'], p)
        base_of[ch] = ch

    for ch, (base_ch, mark_name, dx, dy, extra_w) in ACCENTED_SPECS.items():
        base_spec = BASE_GLYPHS[base_ch]
        base_p = base_paths[base_ch]
        mark_p = svg_path_to_pathops(MARKS[mark_name], dx=dx, dy=dy)
        merged = pathops.Path()
        pathops.union([base_p, mark_p], merged.getPen())
        merged = pathops.simplify(merged, fix_winding=True, clockwise=True)
        all_chars[ch] = (base_spec['w'] + extra_w, merged)
        base_of[ch] = base_ch

    char_to_gname: dict[str, str] = {}
    for ch, (adv, skia_path) in all_chars.items():
        gname = glyph_name_for(ch)
        if gname in tt_glyphs:
            raise ValueError(f'Duplicate glyph name {gname} for {ch!r}')
        glyph_order.append(gname)
        cmap[ord(ch)] = gname
        char_to_gname[ch] = gname

        tt_glyph = pathops_to_ttglyph(skia_path)
        tt_glyphs[gname] = tt_glyph

        bp = BoundsPen(None)
        if len(skia_path) > 0:
            skia_path.draw(bp)
            lsb = int(round(bp.bounds[0])) if bp.bounds else 0
        else:
            lsb = 0
        metrics[gname] = (int(adv), lsb)

    fb = FontBuilder(UPM, isTTF=True)
    fb.setupGlyphOrder(glyph_order)
    fb.setupCharacterMap(cmap)
    fb.setupGlyf(tt_glyphs)
    fb.setupHorizontalMetrics(metrics)
    fb.setupHorizontalHeader(ascent=ASCENT, descent=DESCENT, lineGap=0)
    fb.setupNameTable(
        {
            'familyName': 'Relay Ribbon',
            'styleName': 'Regular',
            'uniqueFontIdentifier': '1.000;RLAY;RelayRibbon-Regular',
            'fullName': 'Relay Ribbon',
            'version': 'Version 1.000',
            'psName': 'RelayRibbon-Regular',
            'designer': 'Relay',
            'description': 'Bespoke flared humanist display typeface inspired by the Relay mark.',
        }
    )
    fb.setupOS2(
        version=4,
        sTypoAscender=ASCENT,
        sTypoDescender=DESCENT,
        sTypoLineGap=0,
        usWinAscent=980,
        usWinDescent=250,
        sxHeight=X_HEIGHT,
        sCapHeight=CAP_HEIGHT,
        usWeightClass=500,
        usWidthClass=5,
        fsType=0,
        fsSelection=(1 << 6) | (1 << 7),  # REGULAR | USE_TYPO_METRICS
        achVendID='RLAY',
    )
    fb.setupPost(italicAngle=0, underlinePosition=-100, underlineThickness=50)
    fb.setupMaxp()

    # Kerning: both OpenType GPOS (HarfBuzz/Compose/Browsers) and legacy kern table
    expanded_kern = expand_kerning(set(all_chars.keys()), base_of)
    fea_lines = ['feature kern {']
    kern_table_pairs: dict[tuple[str, str], int] = {}
    for (lch, rch), val in sorted(expanded_kern.items()):
        lg = char_to_gname[lch]
        rg = char_to_gname[rch]
        fea_lines.append(f'    pos {lg} {rg} {val};')
        kern_table_pairs[(lg, rg)] = val
    fea_lines.append('} kern;')
    addOpenTypeFeaturesFromString(fb.font, '\n'.join(fea_lines))

    kern_table = newTable('kern')
    kern_table.version = 0
    subtable = KernTable_format_0()
    subtable.coverage = 1  # horizontal kerning
    subtable.kernTable = kern_table_pairs
    kern_table.kernTables = [subtable]
    fb.font['kern'] = kern_table

    fb.font.save(FONT)
    print(f'Built {FONT} ({FONT.stat().st_size} bytes, {len(glyph_order)} glyphs, {len(kern_table_pairs)} kern pairs)')


if __name__ == '__main__':
    compile_font()
