(() => {
  'use strict';

  const DURATION_MS = 10000;
  const ocean = document.getElementById('ocean');
  const cloud = document.getElementById('cloud');
  const farPlane = document.getElementById('far-plane');
  const nearPlane = document.getElementById('near-plane');
  const flecks = [...document.querySelectorAll('.fleck')];
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  let startedAt = performance.now();
  let paused = false;

  const lerp = (from, to, progress) => from + (to - from) * progress;
  const fmt = value => value.toFixed(3);

  // Every intermediate pose is calculated from elapsed time, without frame sampling.
  function setProgress(progress) {
    const u = Math.min(1, Math.max(0, Number(progress) || 0));
    const seconds = u * DURATION_MS / 1000;

    // The closer cloud travels about twice as far as the distant ocean.
    ocean.setAttribute('transform',
      `translate(${fmt(lerp(0, -16, u))} ${fmt(lerp(0, 108, u))})`);
    cloud.setAttribute('transform',
      `translate(${fmt(lerp(0, -29, u))} ${fmt(lerp(0, 215, u))})`);

    farPlane.setAttribute('transform',
      `translate(${fmt(lerp(0, 24, u))} ${fmt(lerp(0, -9, u))}) ` +
      `rotate(${fmt(lerp(0, 0.55, u))} 420 1180)`);
    nearPlane.setAttribute('transform',
      `translate(${fmt(lerp(0, 3.5, u))} ${fmt(lerp(0, -1.5, u))}) ` +
      `rotate(${fmt(lerp(0, 0.08, u))} 500 1780)`);

    // Short, linear fades make the painted marks live without any jitter.
    for (const fleck of flecks) {
      const phaseSeconds = Number(fleck.dataset.phase) / 10;
      const periodSeconds = Number(fleck.dataset.period) / 10;
      const maxOpacity = Number(fleck.dataset.max);
      const motion = Number(fleck.dataset.motion || 1);
      const localTime = (seconds + phaseSeconds) % periodSeconds;
      const activeSeconds = Math.min(2.8, periodSeconds * 0.58);
      const fadeSeconds = Math.min(0.7, activeSeconds * 0.35);
      const envelope = localTime < activeSeconds
        ? Math.max(0, Math.min(1, localTime / fadeSeconds,
          (activeSeconds - localTime) / fadeSeconds))
        : 0;
      fleck.setAttribute('opacity', fmt(maxOpacity * envelope));
      fleck.setAttribute('transform',
        `translate(${fmt(motion * localTime / periodSeconds)} ` +
        `${fmt(-motion * localTime / periodSeconds * 0.4)})`);
    }
  }

  function tick(now) {
    if (!paused && !reducedMotion.matches) {
      setProgress(((now - startedAt) % DURATION_MS) / DURATION_MS);
    }
    requestAnimationFrame(tick);
  }

  function onMotionPreferenceChange() {
    startedAt = performance.now();
    setProgress(0);
  }
  if (reducedMotion.addEventListener) {
    reducedMotion.addEventListener('change', onMotionPreferenceChange);
  } else if (reducedMotion.addListener) {
    reducedMotion.addListener(onMotionPreferenceChange);
  }
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) startedAt = performance.now();
  });

  window.windGliderAnimation = {
    setProgress,
    pause() { paused = true; },
    play() { paused = false; startedAt = performance.now(); },
    get durationMs() { return DURATION_MS; }
  };
  setProgress(0);
  requestAnimationFrame(tick);
})();
