"""Renders SVG logos to PNG with headless Chromium, and builds a comparison sheet.

usage: python3 render.py <chrome-binary> <out-dir> [concept-key ...]
"""
import asyncio
import pathlib
import sys

from playwright.async_api import async_playwright

sys.path.insert(0, str(pathlib.Path(__file__).parent))
from concepts import CONCEPTS  # noqa: E402


async def render_svgs(chrome, items, out_dir, size=1024):
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=chrome, args=["--no-sandbox"])
        page = await browser.new_page(viewport={"width": size, "height": size})
        for name, svg in items:
            (out_dir / f"{name}.svg").write_text(svg)
            await page.set_content(f'<html><body style="margin:0">{svg}</body></html>')
            await page.screenshot(path=str(out_dir / f"{name}.png"), clip={"x": 0, "y": 0, "width": size, "height": size})
        await browser.close()


async def sheet(chrome, names, labels, out):
    """Each concept as a Play Store icon (rounded square), a launcher circle, and at small sizes."""
    cells = []
    for name, label in zip(names, labels):
        src = f"{name}.png"
        cells.append(f'''<div class="cell">
  <img class="big" src="{src}">
  <div class="row"><img class="circle" src="{src}"><img class="s96" src="{src}"><img class="s48" src="{src}"><img class="s32" src="{src}"></div>
  <div class="label">{label}</div>
</div>''')
    html = f'''<html><head><style>
body {{ margin:0; background:#ECEAE4; font-family: Inter, sans-serif; display:flex; gap:44px; padding:44px; }}
.cell {{ display:flex; flex-direction:column; align-items:center; gap:22px; }}
.big {{ width:420px; height:420px; border-radius:92px; box-shadow:0 10px 30px rgba(0,0,0,.18); }}
.row {{ display:flex; align-items:center; gap:22px; }}
.circle {{ width:120px; height:120px; border-radius:50%; }}
.s96 {{ width:96px; height:96px; border-radius:22px; }}
.s48 {{ width:48px; height:48px; border-radius:11px; }}
.s32 {{ width:32px; height:32px; border-radius:8px; }}
.label {{ font-size:26px; font-weight:600; color:#333; }}
</style></head><body>{"".join(cells)}</body></html>'''
    page_path = out.parent / "sheet.html"
    page_path.write_text(html)
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=chrome, args=["--no-sandbox"])
        page = await browser.new_page(viewport={"width": 44 + len(names) * 464, "height": 720}, device_scale_factor=1)
        await page.goto(page_path.as_uri())
        await page.wait_for_timeout(300)
        await page.screenshot(path=str(out), full_page=True)
        await browser.close()


def main():
    chrome, out_dir = sys.argv[1], pathlib.Path(sys.argv[2])
    keys = sys.argv[3:] or list(CONCEPTS)
    out_dir.mkdir(parents=True, exist_ok=True)
    items = [(k, CONCEPTS[k]()) for k in keys]
    asyncio.run(render_svgs(chrome, items, out_dir))
    labels = [k.split("-", 1)[0].upper() + ": " + k.split("-", 1)[1].replace("-", " ").capitalize() for k in keys]
    asyncio.run(sheet(chrome, keys, labels, out_dir / "sheet.png"))
    print("wrote", out_dir / "sheet.png")


if __name__ == "__main__":
    main()
