import * as THREE from './vendor/three.module.min.js';

const maplibregl = window.maplibregl;

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

function sizeSceneToViewport() {
    const width = Math.max(1, Math.round(window.visualViewport?.width || window.innerWidth));
    const height = Math.max(1, Math.round(window.visualViewport?.height || window.innerHeight));
    ui.scene.style.setProperty('width', `${width}px`, 'important');
    ui.scene.style.setProperty('height', `${height}px`, 'important');
    state.map?.resize();
}

const state = {
    map: null,
    customLayer: null,
    flight: null,
    points: [],
    duration: 1,
    playing: false,
    elapsed: 0,
    lastFrame: 0,
    rateIndex: 1,
    rates: [8, 32, 128],
    orbitBearing: 0,
    orbitPitch: 74,
    zoom: 16.0,
    ready: false,
    pointers: new Map(),
    pinchDistance: null,
    lastCameraUpdate: 0,
};

const clamp = (value, minimum, maximum) => Math.max(minimum, Math.min(maximum, value));
const toRadians = degrees => degrees * Math.PI / 180;
const toDegrees = radians => radians * 180 / Math.PI;

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

function satelliteTerrainStyle() {
    return {
        version: 8,
        sources: {
            satellite: {
                type: 'raster',
                tiles: ['https://tiles.maps.eox.at/wmts/1.0.0/s2cloudless-2017_3857/default/g/{z}/{y}/{x}.jpg'],
                tileSize: 256,
                maxzoom: 14,
                attribution: 'Sentinel-2 cloudless 2017 · s2maps.eu · EOX IT Services (CC BY 4.0, données Copernicus Sentinel modifiées 2017)',
            },
            terrain: {
                type: 'raster-dem',
                tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'],
                encoding: 'terrarium',
                tileSize: 256,
                maxzoom: 15,
                attribution: 'Relief · AWS Open Data Terrain Tiles',
            },
            hillshade: {
                type: 'raster-dem',
                tiles: ['https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png'],
                encoding: 'terrarium',
                tileSize: 256,
                maxzoom: 15,
            },
        },
        terrain: {source: 'terrain', exaggeration: 1.08},
        sky: {
            'sky-color': '#91b7d0',
            'horizon-color': '#e8ece8',
            'fog-color': '#d9e0de',
            'sky-horizon-blend': 0.35,
            'horizon-fog-blend': 0.45,
            'fog-ground-blend': 0.6,
            'atmosphere-blend': 0.8,
        },
        layers: [
            {id: 'base', type: 'background', paint: {'background-color': '#cbd6d4'}},
            {id: 'satellite', type: 'raster', source: 'satellite', paint: {'raster-opacity': 1, 'raster-saturation': -.08, 'raster-contrast': .08}},
            {id: 'terrain-shade', type: 'hillshade', source: 'hillshade', paint: {'hillshade-shadow-color': '#2f382e', 'hillshade-highlight-color': '#edf2ea', 'hillshade-exaggeration': .22}},
        ],
    };
}

function makeGlider() {
    const group = new THREE.Group();
    group.name = 'GLIDY procedural sailplane';
    const white = new THREE.MeshStandardMaterial({color: 0xf7f8f7, metalness: .22, roughness: .27});
    const charcoal = new THREE.MeshStandardMaterial({color: 0x20282d, metalness: .36, roughness: .18});
    const red = new THREE.MeshStandardMaterial({color: 0xd94236, metalness: .12, roughness: .38});
    const canopy = new THREE.MeshPhysicalMaterial({color: 0x142938, metalness: .25, roughness: .06, transparent: true, opacity: .88});

    const body = new THREE.Mesh(new THREE.CapsuleGeometry(.62, 5.7, 8, 18), white);
    body.rotation.x = Math.PI / 2;
    body.scale.set(1, 1, 1.08);
    body.castShadow = true;
    group.add(body);

    const nose = new THREE.Mesh(new THREE.ConeGeometry(.62, 2.2, 24), white);
    nose.rotation.x = Math.PI / 2;
    nose.position.z = 4.9;
    group.add(nose);

    const wingShape = new THREE.Shape();
    wingShape.moveTo(-10.5, -.55);
    wingShape.lineTo(0, 1.15);
    wingShape.lineTo(10.5, -.55);
    wingShape.lineTo(0, -.15);
    wingShape.closePath();
    const wing = new THREE.Mesh(new THREE.ExtrudeGeometry(wingShape, {depth: .16, bevelEnabled: true, bevelSize: .05, bevelThickness: .05}), white);
    wing.rotation.x = Math.PI / 2;
    wing.position.set(0, .02, .2);
    wing.castShadow = true;
    group.add(wing);

    const tail = new THREE.Mesh(new THREE.BoxGeometry(5.2, .12, .95), white);
    tail.position.set(0, .16, -3.3);
    group.add(tail);
    const fin = new THREE.Mesh(new THREE.BoxGeometry(.15, 1.8, 1.5), red);
    fin.position.set(0, .9, -3.35);
    fin.rotation.x = -.2;
    group.add(fin);

    const cockpit = new THREE.Mesh(new THREE.SphereGeometry(.72, 24, 12), canopy);
    cockpit.scale.set(.72, .45, 1.65);
    cockpit.position.set(0, .48, 2.05);
    group.add(cockpit);

    const skid = new THREE.Mesh(new THREE.BoxGeometry(.22, .18, 1.4), charcoal);
    skid.position.set(0, -.62, -.15);
    group.add(skid);

    const leftTip = new THREE.Mesh(new THREE.BoxGeometry(.42, .24, .55), red);
    leftTip.position.set(-10.2, 0, -.36);
    group.add(leftTip);
    const rightTip = leftTip.clone();
    rightTip.position.x = 10.2;
    group.add(rightTip);

    // Le modèle est agrandi pour rester lisible à l'échelle d'une caméra aérienne.
    group.scale.setScalar(3.8);
    return group;
}

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
    return ((to - from + 540) % 360) - 180;
}

function localCoordinates(originMercator, point) {
    const mercator = maplibregl.MercatorCoordinate.fromLngLat([point.lon, point.lat], 0);
    const unit = originMercator.meterInMercatorCoordinateUnits();
    return new THREE.Vector3(
        (mercator.x - originMercator.x) / unit,
        point.alt,
        (originMercator.y - mercator.y) / unit,
    );
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
        while (points[lo].t < t - 2.5 && lo < i) lo++;
        while (hi < n - 1 && points[hi + 1].t <= t + 2.5) hi++;
        const dt = points[hi].t - points[lo].t;
        points[i].vario = dt > 0.5 ? (points[hi].alt - points[lo].alt) / dt : 0;
        while (points[sLo].t < t - 2 && sLo < i) sLo++;
        while (sHi < n - 1 && points[sHi + 1].t <= t + 2) sHi++;
        const ds = points[sHi].t - points[sLo].t;
        points[i].speed = ds > 0.5 ? distanceMeters(points[sLo], points[sHi]) / ds * 3.6 : 0;
    }
}

function createFlightLayer(map, points) {
    const origin = points[0];
    const originMercator = maplibregl.MercatorCoordinate.fromLngLat([origin.lon, origin.lat], 0);
    const positions = points.map(point => localCoordinates(originMercator, point));
    const customLayer = {
        id: 'glidy-flight-3d',
        type: 'custom',
        renderingMode: '3d',
        onAdd(currentMap, gl) {
            this.camera = new THREE.Camera();
            this.scene = new THREE.Scene();
            this.scene.rotateX(Math.PI / 2);
            this.scene.scale.multiply(new THREE.Vector3(1, 1, -1));

            this.scene.add(new THREE.HemisphereLight(0xeaf4ff, 0x455348, 1.45));
            const sun = new THREE.DirectionalLight(0xffffff, 2.15);
            sun.position.set(-80, 140, -50).normalize();
            this.scene.add(sun);

            const lineGeometry = new THREE.BufferGeometry().setFromPoints(positions);
            const halo = new THREE.Line(lineGeometry, new THREE.LineBasicMaterial({color: 0x192023, transparent: true, opacity: .5}));
            halo.position.y = -1;
            this.scene.add(halo);
            // trace colorée au vario (échelle GLIDY) : les thermiques ressortent en orange
            const colors = new Float32Array(points.length * 3);
            points.forEach((point, i) => {
                const [r, g, b] = varioRgb(point.vario);
                colors[i * 3] = r / 255; colors[i * 3 + 1] = g / 255; colors[i * 3 + 2] = b / 255;
            });
            const coloured = lineGeometry.clone();
            coloured.setAttribute('color', new THREE.BufferAttribute(colors, 3));
            const path = new THREE.Line(coloured, new THREE.LineBasicMaterial({vertexColors: true, transparent: true, opacity: .96}));
            this.scene.add(path);

            this.glider = makeGlider();
            this.scene.add(this.glider);
            this.renderer = new THREE.WebGLRenderer({canvas: currentMap.getCanvas(), context: gl, antialias: true});
            this.renderer.autoClear = false;
            this.renderer.outputColorSpace = THREE.SRGBColorSpace;
            this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
            this.renderer.toneMappingExposure = 1.12;
        },
        setPose(position, heading, bank) {
            if (!this.glider) return;
            this.glider.position.copy(position);
            this.glider.rotation.set(0, toRadians(heading), toRadians(bank), 'YXZ');
        },
        render(gl, args) {
            const transform = {
                x: originMercator.x,
                y: originMercator.y,
                z: originMercator.z,
                scale: originMercator.meterInMercatorCoordinateUnits(),
            };
            const projection = new THREE.Matrix4().fromArray(args.defaultProjectionData.mainMatrix);
            const local = new THREE.Matrix4()
                .makeTranslation(transform.x, transform.y, transform.z)
                .scale(new THREE.Vector3(transform.scale, -transform.scale, transform.scale));
            this.camera.projectionMatrix = projection.multiply(local);
            this.renderer.resetState();
            this.renderer.render(this.scene, this.camera);
        },
    };
    return {customLayer, originMercator};
}

/** Position à l'instant [elapsed] (s depuis le décollage), par recherche dans les temps réels du fichier. */
function flightPosition(elapsed) {
    const points = state.points;
    if (points.length === 1) return {...points[0], heading: 0, speed: 0, bank: 0, vario: 0, progress: 0};
    const duration = Math.max(1, state.duration);
    const t = clamp(elapsed, 0, duration);
    let lo = 0, hi = points.length - 1;
    while (hi - lo > 1) {
        const mid = (lo + hi) >> 1;
        if (points[mid].t <= t) lo = mid; else hi = mid;
    }
    const a = points[lo];
    const b = points[hi];
    const span = Math.max(1e-6, b.t - a.t);
    const fraction = clamp((t - a.t) / span, 0, 1);
    // cap lissé sur quelques points pour que le planeur ne tremble pas à 1 Hz
    const back = points[Math.max(0, lo - 3)];
    const ahead = points[Math.min(points.length - 1, hi + 3)];
    const heading = headingDegrees(back, ahead);
    // inclinaison physique : tan(φ) = v·ω / g, ω = taux de virage mesuré sur ±5 points
    const i0 = Math.max(0, lo - 5), i1 = Math.min(points.length - 1, lo + 5);
    const turn = shortestAngle(headingDegrees(points[i0], a), headingDegrees(a, points[i1]));
    const turnSeconds = Math.max(1, (points[i1].t - points[i0].t) / 2);
    const omega = toRadians(turn) / turnSeconds;
    const bank = toDegrees(Math.atan((a.speed / 3.6) * omega / 9.81));
    return {
        lat: a.lat + (b.lat - a.lat) * fraction,
        lon: a.lon + (b.lon - a.lon) * fraction,
        alt: a.alt + (b.alt - a.alt) * fraction,
        heading,
        speed: a.speed + (b.speed - a.speed) * fraction,
        vario: a.vario + (b.vario - a.vario) * fraction,
        bank: clamp(Number.isFinite(bank) ? bank : 0, -55, 55),
        progress: t / duration,
    };
}

function formatTime(seconds) {
    const whole = Math.max(0, Math.round(seconds));
    const hours = Math.floor(whole / 3600);
    const minutes = Math.floor((whole % 3600) / 60);
    const secs = whole % 60;
    return hours > 0
        ? `${hours}:${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
        : `${String(minutes).padStart(2, '0')}:${String(secs).padStart(2, '0')}`;
}

function updateFrame(timestamp) {
    if (!state.ready) return;
    const delta = state.lastFrame ? Math.min(.1, (timestamp - state.lastFrame) / 1000) : 0;
    state.lastFrame = timestamp;
    if (state.playing) {
        state.elapsed += delta * state.rates[state.rateIndex];
        if (state.elapsed >= state.duration) {
            state.elapsed = state.duration;
            state.playing = false;
            ui.play.textContent = '▶';
        }
    }

    const pose = flightPosition(state.elapsed);
    const local = localCoordinates(state.customLayer.originMercator, pose);
    state.customLayer.layer.setPose(local, pose.heading, pose.bank);
    ui.altitude.textContent = `${Math.round(pose.alt)} m`;
    ui.speed.textContent = `${Math.round(pose.speed)} km/h`;
    if (ui.vario) {
        const v = pose.vario;
        ui.vario.textContent = `${v >= 0 ? '+' : '−'}${Math.abs(v).toFixed(1).replace('.', ',')}`;
        const [r, g, b] = varioRgb(v);
        ui.vario.style.color = `rgb(${r}, ${g}, ${b})`;
    }
    ui.time.textContent = formatTime(state.elapsed);
    ui.timeline.value = String(Math.round(pose.progress * 1000));

    if (timestamp - state.lastCameraUpdate > 32) {
        state.map.setCenterClampedToGround(false);
        state.map.jumpTo({
            center: [pose.lon, pose.lat],
            elevation: pose.alt,
            bearing: pose.heading + state.orbitBearing,
            pitch: state.orbitPitch,
            zoom: state.zoom,
        });
        state.lastCameraUpdate = timestamp;
    }
    setTimeout(() => requestAnimationFrame(updateFrame), 34);
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

async function loadFlight(payload) {
    try {
        sizeSceneToViewport();
        applyInsets(payload && payload.insets);
        state.flight = payload || {};
        state.points = readPoints(payload);
        if (state.points.length < 2) throw new Error('La trace IGC ne contient pas assez de points pour le rejeu 3D.');
        state.duration = Math.max(1, state.points[state.points.length - 1].t - state.points[0].t);
        const t0 = state.points[0].t;
        state.points.forEach(point => { point.t -= t0; });
        computeKinematics(state.points);

        const start = state.points[0];
        const map = new maplibregl.Map({
            container: 'map',
            style: satelliteTerrainStyle(),
            center: [start.lon, start.lat],
            elevation: start.alt,
            zoom: state.zoom,
            pitch: state.orbitPitch,
            bearing: headingDegrees(state.points[0], state.points[1]),
            maxPitch: 88,
            minZoom: 11,
            maxZoom: 18,
            pixelRatio: 1,
            attributionControl: false,
            canvasContextAttributes: {antialias: true},
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
        map.addControl(new maplibregl.AttributionControl({compact: true}), 'top-right');
        await map.once('load');
        const built = createFlightLayer(map, state.points);
        map.addLayer(built.customLayer);
        state.customLayer = {layer: built.customLayer, originMercator: built.originMercator};
        state.ready = true;
        state.playing = true;
        ui.play.textContent = 'Ⅱ';
        ui.loading.classList.add('hidden');
        ui.status.textContent = payload.title || 'Vol IGC';
        setTimeout(() => { ui.status.textContent = ''; }, 2600);
        setTimeout(() => { ui.hint.style.opacity = '0'; }, 6500);
        notifyAndroid('onReady', `${state.points.length} points`);
        requestAnimationFrame(updateFrame);
    } catch (error) {
        reportError(error);
    }
}

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

ui.camera.addEventListener('click', () => {
    state.orbitBearing = 0;
    state.orbitPitch = 74;
    state.zoom = 16.0;
});

ui.timeline.addEventListener('input', event => {
    if (!state.ready) return;
    state.elapsed = Number(event.target.value) / 1000 * state.duration;
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
    state.pointers.set(event.pointerId, {x: event.clientX, y: event.clientY});
    if (state.pointers.size === 1) {
        state.orbitBearing -= (event.clientX - previous.x) * .32;
        state.orbitPitch = clamp(state.orbitPitch + (event.clientY - previous.y) * .18, 50, 88);
    } else {
        const distance = pointerDistance();
        if (distance && state.pinchDistance) {
            state.zoom = clamp(state.zoom + Math.log2(distance / state.pinchDistance) * 1.25, 11.5, 17.5);
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

window.GlidyReplay = {loadFlight};
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
