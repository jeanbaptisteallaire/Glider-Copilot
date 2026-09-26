import * as THREE from './vendor/three.module.min.js';
import {MODEL_MIRROR_X, rigEuler} from './attitude-frame.mjs';

const maplibregl = window.maplibregl;

/*
 * Rejeu 3D GLIDY — MapLibre GL JS 5.15 (relief + imagerie) + three.js r169 (custom layer : trace + planeur).
 *
 * Points d'intégration (modules livrés par d'autres devs, chargés à chaud avec repli) :
 *   - poseAt(t)       : pose du planeur à t (s depuis le 1er point). Par défaut flightPosition() ; si
 *                       ./flight-dynamics.mjs existe, buildAttitudeTrack()+sampleAttitude() (voir setupDynamics).
 *   - createGlider()  : Promise<THREE.Object3D> du planeur à l'échelle 1:1 (m, nez +Z, dessus +Y, aile droite −X).
 *                       Par défaut ./glider-model.mjs (loadGliderModel), repli makeGlider() procédural.
 */

// ─── Réglages de rendu ──────────────────────────────────────────────────────────────────────────
const RENDER = {
    maxPixelRatio: 2,              // netteté sur écran HD, plafonnée pour le GPU
    minPixelRatio: 1,
    terrainExaggeration: 1.15,     // appliquée AUSSI aux altitudes de la trace (sinon le planeur « rentre » dans le relief)
    maxPitch: 85,
    defaultPitch: 70,              // 0 = vue verticale, 90 = horizon
    minUserPitch: 25,
    maxUserPitch: 82,
    cameraClearance: 45,           // m au-dessus du sol mini pour la caméra
    minDistance: 35,               // m caméra → planeur (bien au-delà du plan proche MapLibre ≈ 2–3 m)
    maxDistance: 6000,
    trackMaxSegments: 20000,
    trailWidthPx: 5,               // largeur de la trace (px CSS) ; la bordure sombre est incluse
    hudIntervalMs: 100,
    tileCacheSize: 160,            // tuiles par source
};

const ui = {
    scene: document.getElementById('scene'),
    loading: document.getElementById('loading'),
    loadingTitle: document.getElementById('loading-title'),
    loadingDetail: document.getElementById('loading-detail'),
    altitude: document.getElementById('altitude'),
    vario: document.getElementById('vario'),
    speed: document.getElementById('speed'),
    time: document.getElementById('time'),
    play: document.getElementById('play'),
    rate: document.getElementById('rate'),
    camera: document.getElementById('camera'),
    timeline: document.getElementById('timeline'),
    hint: document.getElementById('hint'),
    status: document.getElementById('status'),
    gesture: document.getElementById('gesture-layer'),
};

const state = {
    map: null,
    layer: null,
    origin: null,            // MercatorCoordinate du 1er point (repère local des sommets de la trace)
    originUnit: 1,           // unités Mercator par mètre à l'origine
    flight: null,
    points: [],
    duration: 1,
    playing: false,
    elapsed: 0,
    lastFrame: 0,
    rateIndex: 2,            // S14 : 4× à l'ouverture (demande JB)
    rates: [1, 2, 4],
    ready: false,
    pointers: new Map(),
    pinchDistance: null,
    scrubbing: false,
    altOffset: 0,            // m : recalage GNSS ↔ MNT (calculé au sol, au décollage/atterrissage)
    altOffsetTries: 0,
    lastHud: 0,
    gliderSpan: 15,
    probe: null,
    // caméra « chase » : consignes pilote + états amortis (valeur + vitesse)
    user: {orbitBearing: 0, pitch: RENDER.defaultPitch, distanceFactor: 1},
    cam: {
        heading: {value: 0, velocity: 0},
        orbit: {value: 0, velocity: 0},
        pitch: {value: RENDER.defaultPitch, velocity: 0},
        distance: {value: 260, velocity: 0},
        cx: {value: 0, velocity: 0}, cy: {value: 0, velocity: 0}, cz: {value: 0, velocity: 0},
        headingTarget: 0,
        prevTarget: null,       // THREE.Vector3 (créés après l'import)
        targetVelocity: null,
        groundUnderGlider: null,
        groundUnderCamera: null,
        frame: 0,
        last: {lon: NaN, lat: NaN, elevation: NaN, zoom: NaN, pitch: NaN, bearing: NaN},
    },
    stats: {frameCpuMs: 0, layerCpuMs: 0, frames: 0, windowStart: 0, fps: 0, worstFrameMs: 0, pixelRatio: 1, lowFpsWindows: 0, jumps: 0},
};

const clamp = (value, minimum, maximum) => Math.max(minimum, Math.min(maximum, value));
const toRadians = degrees => degrees * Math.PI / 180;
const toDegrees = radians => radians * 180 / Math.PI;
const EARTH_CIRCUMFERENCE = 40075016.686;
const meterInMercator = lat => 1 / (EARTH_CIRCUMFERENCE * Math.cos(toRadians(lat)));

function sizeSceneToViewport() {
    const width = Math.max(1, Math.round(window.visualViewport?.width || window.innerWidth));
    const height = Math.max(1, Math.round(window.visualViewport?.height || window.innerHeight));
    ui.scene.style.setProperty('width', `${width}px`, 'important');
    ui.scene.style.setProperty('height', `${height}px`, 'important');
    state.map?.resize();
}

function notifyAndroid(method, value = '') {
    try {
        if (window.AndroidReplay && typeof window.AndroidReplay[method] === 'function') {
            window.AndroidReplay[method](String(value));
        }
    } catch (_) { /* Le rejeu reste utilisable sans pont Android. */ }
}

function reportError(error) {
    const message = error instanceof Error ? error.message : String(error);
    ui.loadingTitle.textContent = 'Le rendu 3D ne peut pas démarrer';
    ui.loadingDetail.textContent = message;
    ui.status.textContent = message;
    notifyAndroid('onError', message);
}

// ─── Cartes ─────────────────────────────────────────────────────────────────────────────────────
// Emprise France métropolitaine + Corse : l'orthophoto IGN n'est demandée que là (hors de France : EOX seul).
const IGN_BOUNDS = [-5.9, 41.2, 10.0, 51.3];
const IGN_ORTHO_URL = 'https://data.geopf.fr/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0'
    + '&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&TILEMATRIXSET=PM&FORMAT=image/jpeg'
    + '&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}';

function satelliteTerrainStyle() {
    return {
        version: 8,
        sources: {
            satellite: {
                type: 'raster',
                tiles: ['https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2017_3857/default/g/{z}/{y}/{x}.jpg'],
                tileSize: 256,
                maxzoom: 14,
                attribution: '<a href="https://s2maps.eu">Sentinel-2 cloudless 2017</a> © EOX IT Services GmbH (CC BY 4.0 — données Copernicus Sentinel modifiées 2017)',
            },
            ign: {
                type: 'raster',
                tiles: [IGN_ORTHO_URL],
                tileSize: 256,
                minzoom: 6,
                maxzoom: 19,
                bounds: IGN_BOUNDS,
                attribution: '<a href="https://geoservices.ign.fr">© IGN – Géoplateforme</a> (orthophotos, Licence Ouverte Etalab 2.0)',
            },
            terrain: {
                type: 'raster-dem',
                tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'],
                encoding: 'terrarium',
                tileSize: 256,
                maxzoom: 15,
                attribution: 'Relief : <a href="https://registry.opendata.aws/terrain-tiles/">Terrain Tiles</a> (Mapzen, AWS Open Data — SRTM, GMTED, EU-DEM © Copernicus…)',
            },
            hillshade: {
                type: 'raster-dem',
                tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'],
                encoding: 'terrarium',
                tileSize: 256,
                maxzoom: 14,
            },
        },
        terrain: {source: 'terrain', exaggeration: RENDER.terrainExaggeration},
        // Atmosphère : brume repoussée vers l'horizon (fog-ground-blend proche de 1) pour voir loin.
        sky: {
            'sky-color': '#6d9fd0',
            'horizon-color': '#dde6ec',
            'fog-color': '#d6dfe4',
            'sky-horizon-blend': 0.55,
            'horizon-fog-blend': 0.75,
            'fog-ground-blend': 0.92,
            'atmosphere-blend': ['interpolate', ['linear'], ['zoom'], 0, 1, 10, 1, 16, 0.55],
        },
        layers: [
            {id: 'base', type: 'background', paint: {'background-color': '#9aa89c'}},
            {id: 'satellite', type: 'raster', source: 'satellite', paint: {'raster-saturation': -.05, 'raster-contrast': .06, 'raster-fade-duration': 120}},
            {id: 'ign-ortho', type: 'raster', source: 'ign', paint: {'raster-fade-duration': 120, 'raster-contrast': .04}},
            {id: 'terrain-shade', type: 'hillshade', source: 'hillshade', paint: {
                'hillshade-shadow-color': '#2a3328', 'hillshade-highlight-color': '#f2f5ee',
                'hillshade-exaggeration': ['interpolate', ['linear'], ['zoom'], 9, .28, 14, .12],
            }},
        ],
    };
}

// ─── Planeur ────────────────────────────────────────────────────────────────────────────────────
/** Planeur procédural de repli, à l'échelle 1:1 (envergure 15 m, nez +Z, dessus +Y, aile droite −X). */
function makeGlider() {
    const group = new THREE.Group();
    group.name = 'GLIDY procedural sailplane';
    const inner = new THREE.Group();
    const white = new THREE.MeshStandardMaterial({color: 0xf7f8f7, metalness: .22, roughness: .27});
    const charcoal = new THREE.MeshStandardMaterial({color: 0x20282d, metalness: .36, roughness: .18});
    const red = new THREE.MeshStandardMaterial({color: 0xd94236, metalness: .12, roughness: .38});
    const canopy = new THREE.MeshPhysicalMaterial({color: 0x142938, metalness: .25, roughness: .06, transparent: true, opacity: .88});

    const body = new THREE.Mesh(new THREE.CapsuleGeometry(.62, 5.7, 8, 18), white);
    body.rotation.x = Math.PI / 2;
    body.scale.set(1, 1, 1.08);
    inner.add(body);
    const nose = new THREE.Mesh(new THREE.ConeGeometry(.62, 2.2, 24), white);
    nose.rotation.x = Math.PI / 2;
    nose.position.z = 4.9;
    inner.add(nose);
    const wingShape = new THREE.Shape();
    wingShape.moveTo(-10.5, -.55);
    wingShape.lineTo(0, 1.15);
    wingShape.lineTo(10.5, -.55);
    wingShape.lineTo(0, -.15);
    wingShape.closePath();
    const wing = new THREE.Mesh(new THREE.ExtrudeGeometry(wingShape, {depth: .16, bevelEnabled: true, bevelSize: .05, bevelThickness: .05}), white);
    wing.rotation.x = Math.PI / 2;
    wing.position.set(0, .02, .2);
    inner.add(wing);
    const tail = new THREE.Mesh(new THREE.BoxGeometry(5.2, .12, .95), white);
    tail.position.set(0, .16, -3.3);
    inner.add(tail);
    const fin = new THREE.Mesh(new THREE.BoxGeometry(.15, 1.8, 1.5), red);
    fin.position.set(0, .9, -3.35);
    fin.rotation.x = -.2;
    inner.add(fin);
    const cockpit = new THREE.Mesh(new THREE.SphereGeometry(.72, 24, 12), canopy);
    cockpit.scale.set(.72, .45, 1.65);
    cockpit.position.set(0, .48, 2.05);
    cockpit.renderOrder = 1;
    inner.add(cockpit);
    const skid = new THREE.Mesh(new THREE.BoxGeometry(.22, .18, 1.4), charcoal);
    skid.position.set(0, -.62, -.15);
    inner.add(skid);
    const leftTip = new THREE.Mesh(new THREE.BoxGeometry(.42, .24, .55), red);
    leftTip.position.set(-10.2, 0, -.36);
    inner.add(leftTip);
    const rightTip = leftTip.clone();
    rightTip.position.x = 10.2;
    inner.add(rightTip);
    inner.scale.setScalar(15 / 21); // envergure de la forme = 21 → 15 m
    group.add(inner);
    group.userData.glider = {wingSpan: 15, length: 7, procedural: true};
    return group;
}

/**
 * POINT D'INTÉGRATION — modèle du planeur. Renvoie un Object3D 1:1 en mètres.
 * Essaie le vrai modèle (glider-model.mjs), repli procédural si le module ou ses fichiers manquent.
 */
async function createGlider() {
    try {
        const module = await import('./glider-model.mjs');
        const load = module.loadGliderModel || module.default;
        const group = await load(THREE, './');
        if (!group || !group.isObject3D) throw new Error('modèle vide');
        return group;
    } catch (error) {
        notifyAndroid('onMapWarning', `Modèle 3D indisponible, planeur simplifié (${error && error.message || error})`);
        return makeGlider();
    }
}

// ─── Géodésie / cinématique ─────────────────────────────────────────────────────────────────────
function distanceMeters(a, b) {
    const earthRadius = 6371000;
    const lat1 = toRadians(a.lat);
    const lat2 = toRadians(b.lat);
    const dLat = lat2 - lat1;
    const dLon = toRadians(b.lon - a.lon);
    const h = Math.sin(dLat / 2) ** 2 + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) ** 2;
    return earthRadius * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
}

function headingDegrees(a, b) {
    const lat1 = toRadians(a.lat);
    const lat2 = toRadians(b.lat);
    const dLon = toRadians(b.lon - a.lon);
    const y = Math.sin(dLon) * Math.cos(lat2);
    const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
    return (toDegrees(Math.atan2(y, x)) + 360) % 360;
}

function shortestAngle(from, to) {
    return ((((to - from) % 360) + 540) % 360) - 180;
}

/** Coordonnées locales (m) dans le repère de l'origine : x est, y altitude (exagérée + recalage), z nord. */
function toLocal(lat, lon, alt, out) {
    const mx = (lon + 180) / 360;
    const sinLat = Math.sin(toRadians(lat));
    const my = 0.5 - 0.25 * Math.log((1 + sinLat) / (1 - sinLat)) / Math.PI;
    out.x = (mx - state.origin.x) / state.originUnit;
    out.y = alt * RENDER.terrainExaggeration;
    out.z = (state.origin.y - my) / state.originUnit;
    return out;
}

// Échelle vario de la charte GLIDY (core/designsystem Theme.kt, varioStops) — mêmes valeurs.
const VARIO_STOPS = [
    [-3.0, [0x2d, 0x70, 0x43]],
    [-1.0, [0x7d, 0xc7, 0x7e]],
    [0.0, [0xbe, 0xbe, 0xbe]],
    [0.6, [0xff, 0xbd, 0x70]],
    [1.8, [0xff, 0x91, 0x30]],
    [3.5, [0xff, 0x80, 0x78]],
];

function varioRgb(ms) {
    if (!Number.isFinite(ms) || ms <= VARIO_STOPS[0][0]) return VARIO_STOPS[0][1];
    for (let k = 1; k < VARIO_STOPS.length; k++) {
        const [v1, c1] = VARIO_STOPS[k];
        const [v0, c0] = VARIO_STOPS[k - 1];
        if (ms <= v1) {
            const f = (ms - v0) / (v1 - v0);
            return [0, 1, 2].map(i => Math.round(c0[i] + (c1[i] - c0[i]) * f));
        }
    }
    return VARIO_STOPS[VARIO_STOPS.length - 1][1];
}

/** Vario lissé (≈5 s) et vitesse sol (≈4 s) de chaque point, à partir des temps réels du fichier IGC. */
function computeKinematics(points) {
    const n = points.length;
    let lo = 0, hi = 0, sLo = 0, sHi = 0;
    for (let i = 0; i < n; i++) {
        const t = points[i].t;
        // fenêtre ±2,5 s, mais au moins un point de part et d'autre (aperçu 512 points : pas de plusieurs s)
        while (points[lo].t < t - 2.5 && lo < i - 1) lo++;
        while (hi < n - 1 && (points[hi + 1].t <= t + 2.5 || hi <= i)) hi++;
        const dt = points[hi].t - points[lo].t;
        points[i].vario = dt > 0.5 ? (points[hi].alt - points[lo].alt) / dt : 0;
        while (points[sLo].t < t - 2 && sLo < i - 1) sLo++;
        while (sHi < n - 1 && (points[sHi + 1].t <= t + 2 || sHi <= i)) sHi++;
        const ds = points[sHi].t - points[sLo].t;
        points[i].speed = ds > 0.5 ? distanceMeters(points[sLo], points[sHi]) / ds * 3.6 : 0;
    }
}

function segmentIndex(t) {
    const points = state.points;
    let lo = 0, hi = points.length - 1;
    while (hi - lo > 1) {
        const mid = (lo + hi) >> 1;
        if (points[mid].t <= t) lo = mid; else hi = mid;
    }
    return lo;
}

/** Position interpolée (sans allocation) — sert au cap de la caméra et au HUD. */
function positionAt(t, out) {
    const points = state.points;
    const tt = clamp(t, 0, state.duration);
    const lo = segmentIndex(tt);
    const hi = Math.min(points.length - 1, lo + 1);
    const a = points[lo], b = points[hi];
    const f = clamp((tt - a.t) / Math.max(1e-6, b.t - a.t), 0, 1);
    out.lat = a.lat + (b.lat - a.lat) * f;
    out.lon = a.lon + (b.lon - a.lon) * f;
    out.alt = a.alt + (b.alt - a.alt) * f;
    out.speed = a.speed + (b.speed - a.speed) * f;
    out.vario = a.vario + (b.vario - a.vario) * f;
    return out;
}

const poseScratch = {lat: 0, lon: 0, alt: 0, heading: 0, pitch: 0, bank: 0, speed: 0, vario: 0, turnRate: 0, progress: 0};

/** Pose historique (interpolation linéaire des points IGC + cap/inclinaison estimés). Réutilise un objet. */
function flightPosition(elapsed) {
    const points = state.points;
    const out = poseScratch;
    const duration = Math.max(1, state.duration);
    const t = clamp(elapsed, 0, duration);
    positionAt(t, out);
    const lo = segmentIndex(t);
    const hi = Math.min(points.length - 1, lo + 1);
    const a = points[lo];
    // cap lissé sur quelques points pour que le planeur ne tremble pas à 1 Hz
    const back = points[Math.max(0, lo - 3)];
    const ahead = points[Math.min(points.length - 1, hi + 3)];
    out.heading = headingDegrees(back, ahead);
    // inclinaison physique : tan(φ) = v·ω / g, ω = taux de virage mesuré sur ±5 points
    const i0 = Math.max(0, lo - 5), i1 = Math.min(points.length - 1, lo + 5);
    const turn = shortestAngle(headingDegrees(points[i0], a), headingDegrees(a, points[i1]));
    const turnSeconds = Math.max(1, (points[i1].t - points[i0].t) / 2);
    const omega = toRadians(turn) / turnSeconds;
    const bank = toDegrees(Math.atan((a.speed / 3.6) * omega / 9.81));
    out.bank = clamp(Number.isFinite(bank) ? bank : 0, -55, 55);
    out.turnRate = toDegrees(omega);
    out.pitch = 0;
    out.progress = t / duration;
    return out;
}

let dynamicsSampler = null; // (t) => pose de flight-dynamics.mjs

/** Tente de brancher flight-dynamics.mjs (pose lissée C1). Repli silencieux sur flightPosition. */
async function setupDynamics(points) {
    dynamicsSampler = null;
    try {
        const module = await import('./flight-dynamics.mjs');
        if (typeof module.buildAttitudeTrack !== 'function' || typeof module.sampleAttitude !== 'function') return;
        const track = module.buildAttitudeTrack(points);
        const probe = module.sampleAttitude(track, 0);
        if (!probe || !Number.isFinite(probe.lat) || !Number.isFinite(probe.lon)) return;
        const scratch = {};
        dynamicsSampler = t => module.sampleAttitude(track, t, scratch);
    } catch (_) { /* module absent : pose historique */ }
}

/**
 * POINT D'INTÉGRATION — pose du planeur à t (s depuis le 1er point) :
 * {lat, lon, alt, heading, pitch, bank (degrés), speed (km/h), vario (m/s), turnRate, progress}.
 * Vitesse/vario restent ceux du HUD historique (même lissage que la couleur de la trace).
 */
function poseAt(t) {
    const base = flightPosition(t);
    if (!dynamicsSampler) return base;
    try {
        const p = dynamicsSampler(clamp(t, 0, state.duration));
        if (p && Number.isFinite(p.lat) && Number.isFinite(p.lon) && Number.isFinite(p.alt)) {
            base.lat = p.lat; base.lon = p.lon; base.alt = p.alt;
            if (Number.isFinite(p.heading)) base.heading = p.heading;
            if (Number.isFinite(p.pitch)) base.pitch = p.pitch;
            if (Number.isFinite(p.bank)) base.bank = p.bank;
            if (Number.isFinite(p.turnRate)) base.turnRate = p.turnRate;
        }
    } catch (_) { dynamicsSampler = null; }
    return base;
}

// ─── Ressort critique (SmoothDamp) ──────────────────────────────────────────────────────────────
function smoothDamp(s, target, smoothTime, dt) {
    const st = Math.max(1e-4, smoothTime);
    const omega = 2 / st;
    const x = omega * dt;
    const decay = 1 / (1 + x + 0.48 * x * x + 0.235 * x * x * x);
    const change = s.value - target;
    const temp = (s.velocity + omega * change) * dt;
    s.velocity = (s.velocity - omega * temp) * decay;
    s.value = target + (change + temp) * decay;
    return s.value;
}

function smoothDampAngle(s, target, smoothTime, dt) {
    return smoothDamp(s, s.value + shortestAngle(s.value, target), smoothTime, dt);
}

// ─── Trace : ruban écran (largeur constante en px), coloré au vario, visible à travers le relief ───
const TRAIL_VERTEX = /* glsl */`
attribute vec3 aOther;
attribute vec2 aSideEnd;   // x : côté (+1/−1) dans le repère du segment, y : 0 = extrémité A, 1 = B
attribute vec3 aColor;
attribute float aTime;
uniform vec2 uResolution;
uniform float uWidth;
varying vec3 vColor;
varying float vEdge;
varying float vTime;
void main() {
    vec4 a = projectionMatrix * modelViewMatrix * vec4(position, 1.0);
    vec4 b = projectionMatrix * modelViewMatrix * vec4(aOther, 1.0);
    // extrémité derrière la caméra : on la ramène sur le plan w = ε pour garder une direction valable
    const float EPS = 1e-3;
    if (b.w < EPS) b = mix(a, b, (a.w - EPS) / max(a.w - b.w, 1e-6));
    vec2 sa = a.xy / max(a.w, EPS) * uResolution;
    vec2 sb = b.xy / max(b.w, EPS) * uResolution;
    vec2 d = sb - sa;
    float len = length(d);
    vec2 dir = len > 1e-5 ? d / len : vec2(1.0, 0.0);
    float side = aSideEnd.y > 0.5 ? -aSideEnd.x : aSideEnd.x;
    vec2 n = vec2(-dir.y, dir.x);
    a.xy += n * side * uWidth / uResolution * a.w;
    gl_Position = a;
    vColor = aColor;
    vEdge = aSideEnd.x;
    vTime = aTime;
}`;

const TRAIL_FRAGMENT = /* glsl */`
uniform float uOpacity;
uniform float uFutureOpacity;
uniform float uNow;
varying vec3 vColor;
varying float vEdge;
varying float vTime;
void main() {
    float e = abs(vEdge);
    bool future = vTime > uNow;
    vec3 base = future ? mix(vColor, vec3(0.92), 0.55) : vColor;
    // liseré sombre : lisibilité sur l'orthophoto claire comme sur la forêt
    vec3 color = mix(base, vec3(0.05, 0.07, 0.06), smoothstep(0.55, 0.8, e));
    float alpha = (future ? uFutureOpacity : uOpacity) * (1.0 - smoothstep(0.86, 1.0, e));
    gl_FragColor = vec4(color, alpha);
}`;

function decimatedIndices(n, maxSegments) {
    if (n - 1 <= maxSegments) return null;
    const stride = (n - 1) / maxSegments;
    const out = new Uint32Array(maxSegments + 1);
    for (let i = 0; i <= maxSegments; i++) out[i] = Math.min(n - 1, Math.round(i * stride));
    return out;
}

function buildTrailGeometry(points) {
    const keep = decimatedIndices(points.length, RENDER.trackMaxSegments);
    const count = keep ? keep.length : points.length;
    const at = i => points[keep ? keep[i] : i];
    const segments = count - 1;
    const positions = new Float32Array(segments * 4 * 3);
    const others = new Float32Array(segments * 4 * 3);
    const sideEnd = new Float32Array(segments * 4 * 2);
    const colors = new Float32Array(segments * 4 * 3);
    const times = new Float32Array(segments * 4);
    const index = new Uint32Array(segments * 6);
    const pa = {x: 0, y: 0, z: 0}, pb = {x: 0, y: 0, z: 0};
    const SIDE = [1, -1, -1, 1];
    const END = [0, 0, 1, 1];
    for (let s = 0; s < segments; s++) {
        const A = at(s), B = at(s + 1);
        toLocal(A.lat, A.lon, A.alt, pa);
        toLocal(B.lat, B.lon, B.alt, pb);
        const ca = varioRgb(A.vario), cb = varioRgb(B.vario);
        for (let k = 0; k < 4; k++) {
            const v = s * 4 + k;
            const self = END[k] ? pb : pa;
            const other = END[k] ? pa : pb;
            const c = END[k] ? cb : ca;
            positions.set([self.x, self.y, self.z], v * 3);
            others.set([other.x, other.y, other.z], v * 3);
            sideEnd[v * 2] = SIDE[k];
            sideEnd[v * 2 + 1] = END[k];
            colors[v * 3] = c[0] / 255; colors[v * 3 + 1] = c[1] / 255; colors[v * 3 + 2] = c[2] / 255;
            times[v] = END[k] ? B.t : A.t;
        }
        const v0 = s * 4;
        index.set([v0, v0 + 1, v0 + 2, v0 + 1, v0 + 3, v0 + 2], s * 6);
    }
    const geometry = new THREE.BufferGeometry();
    geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    geometry.setAttribute('aOther', new THREE.BufferAttribute(others, 3));
    geometry.setAttribute('aSideEnd', new THREE.BufferAttribute(sideEnd, 2));
    geometry.setAttribute('aColor', new THREE.BufferAttribute(colors, 3));
    geometry.setAttribute('aTime', new THREE.BufferAttribute(times, 1));
    geometry.setIndex(new THREE.BufferAttribute(index, 1));
    return {geometry, segments};
}

function trailMaterial(shared, {opacity, futureOpacity, occluded}) {
    const material = new THREE.ShaderMaterial({
        uniforms: {
            uResolution: shared.uResolution,
            uWidth: shared.uWidth,
            uNow: shared.uNow,
            uOpacity: {value: opacity},
            uFutureOpacity: {value: futureOpacity},
        },
        vertexShader: TRAIL_VERTEX,
        fragmentShader: TRAIL_FRAGMENT,
        depthWrite: false,
        side: THREE.DoubleSide,
        toneMapped: false,
    });
    if (occluded) {
        // passe « rayons X » : seulement là où le relief (ou autre) est devant ; dessinée AVANT le planeur
        material.depthFunc = THREE.GreaterDepth;
        material.transparent = false;
        material.blending = THREE.CustomBlending;
        material.blendSrc = THREE.SrcAlphaFactor;
        material.blendDst = THREE.OneMinusSrcAlphaFactor;
    } else {
        material.transparent = true;
        material.depthFunc = THREE.LessEqualDepth;
    }
    return material;
}

// ─── Custom layer three.js ──────────────────────────────────────────────────────────────────────
function createFlightLayer(map, points, gliderModel) {
    const trail = buildTrailGeometry(points);
    const tmpMatrix = new THREE.Matrix4();
    const anchorMatrix = new THREE.Matrix4();
    const layer = {
        id: 'glidy-flight-3d',
        type: 'custom',
        renderingMode: '3d',
        anchor: new THREE.Vector3(),      // position locale (m) du planeur = nouvelle origine de rendu
        gliderLocal: new THREE.Vector3(),
        precision: 'float64-rebased',
        onAdd(currentMap, gl) {
            this.map = currentMap;
            this.camera = new THREE.Camera();
            this.camera.matrixAutoUpdate = false;
            this.scene = new THREE.Scene();
            this.scene.rotateX(Math.PI / 2);
            this.scene.scale.multiply(new THREE.Vector3(1, 1, -1));

            this.scene.add(new THREE.HemisphereLight(0xeaf4ff, 0x455348, 1.45));
            const sun = new THREE.DirectionalLight(0xffffff, 2.15);
            sun.position.set(-80, 140, -50).normalize();
            this.scene.add(sun);

            // « world » porte tout ce qui est exprimé dans le repère de l'origine : décalé de −anchor à chaque frame
            this.world = new THREE.Group();
            this.world.matrixAutoUpdate = false;
            this.scene.add(this.world);

            this.shared = {
                uResolution: {value: new THREE.Vector2(1, 1)},
                uWidth: {value: RENDER.trailWidthPx},
                uNow: {value: 0},
            };
            const hidden = new THREE.Mesh(trail.geometry, trailMaterial(this.shared, {opacity: .38, futureOpacity: .12, occluded: true}));
            hidden.renderOrder = -2;
            hidden.frustumCulled = false;
            const visible = new THREE.Mesh(trail.geometry, trailMaterial(this.shared, {opacity: .97, futureOpacity: .42, occluded: false}));
            visible.renderOrder = 2;
            visible.frustumCulled = false;
            this.world.add(hidden, visible);

            // repères de profondeur : fil vertical planeur → sol et anneau au sol
            const pylonGeometry = new THREE.BufferGeometry();
            pylonGeometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(6), 3));
            this.pylon = new THREE.Line(pylonGeometry, new THREE.LineBasicMaterial({color: 0xb7f7a5, transparent: true, opacity: .75, depthWrite: false, toneMapped: false}));
            this.pylon.frustumCulled = false;
            this.pylon.renderOrder = 3;
            this.scene.add(this.pylon);
            this.groundRing = new THREE.Mesh(
                new THREE.RingGeometry(.78, 1, 40).rotateX(-Math.PI / 2),
                new THREE.MeshBasicMaterial({color: 0xb7f7a5, transparent: true, opacity: .8, depthWrite: false, side: THREE.DoubleSide, toneMapped: false}),
            );
            this.groundRing.renderOrder = 3;
            this.groundRing.frustumCulled = false;
            this.scene.add(this.groundRing);

            // planeur : rig (position/attitude/échelle d'affichage) → modèle 1:1
            this.rig = new THREE.Group();
            // S14 : le modèle (main droite) est symétrisé pour le repère local main gauche (voir attitude-frame.mjs)
            this.body = new THREE.Group();
            this.body.scale.x = MODEL_MIRROR_X;
            this.body.add(gliderModel);
            this.rig.add(this.body);
            this.scene.add(this.rig);
            // silhouette verte quand le relief masque le planeur (dessinée avant, test de profondeur inversé)
            this.ghost = gliderModel.clone(true);
            const ghostMaterial = new THREE.MeshBasicMaterial({color: 0xb7f7a5, depthWrite: false, depthFunc: THREE.GreaterDepth, toneMapped: false,
                transparent: false, blending: THREE.CustomBlending, blendSrc: THREE.SrcAlphaFactor, blendDst: THREE.OneMinusSrcAlphaFactor, opacity: .45});
            this.ghost.traverse(o => { if (o.isMesh) { o.material = ghostMaterial; o.renderOrder = -1; } });
            this.body.add(this.ghost);
            this.rig.traverse(o => { o.frustumCulled = false; });

            this.renderer = new THREE.WebGLRenderer({canvas: currentMap.getCanvas(), context: gl, antialias: true});
            this.renderer.autoClear = false;
            this.renderer.outputColorSpace = THREE.SRGBColorSpace;
            this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
            this.renderer.toneMappingExposure = 1.12;
        },
        /** Pose du planeur (repère local, altitude déjà exagérée + recalée) ; angles en degrés. */
        setPose(local, heading, pitch, bank, visualScale, groundY) {
            if (!this.rig) return;
            this.gliderLocal.copy(local);
            this.anchor.copy(local); // ré-ancrage : l'origine de rendu suit le planeur
            this.rig.position.set(0, 0, 0);
            const e = rigEuler(heading, pitch, bank);
            this.rig.rotation.set(e.x, e.y, e.z, e.order);
            this.rig.scale.setScalar(visualScale);
            const pos = this.pylon.geometry.attributes.position;
            const hasGround = Number.isFinite(groundY) && groundY < local.y - 2;
            this.pylon.visible = hasGround;
            this.groundRing.visible = hasGround;
            if (hasGround) {
                pos.setXYZ(0, 0, -1.2 * visualScale, 0);
                pos.setXYZ(1, 0, groundY - local.y + .5, 0);
                pos.needsUpdate = true;
                this.groundRing.position.set(0, groundY - local.y + .6, 0);
                this.groundRing.scale.setScalar(clamp(visualScale * 4, 6, 400));
            }
            // la trace est en coordonnées d'origine (sans recalage d'altitude) : on la décale de −ancre + recalage
            tmpMatrix.makeTranslation(-local.x, -(local.y - state.altOffset), -local.z);
            this.world.matrix.copy(tmpMatrix);
            this.world.matrixWorldNeedsUpdate = true;
        },
        setNow(t) { if (this.shared) this.shared.uNow.value = t; },
        /**
         * Matrice modèle→clip calculée en double précision (JS) et relative au planeur :
         * VP(float64, MapLibre) × [Mercator ← mètres autour de l'ancre]. Le GPU ne reçoit que des petites
         * coordonnées → plus de tremblement float32 (defaultProjectionData.mainMatrix est en Float32Array).
         */
        projectionFor(args) {
            const t = this.map.transform;
            const vp = args.modelViewProjectionMatrix;
            const worldSize = t && t.worldSize;
            const centerLat = t && t.center ? t.center.lat : NaN;
            const s = state.originUnit;
            const ax = state.origin.x + this.anchor.x * s;
            const ay = state.origin.y - this.anchor.z * s;
            if (vp && vp.length === 16 && worldSize > 0 && Number.isFinite(centerLat)) {
                const zc = 1 / meterInMercator(centerLat);        // mètres par unité z Mercator (convention MapLibre)
                const sz = meterInMercator(yToLat(ay)) * zc;        // ≈ 1 : m (altitude) → unités z de VP
                anchorMatrix.set(
                    worldSize * s, 0, 0, worldSize * ax,
                    0, -worldSize * s, 0, worldSize * ay,
                    0, 0, sz, this.anchor.y * sz,
                    0, 0, 0, 1,
                );
                this.precision = 'float64-rebased';
                return this.camera.projectionMatrix.fromArray(vp).multiply(anchorMatrix);
            }
            // repli : matrice MapLibre float32 (précision moindre)
            anchorMatrix.set(
                s, 0, 0, ax,
                0, -s, 0, ay,
                0, 0, meterInMercator(yToLat(ay)), this.anchor.y * meterInMercator(yToLat(ay)),
                0, 0, 0, 1,
            );
            this.precision = 'float32-fallback';
            return this.camera.projectionMatrix.fromArray(args.defaultProjectionData.mainMatrix).multiply(anchorMatrix);
        },
        render(gl, args) {
            const cpuStart = performance.now();
            this.projectionFor(args);
            this.shared.uResolution.value.set(gl.drawingBufferWidth, gl.drawingBufferHeight);
            this.shared.uWidth.value = RENDER.trailWidthPx * (gl.drawingBufferWidth / Math.max(1, gl.canvas.clientWidth || gl.drawingBufferWidth));
            this.renderer.resetState();
            this.renderer.render(this.scene, this.camera);
            state.stats.layerCpuMs += (performance.now() - cpuStart - state.stats.layerCpuMs) * .05;
        },
    };
    return {layer, segments: trail.segments};
}

function yToLat(y) {
    return toDegrees(Math.atan(Math.sinh(Math.PI * (1 - 2 * y))));
}

// ─── Boucle de rendu ────────────────────────────────────────────────────────────────────────────
function formatTime(seconds) {
    const whole = Math.max(0, Math.round(seconds));
    const hours = Math.floor(whole / 3600);
    const minutes = Math.floor((whole % 3600) / 60);
    const secs = whole % 60;
    return hours > 0
        ? `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
        : `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

const scratchA = {lat: 0, lon: 0, alt: 0, speed: 0, vario: 0};
const scratchB = {lat: 0, lon: 0, alt: 0, speed: 0, vario: 0};
const gliderLocal = new THREE.Vector3();
state.cam.prevTarget = new THREE.Vector3();
state.cam.targetVelocity = new THREE.Vector3();

/** Route « de fond » pour la caméra : direction sur une fenêtre de temps assez large pour ignorer les spirales. */
const COURSE_WINDOWS = [12, 30, 60, 120];
function courseForCamera(t, fallback) {
    for (const window of COURSE_WINDOWS) {
        positionAt(t - window, scratchA);
        positionAt(t + window, scratchB);
        if (distanceMeters(scratchA, scratchB) > 160) return headingDegrees(scratchA, scratchB);
    }
    return fallback;
}

function queryGround(lon, lat) {
    try {
        const value = state.map.queryTerrainElevation([lon, lat]);
        return Number.isFinite(value) ? value : null;
    } catch (_) { return null; }
}

/** Recalage altitude GNSS ↔ relief : au sol (décollage/atterrissage), la trace doit toucher le MNT. */
function calibrateAltitude() {
    const pts = state.points;
    const candidates = [];
    const check = p => {
        if (!(p.speed < 25)) return;
        const ground = queryGround(p.lon, p.lat);
        if (ground != null) candidates.push(ground - p.alt * RENDER.terrainExaggeration);
    };
    check(pts[0]);
    check(pts[pts.length - 1]);
    if (!candidates.length) return false;
    const offset = candidates.reduce((sum, v) => sum + v, 0) / candidates.length;
    state.altOffset = Math.abs(offset) <= 150 ? offset : 0;
    return true;
}

function zoomForDistance(distance, lat) {
    const t = state.map.transform;
    const ctc = (t && t.cameraToCenterDistance) || 1.5 * state.map.getCanvas().clientHeight;
    return Math.log2(ctc / (distance * 512 * meterInMercator(lat)));
}

function updateCamera(pose, dt, snap) {
    const cam = state.cam;
    const map = state.map;
    const layer = state.layer;
    cam.frame++;
    const lon = pose.lon, lat = pose.lat;
    const centerElevation = gliderLocal.y;

    // sol sous le planeur / sous la caméra : requêtes MNT espacées (≈ 10 Hz)
    if (cam.frame % 6 === 0 || cam.groundUnderGlider == null) cam.groundUnderGlider = queryGround(lon, lat);
    const agl = cam.groundUnderGlider != null ? Math.max(0, centerElevation - cam.groundUnderGlider) : 300;
    // centre de caméra jamais sous le relief (trace GNSS sous le MNT au sol) : sinon la caméra finirait dans la montagne
    const centerY = cam.groundUnderGlider != null ? Math.max(gliderLocal.y, cam.groundUnderGlider + 3) : gliderLocal.y;

    // distance de poursuite : plus haut = plus loin (portée visuelle), modulée par le pincement
    const baseDistance = clamp(170 + agl * .28, 170, 1400);
    const targetDistance = clamp(baseDistance * state.user.distanceFactor, RENDER.minDistance, RENDER.maxDistance);

    // cap : route de fond (hors spirale) — la caméra ne suit ni le roulis ni chaque virage
    cam.headingTarget = courseForCamera(state.elapsed, cam.headingTarget);

    if (snap) {
        cam.heading.value = cam.headingTarget; cam.heading.velocity = 0;
        cam.distance.value = targetDistance; cam.distance.velocity = 0;
        cam.cx.value = gliderLocal.x; cam.cy.value = centerY; cam.cz.value = gliderLocal.z;
        cam.cx.velocity = cam.cy.velocity = cam.cz.velocity = 0;
        cam.targetVelocity.set(0, 0, 0);
    } else {
        smoothDampAngle(cam.heading, cam.headingTarget, 1.6, dt);
        smoothDamp(cam.distance, targetDistance, .7, dt);
        // centre : ressort critique avec anticipation de la vitesse estimée (retard ≈ 0 en vitesse constante)
        // → le planeur reste au centre sans « nager », les à-coups d'interpolation sont filtrés.
        const vel = cam.targetVelocity;
        if (dt > 0) {
            const k = 1 - Math.exp(-dt / .25);
            vel.x += ((gliderLocal.x - cam.prevTarget.x) / dt - vel.x) * k;
            vel.y += ((centerY - cam.prevTarget.y) / dt - vel.y) * k;
            vel.z += ((gliderLocal.z - cam.prevTarget.z) / dt - vel.z) * k;
        }
        // constante courte et proportionnelle à la vitesse de lecture : en spirale à 32×, un tour dure < 1 s réelle
        const rate = state.playing ? state.rates[state.rateIndex] : 1;
        const centerTime = clamp(.5 / rate, .004, .12);
        smoothDamp(cam.cx, gliderLocal.x + vel.x * centerTime, centerTime, dt);
        smoothDamp(cam.cy, centerY + vel.y * centerTime, centerTime, dt);
        smoothDamp(cam.cz, gliderLocal.z + vel.z * centerTime, centerTime, dt);
    }
    cam.prevTarget.set(gliderLocal.x, centerY, gliderLocal.z);
    smoothDampAngle(cam.orbit, state.user.orbitBearing, .12, dt);

    // pitch : consigne pilote, limité pour que la caméra reste au-dessus du relief
    const bearing = cam.heading.value + cam.orbit.value;
    const distance = cam.distance.value;
    let pitchLimit = RENDER.maxUserPitch;
    const horizontal = distance * Math.sin(toRadians(state.user.pitch));
    const b = toRadians(bearing);
    const camLat = lat - Math.cos(b) * horizontal / 111320;
    const camLon = lon - Math.sin(b) * horizontal / (111320 * Math.cos(toRadians(lat)));
    if (cam.frame % 6 === 3 || cam.groundUnderCamera == null) cam.groundUnderCamera = queryGround(camLon, camLat);
    if (cam.groundUnderCamera != null) {
        const minCameraElevation = Math.max(cam.groundUnderCamera, cam.groundUnderGlider ?? -1e9) + RENDER.cameraClearance;
        const ratio = (minCameraElevation - cam.cy.value) / distance;
        if (ratio > -1 && ratio < 1) pitchLimit = Math.min(pitchLimit, toDegrees(Math.acos(ratio)));
        else if (ratio >= 1) {
            // même à la verticale la caméra serait trop basse : on recule (monte) jusqu'à la marge de sécurité
            pitchLimit = 0;
            cam.distance.value = minCameraElevation - cam.cy.value + 1;
            cam.distance.velocity = Math.max(0, cam.distance.velocity);
        }
    }
    const targetPitch = clamp(Math.min(state.user.pitch, pitchLimit), 0, RENDER.maxPitch);
    if (snap) { cam.pitch.value = targetPitch; cam.pitch.velocity = 0; } else smoothDamp(cam.pitch, targetPitch, .25, dt);
    // jamais sous le relief, même pendant l'amortissement
    if (cam.pitch.value > pitchLimit) { cam.pitch.value = pitchLimit; cam.pitch.velocity = Math.min(0, cam.pitch.velocity); }

    // centre caméra (lissé) → lon/lat
    const s = state.originUnit;
    const cLat = yToLat(state.origin.y - cam.cz.value * s);
    const cLon = (state.origin.x + cam.cx.value * s) * 360 - 180;
    const zoom = clamp(zoomForDistance(cam.distance.value, cLat), 7, 19.8);
    const pitch = cam.pitch.value;
    const heading = ((bearing % 360) + 360) % 360;
    const last = cam.last;
    const changed = Math.abs(last.lon - cLon) > 1e-9 || Math.abs(last.lat - cLat) > 1e-9
        || Math.abs(last.elevation - cam.cy.value) > .01 || Math.abs(last.zoom - zoom) > 1e-4
        || Math.abs(last.pitch - pitch) > 1e-3 || Math.abs(shortestAngle(last.bearing, heading)) > 1e-3
        || !Number.isFinite(last.lon);
    if (changed) {
        map.jumpTo({center: [cLon, cLat], elevation: cam.cy.value, zoom, pitch, bearing: heading});
        last.lon = cLon; last.lat = cLat; last.elevation = cam.cy.value; last.zoom = zoom; last.pitch = pitch; last.bearing = heading;
        state.stats.jumps++;
    }
    return cam.distance.value;
}

function updateHud(pose, now) {
    if (now - state.lastHud < RENDER.hudIntervalMs) return;
    state.lastHud = now;
    ui.altitude.textContent = `${Math.round(pose.alt)} m`;
    ui.speed.textContent = `${Math.round(pose.speed)} km/h`;
    if (ui.vario) {
        const v = pose.vario;
        ui.vario.textContent = `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(1).replace('.', ',')}`;
        const [r, g, b] = varioRgb(v);
        ui.vario.style.color = `rgb(${r}, ${g}, ${b})`;
    }
    ui.time.textContent = formatTime(state.elapsed);
    if (!state.scrubbing) ui.timeline.value = String(Math.round(pose.progress * 1000));
}

function trackFrameStats(now, frameMs) {
    const stats = state.stats;
    stats.frames++;
    if (state.ready && stats.frames > 2) stats.worstFrameMs = Math.max(stats.worstFrameMs, frameMs);
    if (!stats.windowStart) stats.windowStart = now;
    const span = now - stats.windowStart;
    if (span < 3000) return;
    stats.fps = stats.frames * 1000 / span;
    stats.frames = 0;
    stats.windowStart = now;
    // qualité adaptative : si le téléphone peine, on baisse la densité de pixels par paliers
    const map = state.map;
    if (stats.fps < 38 && state.playing && map && typeof map.setPixelRatio === 'function') {
        stats.lowFpsWindows++;
        if (stats.lowFpsWindows >= 2 && stats.pixelRatio > RENDER.minPixelRatio) {
            stats.pixelRatio = Math.max(RENDER.minPixelRatio, stats.pixelRatio - .5);
            map.setPixelRatio(stats.pixelRatio);
            stats.lowFpsWindows = 0;
        }
    } else {
        stats.lowFpsWindows = 0;
    }
}

let snapNext = true;

function frame(now) {
    requestAnimationFrame(frame);
    if (!state.ready) return;
    const cpuStart = performance.now();
    const frameMs = state.lastFrame ? now - state.lastFrame : 16.7;
    const dt = Math.min(.1, frameMs / 1000);
    state.lastFrame = now;
    trackFrameStats(now, frameMs);

    if (state.playing) {
        state.elapsed += dt * state.rates[state.rateIndex];
        if (state.elapsed >= state.duration) {
            state.elapsed = state.duration;
            state.playing = false;
            ui.play.textContent = '▶';
        }
    }
    if (state.altOffsetTries < 40 && state.cam.frame % 30 === 0) {
        state.altOffsetTries++;
        if (calibrateAltitude()) { state.altOffsetTries = 40; state.cam.groundUnderGlider = null; }
    }

    const pose = poseAt(state.elapsed);
    toLocal(pose.lat, pose.lon, pose.alt, gliderLocal);
    gliderLocal.y += state.altOffset;
    const cam = state.cam;
    // saut (curseur de temps) : la caméra se recale d'un coup plutôt que de « voler » 20 km
    const jumped = Math.hypot(gliderLocal.x - cam.cx.value, gliderLocal.z - cam.cz.value) > 2500;
    const jumpsBefore = state.stats.jumps;
    const distance = updateCamera(pose, dt, snapNext || jumped);
    const cameraMoved = state.stats.jumps !== jumpsBefore;
    snapNext = false;

    const visualScale = clamp(distance / 115, 1, 9) * (15 / state.gliderSpan);
    const groundY = cam.groundUnderGlider;
    const gearDown = !(pose.speed > 55);
    if (state.gear && gearDown !== state.gearDown) { state.gearDown = gearDown; for (const g of state.gear) g.visible = gearDown; }
    state.layer.setPose(gliderLocal, pose.heading, pose.pitch || 0, pose.bank, visualScale, groundY);
    state.layer.setNow(state.elapsed);
    // caméra et planeur mis à jour ensemble : on demande le rendu de CETTE pose (pas de setTimeout)
    if (cameraMoved || state.playing || state.pointers.size) state.map.triggerRepaint();
    updateHud(pose, now);
    if (state.probe) {
        const c = state.cam;
        state.probe.push([now, state.elapsed, gliderLocal.x, gliderLocal.y, gliderLocal.z, c.cx.value, c.cy.value, c.cz.value,
            c.heading.value + c.orbit.value, c.pitch.value, c.distance.value]);
        if (state.probe.length > 5000) state.probe.shift();
    }
    state.stats.frameCpuMs += (performance.now() - cpuStart - state.stats.frameCpuMs) * .05;
}

/**
 * Charge de rejeu : format colonnes (S11, toute la trace) {lat:[], lon:[], alt:[], t:[]} ou ancien format
 * {points:[{lat,lon,alt}], durationSeconds} (512 points d'aperçu, temps supposés réguliers).
 */
function readPoints(payload) {
    if (!payload) return [];
    let points = [];
    if (Array.isArray(payload.lat)) {
        for (let i = 0; i < payload.lat.length; i++) {
            points.push({lat: Number(payload.lat[i]), lon: Number(payload.lon[i]), alt: Number(payload.alt[i]), t: Number(payload.t[i])});
        }
    } else if (Array.isArray(payload.points)) {
        const n = payload.points.length;
        const duration = Math.max(1, Number(payload.durationSeconds) || n);
        points = payload.points.map((p, i) => ({lat: Number(p.lat), lon: Number(p.lon), alt: Number(p.alt), t: n > 1 ? i / (n - 1) * duration : 0}));
    }
    return points.filter(p => [p.lat, p.lon, p.alt, p.t].every(Number.isFinite));
}

/** Marges système (barre d'état, barre de navigation) transmises par Android, en px CSS. */
function applyInsets(insets) {
    const root = document.documentElement.style;
    root.setProperty('--inset-top', `${Math.max(0, Number(insets && insets.top) || 0)}px`);
    root.setProperty('--inset-bottom', `${Math.max(0, Number(insets && insets.bottom) || 0)}px`);
}

let loopStarted = false;

async function loadFlight(payload) {
    try {
        if (state.map) { state.map.remove(); state.map = null; state.ready = false; }
        sizeSceneToViewport();
        applyInsets(payload && payload.insets);
        state.flight = payload || {};
        state.points = readPoints(payload);
        if (state.points.length < 2) throw new Error('La trace IGC ne contient pas assez de points pour le rejeu 3D.');
        // temps strictement croissants (doublons IGC) et relatifs au 1er point
        const t0 = state.points[0].t;
        state.points.forEach(point => { point.t -= t0; });
        state.points = state.points.filter((p, i, all) => i === 0 || p.t > all[i - 1].t);
        if (state.points.length < 2) throw new Error('La trace IGC ne contient pas assez de points pour le rejeu 3D.');
        state.duration = Math.max(1, state.points[state.points.length - 1].t);
        computeKinematics(state.points);
        state.elapsed = 0;
        state.altOffset = 0;
        state.altOffsetTries = 0;

        const start = state.points[0];
        state.origin = maplibregl.MercatorCoordinate.fromLngLat([start.lon, start.lat], 0);
        state.originUnit = state.origin.meterInMercatorCoordinateUnits();

        const [gliderModel] = await Promise.all([createGlider(), setupDynamics(state.points)]);
        state.gliderSpan = (gliderModel.userData.glider && gliderModel.userData.glider.wingSpan) || 15;
        // train d'atterrissage : sorti au sol, rentré en vol (comme sur un planeur à train escamotable)
        state.gear = [];
        gliderModel.traverse(o => { if (o.isMesh && /^(LandingGear|Wheel)/.test(o.name || '')) state.gear.push(o); });
        state.gearDown = true;

        const pixelRatio = clamp(window.devicePixelRatio || 1, RENDER.minPixelRatio, RENDER.maxPixelRatio);
        state.stats.pixelRatio = pixelRatio;
        const initialHeading = headingDegrees(state.points[0], state.points[Math.min(state.points.length - 1, 10)]);
        state.cam.headingTarget = initialHeading;
        const map = new maplibregl.Map({
            container: 'map',
            style: satelliteTerrainStyle(),
            center: [start.lon, start.lat],
            elevation: start.alt * RENDER.terrainExaggeration,
            zoom: 15,
            pitch: RENDER.defaultPitch,
            bearing: initialHeading,
            maxPitch: RENDER.maxPitch,
            minZoom: 7,
            maxZoom: 19.8,
            pixelRatio,
            centerClampedToGround: false,
            maxTileCacheSize: RENDER.tileCacheSize,
            refreshExpiredTiles: false,
            fadeDuration: 120,
            attributionControl: false,
            canvasContextAttributes: {antialias: true, powerPreference: 'high-performance'},
        });
        state.map = map;
        map.dragPan.disable();
        map.dragRotate.disable();
        map.scrollZoom.disable();
        map.doubleClickZoom.disable();
        map.touchZoomRotate.disable();
        map.keyboard.disable();
        map.on('error', event => {
            // Une tuile absente ne doit pas interrompre le vol : le fond de secours reste visible.
            if (event && event.error) notifyAndroid('onMapWarning', event.error.message || 'Tuile indisponible');
        });

        // attribution repliée derrière un « i », sous le tableau de bord (plus sous les commandes de lecture)
        map.addControl(new maplibregl.AttributionControl({
            compact: true,
            // modèle 3D sous CC BY 4.0 : crédit obligatoire (vendor/licenses/GLIDER-MODEL-LICENSE.txt)
            customAttribution: 'Planeur 3D : <a href="https://sketchfab.com/3d-models/underpoly-free-sailplane-glider-45ffefc38fcf4e76a9d0c2a4e76262ef">« UNDERPOLY: Free Sailplane Glider »</a> par UNDERPOLY Project (<a href="https://creativecommons.org/licenses/by/4.0/">CC BY 4.0</a>, modifié)',
        }), 'top-right');
        await map.once('load');
        map.setCenterClampedToGround(false);
        const built = createFlightLayer(map, state.points, gliderModel);
        map.addLayer(built.layer);
        state.layer = built.layer;
        state.trailSegments = built.segments;
        // l'attribution compacte s'ouvre d'elle-même au chargement : on la replie (le « i » reste disponible)
        document.querySelectorAll('.maplibregl-ctrl-attrib.maplibregl-compact-show').forEach(el => el.classList.remove('maplibregl-compact-show'));
        document.querySelectorAll('.maplibregl-ctrl-attrib details[open]').forEach(el => el.removeAttribute('open'));
        snapNext = true;
        state.ready = true;
        state.playing = true;
        ui.play.textContent = 'Ⅱ';
        ui.loading.classList.add('hidden');
        ui.status.textContent = payload.title || 'Vol IGC';
        setTimeout(() => { ui.status.textContent = ''; }, 2600);
        setTimeout(() => { ui.hint.style.opacity = '0'; }, 6500);
        notifyAndroid('onReady', `${state.points.length} points`);
        if (!loopStarted) { loopStarted = true; requestAnimationFrame(frame); }
    } catch (error) {
        reportError(error);
    }
}

// ─── Commandes ──────────────────────────────────────────────────────────────────────────────────
ui.play.addEventListener('click', () => {
    if (!state.ready) return;
    if (state.elapsed >= state.duration) state.elapsed = 0;
    state.playing = !state.playing;
    ui.play.textContent = state.playing ? 'Ⅱ' : '▶';
});

ui.rate.addEventListener('click', () => {
    state.rateIndex = (state.rateIndex + 1) % state.rates.length;
    ui.rate.textContent = `${state.rates[state.rateIndex]}×`;
});

/** Recentrer : retour derrière le planeur, pitch et distance par défaut (transition amortie). */
ui.camera.addEventListener('click', () => {
    state.user.orbitBearing = 0; // l'amortissement angulaire prend le plus court chemin
    state.user.pitch = RENDER.defaultPitch;
    state.user.distanceFactor = 1;
});

ui.timeline.addEventListener('pointerdown', () => { state.scrubbing = true; });
ui.timeline.addEventListener('pointerup', () => { state.scrubbing = false; });
ui.timeline.addEventListener('pointercancel', () => { state.scrubbing = false; });
ui.timeline.addEventListener('input', event => {
    if (!state.ready) return;
    state.elapsed = Number(event.target.value) / 1000 * state.duration;
    snapNext = true; // saut dans le temps : la caméra se recale sans « voler » jusqu'au planeur
});

function pointerDistance() {
    const values = [...state.pointers.values()];
    if (values.length < 2) return null;
    return Math.hypot(values[0].x - values[1].x, values[0].y - values[1].y);
}

ui.gesture.addEventListener('pointerdown', event => {
    ui.gesture.setPointerCapture(event.pointerId);
    state.pointers.set(event.pointerId, {x: event.clientX, y: event.clientY});
    state.pinchDistance = pointerDistance();
    ui.hint.style.opacity = '0';
});

ui.gesture.addEventListener('pointermove', event => {
    const previous = state.pointers.get(event.pointerId);
    if (!previous) return;
    const dx = event.clientX - previous.x;
    const dy = event.clientY - previous.y;
    previous.x = event.clientX;
    previous.y = event.clientY;
    if (state.pointers.size === 1) {
        // glisser = tourner autour du planeur (s'ajoute au cap lissé) ; vertical = inclinaison de la vue
        state.user.orbitBearing -= dx * .32;
        state.user.pitch = clamp(state.user.pitch + dy * .18, RENDER.minUserPitch, RENDER.maxUserPitch);
    } else {
        const distance = pointerDistance();
        if (distance && state.pinchDistance) {
            state.user.distanceFactor = clamp(state.user.distanceFactor * state.pinchDistance / distance, .2, 10);
        }
        state.pinchDistance = distance;
    }
});

function releasePointer(event) {
    state.pointers.delete(event.pointerId);
    state.pinchDistance = pointerDistance();
}
ui.gesture.addEventListener('pointerup', releasePointer);
ui.gesture.addEventListener('pointercancel', releasePointer);
ui.gesture.addEventListener('wheel', event => {
    event.preventDefault();
    state.user.distanceFactor = clamp(state.user.distanceFactor * Math.exp(event.deltaY * .0015), .2, 10);
}, {passive: false});

/** Mesures pour les tests (fps, précision, caméra) — sans effet sur le rendu. */
function debugInfo() {
    const cam = state.cam;
    return {
        fps: Math.round(state.stats.fps * 10) / 10,
        worstFrameMs: Math.round(state.stats.worstFrameMs),
        frameCpuMs: +state.stats.frameCpuMs.toFixed(3),   // JS de la boucle (pose + caméra + HUD)
        layerCpuMs: +state.stats.layerCpuMs.toFixed(3),   // JS du custom layer (matrices + appels three)
        pixelRatio: state.stats.pixelRatio,
        precision: state.layer ? state.layer.precision : null,
        elapsed: state.elapsed,
        trailSegments: state.trailSegments,
        dynamics: !!dynamicsSampler,
        glider: state.layer && state.layer.rig ? state.layer.rig.children[0].name : null,
        altOffset: state.altOffset,
        camera: {heading: cam.heading.value, pitch: cam.pitch.value, distance: cam.distance.value, zoom: state.map ? state.map.getZoom() : null},
        glider3d: state.layer ? state.layer.gliderLocal.toArray() : null,
        cameraLagM: state.layer ? Math.hypot(cam.cx.value - state.layer.gliderLocal.x, cam.cy.value - state.layer.gliderLocal.y, cam.cz.value - state.layer.gliderLocal.z) : null,
    };
}

window.GlidyReplay = {
    loadFlight,
    debug: debugInfo,
    map: () => state.map,
    // enregistrement frame par frame (tests) : [t, elapsed, planeur xyz, centre caméra xyz, cap, pitch, distance]
    probe(on) { if (on) state.probe = []; const out = state.probe; if (!on) state.probe = null; return out; },
};
window.addEventListener('resize', sizeSceneToViewport);
notifyAndroid('onJavascriptReady', 'ready');

if (new URLSearchParams(window.location.search).has('demo')) {
    const center = {lat: 43.802, lon: 3.735};
    const demoPoints = Array.from({length: 260}, (_, index) => {
        const progress = index / 259;
        const thermal = Math.sin(progress * Math.PI * 18);
        const circuit = progress * Math.PI * 2.4;
        return {
            lat: center.lat + Math.sin(circuit) * (.045 + progress * .035) + Math.sin(progress * 35) * .006,
            lon: center.lon + Math.cos(circuit) * (.065 + progress * .045) + Math.cos(progress * 29) * .008,
            alt: 220 + Math.sin(progress * Math.PI) * 1450 + thermal * 120,
        };
    });
    setTimeout(() => loadFlight({title: 'Saint-Martin-de-Londres · vol simulé', durationSeconds: 6780, points: demoPoints}), 0);
}
