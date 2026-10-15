# Test fixtures

These files are responses saved from the English Wiktionary APIs (en.wiktionary.org) on
12 October 2026, used so the tests run without the network. Their content is from Wiktionary,
written by Wiktionary's contributors, and is available under the
[Creative Commons Attribution-ShareAlike 4.0 License](https://creativecommons.org/licenses/by-sa/4.0/).
The contributors to each entry are listed in its history, for example
https://en.wiktionary.org/w/index.php?title=dog&action=history.

| File | Source |
|---|---|
| `mobilehtml_<word>.html` | `https://en.wiktionary.org/api/rest_v1/page/mobile-html/<word>` |
| `def_<word>.json` | `https://en.wiktionary.org/api/rest_v1/page/definition/<word>` |
| `wotd.json` | the `Wiktionary:Word of the day` page, via the Action API |
| `search_seren.json` | `https://en.wiktionary.org/w/rest.php/v1/search/title?q=seren` |
| `random.json` | a random-page query from the Action API |
