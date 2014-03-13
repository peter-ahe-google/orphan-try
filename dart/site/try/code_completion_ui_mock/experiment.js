var currentEntry;

var installedServerResults = false;

var isDisplayingResults = false;

(function() {
  "use strict";
  function hasModifier(/*KeyboardEvent*/ event) {
    return event.getModifierState("Alt") ||
        event.getModifierState("AltGraph") ||
        event.getModifierState("CapsLock") ||
        event.getModifierState("Control") ||
        event.getModifierState("Fn") ||
        event.getModifierState("Meta") ||
        event.getModifierState("NumLock") ||
        event.getModifierState("ScrollLock") ||
        event.getModifierState("Scroll") ||
        event.getModifierState("Win") ||
        event.getModifierState("Shift") ||
        event.getModifierState("SymbolLock") ||
        event.getModifierState("OS");
  }
  function onKeydown(/*KeyboardEvent*/ event) {
    if (!isDisplayingResults) {
      isDisplayingResults = true;
      document.querySelector('.dart-code-completion').style.display = 'block';
      installedServerResults = true;
      window.setTimeout(installServerResults, getRandomInt(1080, 2000));
      return;
    }
    if (!installedServerResults) {
      installedServerResults = true;
      window.setTimeout(installServerResults, getRandomInt(1080, 2000));
    }
    var keyCode = event.keyCode;
    var keyIdentifier = event.keyIdentifier;
    var modifier = hasModifier(event);
    if (!modifier) {
      if (keyCode === 40) return moveActive(event, 1);
      if (keyCode === 38) return moveActive(event, -1);
    }
    if (modifier) {
      if (keyCode === 40) return selectServerResults(event);
    }
    console.log([keyCode, keyIdentifier, modifier]);
    console.dir(event);
  }
  function moveActive(/*KeyboardEvent*/ event, /*int*/ distance) {
    var entries = document.querySelectorAll('.dart-static>.dart-entry');
    var activeIndex = -1;
    for (var i = 0; i < entries.length; i++) {
      if (entries[i].classList.contains('active')) {
        activeIndex = i;
        break;
      }
    }
    var newIndex = activeIndex + distance;
    if (0 <= newIndex && newIndex < entries.length) {
      var newEntry = entries[newIndex];
      currentEntry = newEntry;
    }
    if (0 <= newIndex && activeIndex !== -1) {
      entries[activeIndex].classList.remove('active');
    }
    var serverResults = document.querySelectorAll('.dart-server>.dart-entry');
    var serverResultCount = serverResults.length;
    if (serverResultCount > 0) {
      var cr = currentEntry.getBoundingClientRect();
      var sr = document.querySelector('.dart-static').getBoundingClientRect();
      if (cr.bottom - sr.top > sr.height) {
        if (cr.top - sr.top >= sr.height) {
          var entry = serverResults[0];
          entry.parentNode.removeChild(entry);
          currentEntry.parentNode.insertBefore(entry, currentEntry);
          currentEntry = entry;
          serverResultCount--;
        } else {
          console.log(cr.bottom - sr.top);
        }
        document.querySelector('.dart-static').style.height =
            String((cr.bottom - sr.top)) + "px";
      } else {
        currentEntry.scrollIntoView(false);
      }
    } else {
      currentEntry.scrollIntoView(false);
    }
    if (serverResultCount === 0) {
      // document.querySelector('.dart-static').classList.remove('dart-limited-height');
      // document.querySelector('.dart-static').style.height = '';
      document.querySelector('.dart-server').style.display = 'none';
    }
    if (currentEntry != null) {
      currentEntry.classList.add('active');
    }
  }
  function selectServerResults(/*KeyboardEvent*/ event) {
    var entries = document.querySelectorAll('.dart-static>.dart-entry.active');
    for (var i = 0; i < entries.length; i++) {
      entries[i].classList.remove('active');
    }
    return moveActive(event, 4);
  }
  function installServerResults() {
    var mockServerResults = document.querySelectorAll('.mock-data>.dart-entry');
    var results = document.querySelector('.dart-server');
    results.style.display = 'block';
    for (var i = 0; i < mockServerResults.length; i++) {
      var result = mockServerResults[i];
      result.parentNode.removeChild(result);
      results.appendChild(result);
    }
  }
  function getRandomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
  }
  document.body.addEventListener('keydown', onKeydown);
})();
