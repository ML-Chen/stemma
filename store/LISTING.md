# Google Play listing and Play Console answers for Stemma

Copy these into Play Console. Text lengths are within Play's limits (name 30, short description 80, full description 4,000 characters).

## Store listing (Grow → Store presence → Main store listing)

**App name** (28 characters)

```
Stemma: Etymology Dictionary
```

**Short description** (74 characters)

```
Word origins and definitions in every language, including proto-languages.
```

**Full description**

```
Stemma is a fast, clean reader for Wiktionary, the free dictionary written by volunteers. Wiktionary traces where words come from, step by step, back to reconstructed proto-languages such as Proto-Indo-European, and it has entries for words in thousands of languages, from Arabic and Inuktitut to Yucatec Maya. Stemma shows all of it in a layout made for phones.

A stemma is a family tree of descent. The asterisk at the centre of the icon is the mark linguists put before a reconstructed word, like *wódr̥, the ancestor of "water".

Features
• Search with suggestions as you type
• Full entries: definitions, usage examples, pronunciations with audio, etymologies, translations and inflection tables
• Each language on a page gets its own collapsible section, and your preferred language opens first
• A Word of the Day and a random word on the Explore screen
• Save words and read them without an internet connection
• Reading history and recent searches, kept on your device
• Contents list, find in page and adjustable text size
• Light, Sepia, Dark and Black themes
• "Define" in the text-selection menu of other apps: select a word anywhere and look it up
• No ads, no account and no analytics

Content and licence
Definitions and other text come from English Wiktionary (en.wiktionary.org) and are available under the Creative Commons Attribution-ShareAlike 4.0 License. Every entry links to its Wiktionary page and list of contributors.

Stemma is an independent app. It is not made, endorsed or sponsored by the Wikimedia Foundation. Wiktionary is a trademark of the Wikimedia Foundation.
```

**Graphics** (all in this folder)

| Play Console field | File | Size |
|---|---|---|
| App icon | `icon-512.png` | 512 × 512, 32-bit PNG |
| Feature graphic | `feature-graphic.png` | 1024 × 500 |
| Phone screenshots | `screenshots/phone-1-explore.png` … `phone-6-search.png` | 1080 × 1920 each |

The screenshots show the app's real screens with real Wiktionary content, but they were rendered on a computer (the app running under Robolectric, and the entry page rendered by Chromium, the engine Android's WebView uses) rather than captured on a phone. Replacing them with screenshots from your own phone is optional.

**Category:** Books & Reference. **Tags:** Dictionary, Reference. **Contact email:** required by Play; use the address you want users to see.

## App content (Policy and programs → App content)

**Privacy policy:** host `privacy-policy.html` at a public URL and paste the URL. First replace `[your contact email]` in the file with a real address. GitHub Pages, Google Sites or any web host works.

**Ads:** No, the app does not contain ads.

**App access:** All functionality is available without special access (no login).

**Target audience and content:** select the age groups 13–15, 16–17 and 18 and over. Selecting any age group under 13 brings in Play's Families policy requirements.

**Content rating (IARC questionnaire):** choose the category "Reference, News, or Educational". Answer no to violence, sexual content, gambling, controlled substances, user interaction and location sharing. Wiktionary has entries for swear words and slurs, so answer yes if the questionnaire asks whether the app can display crude language or profanity; that can raise the rating to Teen or an equivalent.

**Data safety** (conservative answers, based on Google's definitions in [Play Console Help](https://support.google.com/googleplay/android-developer/answer/10787469)):

- Does your app collect or share any of the required user data types? **Yes.** The app sends the words you search for and open to Wikimedia's servers, and Google counts any data an app sends off the device as "collected", even when it goes to a third party rather than the developer.
- Data type: **App activity → In-app search history.** Collected: yes. Shared: no (Google exempts transfers that follow a user-initiated action the user expects, such as typing a search). Processed ephemerally: no. Required (users can't turn it off). Purpose: App functionality.
- Is all of the user data collected by your app encrypted in transit? **Yes** (HTTPS only).
- Do you provide a way for users to request that their data is deleted? **No.** The developer stores no user data; on-device history can be cleared in Settings.

**Government apps, financial features, health, news:** No to each.

## Releasing

Personal developer accounts created after 13 November 2023 must run a closed test with at least 12 testers, opted in for 14 consecutive days, before Play allows a production release ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465)). Organisation accounts, and personal accounts created before that date, can release to production directly.

1. Testing → Closed testing → Create track. Upload `Stemma-1.0.0.aab`. Accept Play App Signing when asked (Google keeps the app signing key; your file `signing/upload-keystore.jks` is the upload key).
2. Add at least 12 testers (an email list or a Google Group), send them the opt-in link, and roll out the release.
3. After 14 days, apply for production access from the Dashboard, then promote the release to Production.
