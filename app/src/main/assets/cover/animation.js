    (() => {
      'use strict';
      const FPS = 10;
      const FRAMES = 100;              // 10 seconds, then an intentional restart.
      const FRAME_MS = 1000 / FPS;
      const ocean = document.getElementById('ocean');
      const cloud = document.getElementById('cloud');
      const farPlane = document.getElementById('far-plane');
      const nearPlane = document.getElementById('near-plane');
      const flecks = [...document.querySelectorAll('.fleck')];
      const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
      const TAU = Math.PI * 2;
      let lastFrame = -1;
      let startedAt = performance.now();
      let paused = false;

      function setFrame(frame) {
        frame = ((Math.floor(frame) % FRAMES) + FRAMES) % FRAMES;
        if (frame === lastFrame) return;
        lastFrame = frame;
        const u = frame / (FRAMES - 1);

        // Only these sampled positions reach the DOM; there is no in-between motion.
        const oceanX = -25 * u + 1.5 * Math.sin(TAU * u);
        const oceanY = 155 * u + 2 * Math.sin(TAU * u * 1.2);
        ocean.setAttribute('transform', `translate(${oceanX.toFixed(2)} ${oceanY.toFixed(2)})`);

        // The nearby cloud crosses the picture more than twice as fast as the distant sea.
        const cloudX = -68 * u + 3 * Math.sin(TAU * u * 0.9);
        const cloudY = 385 * u + 4 * Math.sin(TAU * u * 1.1);
        const cloudScale = 1 + 0.055 * u;
        cloud.setAttribute('transform',
          `translate(${cloudX.toFixed(2)} ${cloudY.toFixed(2)}) ` +
          `translate(360 710) scale(${cloudScale.toFixed(4)}) translate(-360 -710)`);

        const farX = 18 * (Math.sin(TAU * u * 1.15 + 0.4) - Math.sin(0.4)) + 11 * u;
        const farY = 12 * Math.sin(TAU * u * 1.1) - 6 * u;
        const farAngle = 0.75 * Math.sin(TAU * u * 1.1);
        farPlane.setAttribute('transform',
          `translate(${farX.toFixed(2)} ${farY.toFixed(2)}) rotate(${farAngle.toFixed(3)} 420 1180)`);

        const nearX = 2.6 * Math.sin(TAU * u * 0.9);
        const nearY = 2.0 * (Math.sin(TAU * u * 1.1 + 0.2) - Math.sin(0.2));
        const nearAngle = 0.14 * Math.sin(TAU * u);
        nearPlane.setAttribute('transform',
          `translate(${nearX.toFixed(2)} ${nearY.toFixed(2)}) rotate(${nearAngle.toFixed(3)} 500 1780)`);

        for (const [i, fleck] of flecks.entries()) {
          const phase = Number(fleck.dataset.phase);
          const period = Number(fleck.dataset.period);
          const max = Number(fleck.dataset.max);
          const wave = Math.sin(TAU * (frame + phase) / period);
          const handVariation = 0.83 + 0.17 * Math.sin((frame + phase) * 2.17 + i * 0.71);
          const opacity = max * Math.pow(Math.max(0, wave), 1.8) * handVariation;
          fleck.setAttribute('opacity', opacity.toFixed(3));
          const motion = Number(fleck.dataset.motion || 1);
          const trembleX = Math.round(Math.sin((frame + phase) * 0.81) * motion);
          const trembleY = Math.round(Math.cos((frame + phase) * 0.57) * motion * 0.65);
          fleck.setAttribute('transform', `translate(${trembleX} ${trembleY})`);
        }
      }

      function tick(now) {
        if (!paused && !reducedMotion.matches) {
          setFrame(Math.floor(((now - startedAt) % 10000) / FRAME_MS));
        }
        requestAnimationFrame(tick);
      }

      const onMotionPreferenceChange = () => {
        startedAt = performance.now();
        setFrame(0);
      };
      if (reducedMotion.addEventListener) reducedMotion.addEventListener('change', onMotionPreferenceChange);
      else if (reducedMotion.addListener) reducedMotion.addListener(onMotionPreferenceChange);
      document.addEventListener('visibilitychange', () => {
        if (!document.hidden) startedAt = performance.now();
      });

      // Tiny preview API for integration/QA; no visible controls in the app artwork.
      window.windGliderAnimation = {
        setFrame,
        pause() { paused = true; },
        play() { paused = false; startedAt = performance.now(); },
        get fps() { return FPS; },
        get durationMs() { return 10000; }
      };
      setFrame(0);
      requestAnimationFrame(tick);
    })();
