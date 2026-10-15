"""Completes the entry-screen store screenshot.

Robolectric draws the app's real Compose UI but leaves the WebView blank. This script renders the
HTML the app gave that WebView (saved by AppScreensTest as 2-entry.html) in headless Chromium,
the engine Android's WebView uses, and pastes it into the blank area of 2-entry.png.

usage: python3 compose_entry_screenshot.py <chrome-binary> <store/raw dir> <out.png>
"""
import asyncio
import pathlib
import sys

from PIL import Image
from playwright.async_api import async_playwright

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets/entry"
# Layout of the entry screen as captured (411x731dp at 420dpi): 64dp top bar, 80dp bottom bar.
SCALE, TOP, BOTTOM = 2.625, 64, 80


async def render(chrome, html, width_dp, height_dp, out):
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path=chrome, args=["--no-sandbox"])
        page = await browser.new_page(
            viewport={"width": width_dp, "height": height_dp}, device_scale_factor=SCALE, is_mobile=True,
        )

        async def assets(route):
            name = route.request.url.split("/assets/entry/")[1]
            kind = "text/css" if name.endswith(".css") else "application/javascript"
            await route.fulfill(body=(ASSETS / name).read_text(), content_type=kind)

        async def document(route):
            await route.fulfill(body=html, content_type="text/html; charset=utf-8")

        async def no_images(route):
            await route.fulfill(status=204, body="")

        await page.route("https://appassets.androidplatform.net/assets/entry/*", assets)
        await page.route("https://*.wikimedia.org/**", no_images)
        await page.route("https://en.wiktionary.org/wiki/*", document)
        await page.goto("https://en.wiktionary.org/wiki/entry")
        await page.wait_for_timeout(300)
        await page.screenshot(path=out)
        await browser.close()


def main():
    chrome, raw, out = sys.argv[1], pathlib.Path(sys.argv[2]), sys.argv[3]
    shell = Image.open(raw / "2-entry.png").convert("RGB")
    width_dp = round(shell.width / SCALE)
    height_dp = round(shell.height / SCALE) - TOP - BOTTOM
    tmp = raw / "2-entry.webview.png"
    asyncio.run(render(chrome, (raw / "2-entry.html").read_text(), width_dp, height_dp, str(tmp)))
    shell.paste(Image.open(tmp).convert("RGB"), (0, round(TOP * SCALE)))
    shell.save(out)
    tmp.unlink()


if __name__ == "__main__":
    main()
