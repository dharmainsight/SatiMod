(function (root) {
  'use strict';
  const api = {
    classify(dx, dy, elapsed, fullscreen) {
      if (elapsed > 1100 || elapsed < 20 || Math.abs(dy) < 70 || Math.abs(dy) < Math.abs(dx) * 1.6) return null;
      return fullscreen ? (dy > 0 ? 'exit' : null) : (dy < 0 ? 'enter' : null);
    },
    clampSeek(time, delta, duration) { return Math.max(0, Math.min(Number.isFinite(duration) ? duration : Infinity, time + delta)); },
    youtubeUrl(value) {
      try { const u = new URL(value); return u.protocol === 'https:' && ['m.youtube.com', 'www.youtube.com', 'youtube.com', 'youtu.be'].includes(u.hostname); } catch (_) { return false; }
    }
  };
  root.FlowGestures = api;
  if (typeof module !== 'undefined') module.exports = api;
})(globalThis);
