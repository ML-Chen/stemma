"""Builds the Play Store graphics in store/ from the screenshots that AppScreensTest saves.

usage: python3 store/tools/make_store_assets.py <chrome-binary>
Needs: pip install playwright fonttools pillow
"""
import asyncio
import base64
import pathlib
import subprocess
import sys

from PIL import Image
from playwright.async_api import async_playwright

STORE = pathlib.Path(__file__).resolve().parents[1]
RAW = STORE / "raw"
SHOTS = STORE / "screenshots"
ORDER = ["1-explore", "2-entry", "4-saved", "5-history", "6-explore-dark", "3-search"]


def data_uri(path):
    return "data:image/png;base64," + base64.b64encode(path.read_bytes()).decode()


async def feature_graphic(chrome, html, out):
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=chrome, args=["--no-sandbox"])
        page = await browser.new_page(viewport={"width": 1024, "height": 500})
        await page.set_content(html)
        await page.wait_for_timeout(300)
        await page.screenshot(path=str(out))
        await browser.close()


def main():
    chrome = sys.argv[1]
    # 1. App icon (512x512) and the Android launcher icon layers.
    subprocess.run([sys.executable, str(STORE / "tools/logo/final.py"), chrome], check=True)
    icon = STORE / "icon-512.png"

    # 2. Phone screenshots, 1080x1920. The entry screen gets its WebView area filled in first.
    entry = RAW / "2-entry.composited.png"
    subprocess.run([sys.executable, str(STORE / "tools/compose_entry_screenshot.py"), chrome, str(RAW), str(entry)], check=True)
    SHOTS.mkdir(exist_ok=True)
    for i, name in enumerate(ORDER, start=1):
        source = entry if name == "2-entry" else RAW / f"{name}.png"
        label = name.split("-", 1)[1]
        Image.open(source).convert("RGB").resize((1080, 1920), Image.LANCZOS).save(SHOTS / f"phone-{i}-{label}.png")

    # 3. Feature graphic, 1024x500.
    template = (STORE / "tools/feature-graphic.html").read_text()
    html = template.replace("ICON", data_uri(icon)).replace("SHOT", data_uri(SHOTS / "phone-2-entry.png"))
    out = STORE / "feature-graphic.png"
    asyncio.run(feature_graphic(chrome, html, out))
    Image.open(out).convert("RGB").save(out)
    print("wrote", icon, out, *sorted(SHOTS.iterdir()), sep="\n  ")


if __name__ == "__main__":
    main()
