// Tests de la dynamique de vol du rejeu 3D — exécuter : node --test 'feature/replay3d/src/test/js/*.test.mjs'
import {test} from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname, resolve} from 'node:path';
import {performance} from 'node:perf_hooks';
import {buildAttitudeTrack, sampleAttitude} from '../../main/assets/replay3d/flight-dynamics.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const IGC = resolve(here, '../../../../../samples/saint-martin-de-londres-vol-synthetique.igc');
const G = 9.80665, DEG = 180 / Math.PI, RAD = Math.PI / 180, R_EARTH = 6371008.8;
const LAT0 = 43.8, LON0 = 3.78;

// ---------------------------------------------------------------- utilitaires

function parseIgc(text) {
    const pts = [];
    let t0 = null, prev = -1, dayOffset = 0;
    for (const line of text.split(/\r?\n/)) {
        const m = /^B(\d{2})(\d{2})(\d{2})(\d{2})(\d{5})([NS])(\d{3})(\d{5})([EW])([AV])(\d{5}|-\d{4})(\d{5}|-\d{4})/.exec(line);
        if (!m) continue;
        let sec = +m[1] * 3600 + +m[2] * 60 + +m[3];
        if (prev >= 0 && sec + dayOffset < prev - 43200) dayOffset += 86400;
        sec += dayOffset;
        prev = sec;
        if (t0 === null) t0 = sec;
        const lat = (+m[4] + +m[5] / 60000) * (m[6] === 'S' ? -1 : 1);
        const lon = (+m[7] + +m[8] / 60000) * (m[9] === 'W' ? -1 : 1);
        pts.push({lat, lon, alt: +m[12], t: sec - t0});
    }
    return pts;
}

function rng(seed) { // mulberry32 + Box-Muller
    let a = seed >>> 0;
    const u = () => { a = (a + 0x6D2B79F5) >>> 0; let t = a; t = Math.imul(t ^ (t >>> 15), t | 1); t ^= t + Math.imul(t ^ (t >>> 7), t | 61); return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
    return () => Math.sqrt(-2 * Math.log(u() + 1e-12)) * Math.cos(2 * Math.PI * u());
}

const ky = R_EARTH * RAD, kx = ky * Math.cos(LAT0 * RAD);
const toLL = (x, y, z, t) => ({lat: LAT0 + y / ky, lon: LON0 + x / kx, alt: z, t});
const toXY = (p) => ({x: (p.lon - LON0) * kx, y: (p.lat - LAT0) * ky});

/** Trace synthétique : fonction (t) → {x, y, z} en m, échantillonnée à 1 Hz, bruit gaussien σ. */
function synth(fn, duration, sigma, seed = 1) {
    const g = rng(seed);
    const pts = [];
    for (let t = 0; t <= duration; t++) {
        const {x, y, z} = fn(t);
        pts.push(toLL(x + sigma * g(), y + sigma * g(), z + sigma * g(), t));
    }
    return pts;
}

const V = 25, R = 150, W = V / R; // 90 km/h, rayon 150 m
const spiral = (t) => ({x: R * Math.sin(W * t), y: R * Math.cos(W * t), z: 1000 + 1.5 * t}); // horaire → virage à droite

function sampleSeries(track, from, to, dt = 1 / 60) {
    const out = {t: [], heading: [], bank: [], pitch: [], lat: [], lon: [], alt: [], speed: []};
    const pose = {};
    for (let t = from; t <= to + 1e-9; t += dt) {
        sampleAttitude(track, t, pose);
        out.t.push(t);
        for (const k of ['heading', 'bank', 'pitch', 'lat', 'lon', 'alt', 'speed']) out[k].push(pose[k]);
    }
    return out;
}

const mean = (a) => a.reduce((s, v) => s + v, 0) / a.length;
const std = (a) => { const m = mean(a); return Math.sqrt(mean(a.map(v => (v - m) ** 2))); };
const wrap180 = (d) => d - 360 * Math.floor((d + 180) / 360);
const maxOf = (a) => a.reduce((m, v) => (v > m ? v : m), -Infinity);
const maxAbs = (a) => a.reduce((m, v) => Math.max(m, Math.abs(v)), 0);
function unwrapDeg(a) { const o = [a[0]]; for (let i = 1; i < a.length; i++) o.push(o[i - 1] + wrap180(a[i] - a[i - 1])); return o; }
function secondDiffStd(a, dt) { const d = []; for (let i = 1; i < a.length - 1; i++) d.push((a[i + 1] - 2 * a[i] + a[i - 1]) / (dt * dt)); return std(d); }

// Reproduction de l'approche naïve actuelle de replay.mjs (computeKinematics + flightPosition), pour comparaison.
function haversine(a, b) { const l1 = a.lat * RAD, l2 = b.lat * RAD, dl = (b.lon - a.lon) * RAD; const h = Math.sin((l2 - l1) / 2) ** 2 + Math.cos(l1) * Math.cos(l2) * Math.sin(dl / 2) ** 2; return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h)); }
function bearing(a, b) { const l1 = a.lat * RAD, l2 = b.lat * RAD, dl = (b.lon - a.lon) * RAD; return (Math.atan2(Math.sin(dl) * Math.cos(l2), Math.cos(l1) * Math.sin(l2) - Math.sin(l1) * Math.cos(l2) * Math.cos(dl)) * DEG + 360) % 360; }
function naive(pointsIn) {
    const points = pointsIn.map(p => ({...p}));
    const n = points.length;
    let sLo = 0, sHi = 0;
    for (let i = 0; i < n; i++) {
        const t = points[i].t;
        while (points[sLo].t < t - 2 && sLo < i) sLo++;
        while (sHi < n - 1 && points[sHi + 1].t <= t + 2) sHi++;
        const ds = points[sHi].t - points[sLo].t;
        points[i].speed = ds > 0.5 ? haversine(points[sLo], points[sHi]) / ds * 3.6 : 0;
    }
    return (t) => {
        let lo = 0, hi = n - 1;
        while (hi - lo > 1) { const mid = (lo + hi) >> 1; if (points[mid].t <= t) lo = mid; else hi = mid; }
        const a = points[lo], b = points[hi];
        const heading = bearing(points[Math.max(0, lo - 3)], points[Math.min(n - 1, hi + 3)]);
        const i0 = Math.max(0, lo - 5), i1 = Math.min(n - 1, lo + 5);
        const turn = wrap180(bearing(a, points[i1]) - bearing(points[i0], a));
        const omega = turn * RAD / Math.max(1, (points[i1].t - points[i0].t) / 2);
        const bank = Math.atan((a.speed / 3.6) * omega / 9.81) * DEG;
        return {heading, bank: Math.max(-55, Math.min(55, Number.isFinite(bank) ? bank : 0))};
    };
}

// ---------------------------------------------------------------- tests

const igcPoints = parseIgc(readFileSync(IGC, 'utf8'));

test('trace IGC réelle : pré-calcul rapide, continuité à 60 Hz, écart aux points GPS, jitter vs naïf', () => {
    assert.ok(igcPoints.length > 6000, `points IGC : ${igcPoints.length}`);
    buildAttitudeTrack(igcPoints); // chauffe JIT
    const t0 = performance.now();
    const track = buildAttitudeTrack(igcPoints);
    const buildMs = performance.now() - t0;
    assert.ok(buildMs < 300, `pré-calcul ${buildMs.toFixed(1)} ms`);

    const dt = 1 / 60;
    const s = sampleSeries(track, track.t0, track.t1, dt);
    let maxJumpRatio = 0, maxHeadRate = 0;
    for (let i = 1; i < s.t.length; i++) {
        const d = Math.hypot((s.lat[i] - s.lat[i - 1]) * ky, (s.lon[i] - s.lon[i - 1]) * kx, s.alt[i] - s.alt[i - 1]);
        const bound = 3 * (Math.max(s.speed[i], s.speed[i - 1]) / 3.6 + 1) * dt;
        maxJumpRatio = Math.max(maxJumpRatio, d / bound);
        maxHeadRate = Math.max(maxHeadRate, Math.abs(wrap180(s.heading[i] - s.heading[i - 1])) / dt);
    }
    assert.ok(maxJumpRatio <= 1, `saut de position / borne = ${maxJumpRatio.toFixed(2)}`);
    assert.ok(maxHeadRate <= 45, `taux de cap max ${maxHeadRate.toFixed(1)} °/s`);
    let maxRoll = 0;
    for (let i = 1; i < s.t.length; i++) maxRoll = Math.max(maxRoll, Math.abs(s.bank[i] - s.bank[i - 1]) / dt);
    assert.ok(maxRoll <= 30.0001, `taux de roulis max ${maxRoll.toFixed(2)} °/s`);
    assert.ok(maxAbs(s.bank) <= 60);
    assert.ok(maxAbs(s.pitch) <= 20);

    // écart horizontal aux points GPS
    const pose = {};
    const errs = igcPoints.map(p => {
        sampleAttitude(track, p.t, pose);
        return Math.hypot((pose.lat - p.lat) * ky, (pose.lon - p.lon) * kx);
    }).sort((a, b) => a - b);
    const p95 = errs[Math.floor(errs.length * 0.95)];
    assert.ok(p95 < 5, `écart p95 ${p95.toFixed(2)} m`);

    // spirales : échantillons en vol avec |taux de virage| > 6 °/s
    const turning = [];
    for (let t = track.t0; t <= track.t1; t += 1) {
        sampleAttitude(track, t, pose);
        if (Math.abs(pose.turnRate) > 6 && pose.speed > 50) turning.push(Math.abs(pose.bank));
    }

    // jitter : naïf vs nouveau (même échantillonnage 60 Hz, cap déroulé)
    const nv = naive(igcPoints);
    const nh = [], nb = [];
    for (const t of s.t) { const q = nv(t); nh.push(q.heading); nb.push(q.bank); }
    const stats = {
        buildMs: +buildMs.toFixed(1),
        points: igcPoints.length,
        ecartGpsMedian_m: +errs[errs.length >> 1].toFixed(2), ecartGpsP95_m: +p95.toFixed(2), ecartGpsMax_m: +errs[errs.length - 1].toFixed(2),
        bankSpiraleMax: +maxOf(turning).toFixed(1), bankSpiraleMoyen: +mean(turning).toFixed(1),
        tauxCapMax_degS: +maxHeadRate.toFixed(1), tauxRoulisMax_degS: +maxRoll.toFixed(1),
        jitterCap_naif: +secondDiffStd(unwrapDeg(nh), dt).toFixed(1), jitterCap_nouveau: +secondDiffStd(unwrapDeg(s.heading), dt).toFixed(2),
        jitterBank_naif: +secondDiffStd(nb, dt).toFixed(1), jitterBank_nouveau: +secondDiffStd(s.bank, dt).toFixed(2),
    };
    console.log('[IGC réel]', JSON.stringify(stats));
    assert.ok(stats.jitterCap_nouveau < stats.jitterCap_naif / 10);
    assert.ok(stats.jitterBank_nouveau < stats.jitterBank_naif / 10);
});

test('spirale bruitée (r 150 m, 90 km/h, +1,5 m/s, σ 4 m) → bank ≈ atan(V²/gr) stable', () => {
    const pts = synth(spiral, 300, 4, 7);
    const track = buildAttitudeTrack(pts, {bankGain: 1}); // physique pure (sans recalage visuel)
    const s = sampleSeries(track, 30, 270);
    const expected = Math.atan(V * V / (G * R)) * DEG;
    const m = mean(s.bank), sd = std(s.bank);
    // cap : tangent au cercle (horaire → cap = ωt + 90°)
    const headErr = s.t.map((t, i) => Math.abs(wrap180(s.heading[i] - (W * t * DEG + 90))));
    const pose = {};
    const vario = [];
    for (let t = 30; t <= 270; t += 1) vario.push(sampleAttitude(track, t, pose).vario);
    const nv = naive(pts);
    const nb = s.t.map(t => nv(t).bank);
    console.log('[spirale]', JSON.stringify({
        attendu: +expected.toFixed(2), bankMoyen: +m.toFixed(2), bankEcartType: +sd.toFixed(2),
        bankMax: +maxOf(s.bank).toFixed(2), capErreurMax: +maxOf(headErr).toFixed(2), varioMoyen: +mean(vario).toFixed(2),
        naif_bankMoyen: +mean(nb).toFixed(2), naif_bankEcartType: +std(nb).toFixed(2),
        jitterBank_naif: +secondDiffStd(nb, 1 / 60).toFixed(1), jitterBank_nouveau: +secondDiffStd(s.bank, 1 / 60).toFixed(2),
    }));
    assert.ok(Math.abs(m - expected) < 5, `bank moyen ${m.toFixed(2)} vs ${expected.toFixed(2)}`);
    assert.ok(sd < 2, `écart-type bank ${sd.toFixed(2)}`);
    assert.ok(m > 0, 'virage horaire → bank positif (aile droite basse)');
    assert.ok(maxOf(headErr) < 6, `erreur de cap max ${maxOf(headErr).toFixed(2)}°`);
    assert.ok(Math.abs(mean(vario) - 1.5) < 0.2);
});

test('ligne droite bruitée → |bank| < 3°, écart-type du cap < 2°', () => {
    const hdg = 60 * RAD;
    const pts = synth(t => ({x: V * t * Math.sin(hdg), y: V * t * Math.cos(hdg), z: 1200 - 0.8 * t}), 300, 4, 11);
    const track = buildAttitudeTrack(pts);
    const s = sampleSeries(track, 10, 290);
    const maxBank = maxAbs(s.bank);
    const sdHead = std(s.heading.map(h => wrap180(h - 60)));
    const nv = naive(pts);
    const nq = s.t.map(t => nv(t));
    console.log('[ligne droite]', JSON.stringify({
        bankMaxAbs: +maxBank.toFixed(2), capEcartType: +sdHead.toFixed(2), capMoyen: +mean(s.heading).toFixed(2),
        pitchMoyen: +mean(s.pitch).toFixed(2),
        naif_bankMaxAbs: +maxAbs(nq.map(q => q.bank)).toFixed(2), naif_capEcartType: +std(nq.map(q => wrap180(q.heading - 60))).toFixed(2),
    }));
    assert.ok(maxBank < 3, `|bank| max ${maxBank.toFixed(2)}`);
    assert.ok(sdHead < 2, `écart-type cap ${sdHead.toFixed(2)}`);
});

test('entrée en virage brutale → taux de roulis ≤ limite, anticipation, saturation', () => {
    // 60 s en ligne droite vers le nord puis cercle serré à droite (r 60 m à 90 km/h → atan = 46,7°)
    const r = 60, w = V / r;
    const fn = t => t <= 60 ? {x: 0, y: V * t, z: 1000}
        : {x: r - r * Math.cos(w * (t - 60)), y: V * 60 + r * Math.sin(w * (t - 60)), z: 1000};
    const track = buildAttitudeTrack(synth(fn, 180, 3, 5), {bankGain: 1, maxRollRateDegS: 30, maxBankDeg: 60});
    const dt = 1 / 60;
    const s = sampleSeries(track, 0, 180, dt);
    let maxRate = 0;
    for (let i = 1; i < s.t.length; i++) maxRate = Math.max(maxRate, Math.abs(s.bank[i] - s.bank[i - 1]) / dt);
    const at = (t) => sampleAttitude(track, t).bank;
    const steady = s.bank.filter((_, i) => s.t[i] > 90 && s.t[i] < 170);
    console.log('[entrée en virage]', JSON.stringify({
        tauxRoulisMax: +maxRate.toFixed(2), bank_t59: +at(59).toFixed(1), bank_t60: +at(60).toFixed(1),
        bank_t62: +at(62).toFixed(1), bank_t65: +at(65).toFixed(1), bankEtabli: +mean(steady).toFixed(1),
        attendu: +(Math.atan(V * w / G) * DEG).toFixed(1),
    }));
    assert.ok(maxRate <= 30.0001, `taux de roulis ${maxRate.toFixed(2)} °/s`);
    assert.ok(at(60) > 2, 'le pilote incline avant/au début de la courbure (zéro-phase)');
    assert.ok(Math.abs(at(40)) < 3, 'rien de parasite 20 s avant');
    assert.ok(mean(steady) > 40 && mean(steady) <= 60);

    // limiteur réellement sollicité : taux de roulis imposé à 3 °/s → la dérivée ne le dépasse jamais
    const slow = buildAttitudeTrack(synth(fn, 180, 3, 5), {maxRollRateDegS: 3});
    const s8 = sampleSeries(slow, 0, 180, dt);
    let max8 = 0;
    for (let i = 1; i < s8.t.length; i++) max8 = Math.max(max8, Math.abs(s8.bank[i] - s8.bank[i - 1]) / dt);
    console.log('[limiteur 3 °/s]', JSON.stringify({tauxRoulisMax: +max8.toFixed(2)}));
    assert.ok(max8 <= 3.0001 && max8 > 2.5, `taux de roulis ${max8.toFixed(2)} °/s`);
});

test('au sol : cap figé, bank 0, pas de rotation parasite', () => {
    // 60 s immobile (bruit GPS), roulage/décollage vers l'est, 60 s de vol en ligne droite
    const fn = t => t < 60 ? {x: 0, y: 0, z: 200}
        : t < 80 ? {x: 0.5 * 1.25 * (t - 60) ** 2, y: 0, z: 200}
            : {x: 250 + V * (t - 80), y: 0, z: 200 + (t - 80)};
    const track = buildAttitudeTrack(synth(fn, 140, 3, 3));
    const s = sampleSeries(track, 0, 55, 1 / 10);
    const headings = unwrapDeg(s.heading);
    const spread = maxOf(headings) + maxOf(headings.map(v => -v));
    const maxBank = maxAbs(s.bank);
    console.log('[sol]', JSON.stringify({capAmplitudeSol: +spread.toFixed(3), bankMaxSol: +maxBank.toFixed(3), capSol: +s.heading[0].toFixed(1), pitchSol: +s.pitch[0].toFixed(2)}));
    assert.ok(spread < 0.5, `cap au sol varie de ${spread.toFixed(2)}°`);
    assert.ok(maxBank < 0.5);
    assert.ok(Math.abs(wrap180(s.heading[0] - 90)) < 10, 'cap figé = cap de décollage (est)');
    assert.ok(Math.abs(s.pitch[0]) < 1);
});

test('cas limites : 0, 1, 2 points, doublons, trou de 60 s, désordre, NaN', () => {
    assert.equal(sampleAttitude(buildAttitudeTrack([]), 0), null);
    const one = sampleAttitude(buildAttitudeTrack([{lat: 43.8, lon: 3.7, alt: 500, t: 10}]), 12);
    assert.ok(Math.abs(one.lat - 43.8) < 1e-9 && one.alt === 500 && one.bank === 0 && one.speed === 0);

    const two = buildAttitudeTrack([toLL(0, 0, 500, 0), toLL(250, 0, 510, 10)]);
    const mid = sampleAttitude(two, 5);
    assert.ok(Math.abs(toXY(mid).x - 125) < 1, `milieu x ${toXY(mid).x}`);
    assert.ok(Math.abs(mid.heading - 90) < 1 && Math.abs(mid.speed - 90) < 1 && Math.abs(mid.bank) < 1e-6);

    // doublons de temps + désordre + NaN
    const dup = synth(spiral, 120, 4, 9);
    const messy = [...dup, ...dup.slice(20, 40).map(p => ({...p})), {lat: NaN, lon: 3, alt: 1, t: 5}, {lat: 43, lon: 3, alt: 1, t: NaN}];
    messy.reverse();
    const tr = buildAttitudeTrack(messy, {bankGain: 1});
    const s = sampleSeries(tr, 20, 100);
    for (const k of ['lat', 'lon', 'alt', 'heading', 'bank', 'pitch']) assert.ok(s[k].every(Number.isFinite), k);
    assert.ok(Math.abs(mean(s.bank) - Math.atan(V * V / (G * R)) * DEG) < 5);

    // trou de 60 s au milieu d'une ligne droite, avec un saut d'altitude (pic GPS) avant
    const line = synth(t => ({x: V * t, y: 0, z: 1000}), 300, 4, 13).filter(p => p.t < 100 || p.t > 160);
    line[50].alt += 400;
    const tg = buildAttitudeTrack(line);
    const sg = sampleSeries(tg, 0, 300, 1 / 60);
    for (const k of ['lat', 'lon', 'alt', 'heading', 'bank', 'pitch']) assert.ok(sg[k].every(Number.isFinite), k);
    const pose = {};
    let maxDev = 0, maxAltDev = 0;
    for (let t = 100; t <= 160; t += 0.5) {
        sampleAttitude(tg, t, pose);
        maxDev = Math.max(maxDev, Math.abs(toXY(pose).y), Math.abs(toXY(pose).x - V * t));
    }
    for (let t = 5; t <= 295; t += 0.5) maxAltDev = Math.max(maxAltDev, Math.abs(sampleAttitude(tg, t, pose).alt - 1000));
    console.log('[trou 60 s]', JSON.stringify({ecartMaxDansLeTrou_m: +maxDev.toFixed(2), ecartAltMax_m: +maxAltDev.toFixed(2), bankMaxAbs: +maxAbs(sg.bank).toFixed(2)}));
    assert.ok(maxDev < 10, `écart dans le trou ${maxDev.toFixed(1)} m`);
    assert.ok(maxAltDev < 10, `pic d'altitude filtré (${maxAltDev.toFixed(1)} m)`);
    assert.ok(maxAbs(sg.bank) < 3);

    // temps hors bornes → bornés ; pas d'allocation avec `out`
    const out = {};
    assert.equal(sampleAttitude(tg, -100, out), out);
    assert.ok(Number.isFinite(sampleAttitude(tg, 1e9, out).lat));
});

test('20 000 points : pré-calcul et échantillonnage rapides', () => {
    const pts = synth(t => ({x: 300 * Math.sin(t / 40), y: 8 * t, z: 1000 + 200 * Math.sin(t / 300)}), 19999, 4, 21);
    const t0 = performance.now();
    const track = buildAttitudeTrack(pts);
    const buildMs = performance.now() - t0;
    const pose = {};
    const t1 = performance.now();
    let acc = 0;
    for (let i = 0; i < 200000; i++) acc += sampleAttitude(track, (i * 0.0997) % 19999, pose).bank;
    const perSampleUs = (performance.now() - t1) / 200000 * 1000;
    console.log('[20k]', JSON.stringify({buildMs: +buildMs.toFixed(1), sampleUs: +perSampleUs.toFixed(3)}));
    assert.ok(Number.isFinite(acc));
    assert.ok(buildMs < 1000);
    assert.ok(perSampleUs < 5);
});

test('S14 — réglage par défaut : spirale stabilisée ≈ 40°, progressive, toujours vers l\'intérieur', () => {
    for (const dir of [1, -1]) {
        // spirale type thermique : r 150 m à 90 km/h, sens horaire (dir 1) ou anti-horaire (dir −1)
        const fn = t => { const p = spiral(t); return {x: dir * p.x, y: p.y, z: p.z}; };
        const track = buildAttitudeTrack(synth(fn, 300, 4, 7));
        const s = sampleSeries(track, 30, 270);
        const m = mean(s.bank);
        const dt = s.t[1] - s.t[0];
        let maxRate = 0;
        for (let i = 1; i < s.t.length; i++) maxRate = Math.max(maxRate, Math.abs(s.bank[i] - s.bank[i - 1]) / dt);
        console.log('[défaut S14]', JSON.stringify({sens: dir > 0 ? 'droite' : 'gauche', bankMoyen: +m.toFixed(1), tauxRoulisMax: +maxRate.toFixed(1)}));
        assert.ok(Math.abs(Math.abs(m) - 40) < 4, `bank moyen ${m.toFixed(1)}`);
        assert.ok(Math.sign(m) === dir, 'virage à droite → bank > 0 (aile droite basse, côté intérieur)');
        assert.ok(maxRate <= 25.0001);
        assert.ok(maxAbs(s.bank) <= 55);
    }
});
