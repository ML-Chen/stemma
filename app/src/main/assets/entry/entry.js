/* Behaviour for Wiktionary entries shown in the app's WebView.
   The page talks to the app through window.StemmaBridge (see EntryJsBridge.kt). */
(function () {
  'use strict';

  var bridge = window.StemmaBridge;
  var currentPath = decodeURIComponent(location.pathname);
  var player = null;
  var playingButton = null;

  function openAncestors(el) {
    for (var p = el; p; p = p.parentElement) {
      if (p.tagName === 'DETAILS') p.open = true;
    }
  }

  function findTarget(id) {
    if (!id) return null;
    var el = document.getElementById(id);
    if (!el) {
      // Fragments sometimes arrive with spaces instead of underscores.
      el = document.getElementById(id.replace(/ /g, '_'));
    }
    return el;
  }

  /** Opens any collapsed blocks around the element with this id and scrolls to it. */
  function jump(id, smooth) {
    var el = findTarget(id);
    if (!el) return false;
    openAncestors(el);
    var top = el.getBoundingClientRect().top + window.scrollY - 8;
    window.scrollTo({ top: Math.max(0, top), behavior: smooth ? 'smooth' : 'auto' });
    return true;
  }

  function stopAudio() {
    if (player) {
      player.pause();
      player = null;
    }
    if (playingButton) {
      playingButton.classList.remove('playing');
      playingButton = null;
    }
  }

  function play(button) {
    var wasPlaying = button === playingButton;
    stopAudio();
    if (wasPlaying) return;
    var audio = new Audio(button.getAttribute('data-src'));
    player = audio;
    playingButton = button;
    button.classList.add('playing');
    audio.addEventListener('ended', stopAudio);
    audio.addEventListener('error', function () {
      button.classList.add('failed');
      stopAudio();
    });
    var started = audio.play();
    if (started && started.catch) {
      started.catch(function () {
        button.classList.add('failed');
        stopAudio();
      });
    }
  }

  document.addEventListener('click', function (event) {
    var audioButton = event.target.closest('button.wh-audio');
    if (audioButton) {
      event.preventDefault();
      play(audioButton);
      return;
    }

    var link = event.target.closest('a');
    if (!link) return;

    if (link.hasAttribute('data-jump')) {
      event.preventDefault();
      jump(link.getAttribute('data-jump'), true);
      return;
    }
    if (link.classList.contains('wh-more')) {
      event.preventDefault();
      if (bridge) bridge.showContents();
      return;
    }

    var href = link.getAttribute('href');
    if (!href) return;
    event.preventDefault();

    if (link.classList.contains('wh-external')) {
      if (bridge) bridge.openExternal(link.href);
      return;
    }

    if (href.charAt(0) === '#') {
      jump(decodeURIComponent(href.slice(1)), true);
      return;
    }

    var url;
    try {
      url = new URL(link.href);
    } catch (e) {
      return;
    }
    // A link to another section of this same page.
    if (url.host === location.host && decodeURIComponent(url.pathname) === currentPath && url.hash) {
      jump(decodeURIComponent(url.hash.slice(1)), true);
      return;
    }
    if (bridge) bridge.openLink(url.href);
  }, true);

  window.wh = {
    jump: function (id) { return jump(id, true); },
    stopAudio: stopAudio,
    setTheme: function (cls) {
      var root = document.documentElement;
      root.className = root.className.replace(/\bwh-(light|sepia|dark|black)\b/g, '').trim() + ' ' + cls;
    },
    scrollY: function () { return window.scrollY; }
  };

  var fragment = document.body.getAttribute('data-fragment');
  var restore = parseInt(document.body.getAttribute('data-scroll') || '0', 10);
  if (restore > 0) {
    // The app saves WebView.getScrollY(), which is in screen pixels; the page scrolls in CSS pixels.
    window.scrollTo(0, restore / (window.devicePixelRatio || 1));
  } else if (fragment) {
    jump(fragment, false);
  }
})();
