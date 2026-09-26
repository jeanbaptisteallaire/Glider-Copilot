/**
 * GLIDY — dynamique de vol du rejeu 3D.
 *
 * Un fichier IGC ne contient que (t, lat, lon, alt) à ~1 Hz, bruités (±3–5 m) : ni cap, ni inclinaison,
 * ni tangage. Ce module les DÉDUIT du tracé, une fois pour toutes (pré-calcul), puis fournit pour tout
 * temps continu une pose lissée, C1 en position, sans tremblement.
 *
 *   const track = buildAttitudeTrack(points, options?);   // points : [{lat, lon, alt, t}] (t en s)
 *   const pose  = sampleAttitude(track, t, out?);           // O(1), aucune allocation si `out` est fourni
 *   // pose = {lat, lon, alt, heading, pitch, bank, speed, vario, turnRate}
 *   //   heading °  0–360 (nord, sens horaire)   bank ° (+ = aile droite basse, virage à droite)
 *   //   pitch °  (+ = nez haut)                 speed km/h (sol, horizontale)   vario m/s   turnRate °/s
 *
 * Chaîne de calcul (toute la trace est connue → filtres à phase nulle, centrés, non causaux) :
 *  1. Nettoyage : points non finis rejetés, tri par t, doublons de temps moyennés, pics d'altitude
 *     (filtre de Hampel, médiane glissante sur 7 points) remplacés par la médiane.
 *  2. Projection ENU locale (équirectangulaire autour du 1er point : x est, y nord, z haut, en m).
 *  3. Rééchantillonnage sur une grille uniforme (pas h = intervalle médian borné [0,25 ; 1] s).
 *     Les nœuds situés À L'INTÉRIEUR d'un trou > `gapSeconds` sont marqués « trou ».
 *  4. Hors trous, par tronçon continu : Savitzky-Golay cubique (fenêtre 11 s) sur x/y/z → position
 *     débruitée ; SG dérivée (ordre 2, 9 s) → direction de la vitesse (cap) ;
 *     SG dérivée quartique (9 s) → norme de la vitesse (peu atténuée même en virage serré) ; SG dérivée (11 s) sur z → vario.
 *     Dans les trous : interpolation LINÉAIRE entre les extrémités lissées (pas de spline qui explose).
 *  5. Position échantillonnée par Hermite cubique (Catmull-Rom uniforme en temps : dérivée nodale =
 *     différence centrée) → continuité C1, aucun saut de vitesse au passage d'un point.
 *  6. Cap = tangente à la trajectoire lissée ; figé (maintien de la dernière valeur fiable) quand la
 *     vitesse sol passe sous ~25 km/h (transition douce 21→29 km/h) ; puis lissage gaussien σ 1 s
 *     (préserve exactement une rampe = virage établi), limiteur de taux à phase nulle (≤ 45 °/s).
 *     Taux de virage ω = dérivée du cap lissé.
 *  7. Inclinaison : virage coordonné φc = atan(V·ω/g).
 *     HYPOTHÈSE : V = vitesse AIR ≈ vitesse SOL lissée (le vent est inconnu). Par vent fort, la vitesse
 *     sol varie le long du cercle (vent arrière / vent de face) → l'inclinaison déduite oscille
 *     légèrement autour de la vraie valeur (±~15 % pour 20 km/h de vent à 90 km/h).
 *     Puis bande morte douce (φ³/(φ²+d²), d = 2,5°), saturation ±60°, et dynamique de roulis :
 *     filtre du 2e ordre critique (ωn = 1/τ, τ = 1 s) avec limitation du taux de roulis (30 °/s),
 *     exécuté dans le sens du temps ET à rebours, puis moyenné → réponse symétrique à phase nulle :
 *     le planeur commence à s'incliner avant que la trajectoire ne courbe (anticipation du pilote),
 *     et la moyenne de deux signaux à taux borné reste à taux borné.
 *  8. Tangage : pente γ = atan(vz/V) + calage 2°, bornée ±20°, même filtre (τ 1,5 s, 10 °/s).
 *     Au sol : inclinaison 0, tangage `groundPitchDeg`, cap figé.
 */

const G = 9.80665;
const EARTH_RADIUS = 6371008.8;
const DEG = 180 / Math.PI;
const RAD = Math.PI / 180;

export const DEFAULT_OPTIONS = Object.freeze({
    gapSeconds: 10,            // intervalle au-delà duquel on considère un trou de données
    positionWindowS: 11,       // fenêtre Savitzky-Golay (cubique) de la position
    headingWindowS: 9,         // fenêtre SG (dérivée, ordre 2) de la direction de vitesse
    headingSmoothS: 1,         // σ du lissage gaussien du cap déroulé
    speedWindowS: 9,           // fenêtre SG (quartique, dérivée) de la vitesse sol
    speedSmoothS: 2,           // σ du lissage gaussien de la vitesse sol
    altWindowS: 11,            // fenêtre SG de la dérivée verticale
    varioSmoothS: 1,           // σ du lissage gaussien du vario
    altSpikeM: 25,             // écart à la médiane (7 pts) au-delà duquel une altitude est un pic
    groundSpeedKmh: 25,        // sous cette vitesse sol : au sol (cap figé, inclinaison 0)
    groundBlendKmh: 4,         // demi-largeur de la transition douce autour de groundSpeedKmh
    rollTimeConstantS: 1.0,    // constante du filtre de roulis (2e ordre critique)
    maxRollRateDegS: 30,       // taux de roulis max (planeur : 25–35 °/s)
    maxBankDeg: 60,
    bankDeadbandDeg: 2.5,      // bande morte douce autour de 0
    maxTurnRateDegS: 45,       // taux de virage max (limiteur du cap, borne de ω)
    pitchTrimDeg: 2,           // assiette de croisière ajoutée à la pente
    maxPitchDeg: 20,
    pitchTimeConstantS: 1.5,
    maxPitchRateDegS: 10,
    groundPitchDeg: 0,
});

// ---------------------------------------------------------------------------------------------
// Construction
// ---------------------------------------------------------------------------------------------

/**
 * Pré-calcule la piste d'attitude. `points` : tableau `{lat, lon, alt, t}` (t en s, croissant de
 * préférence ; doublons, désordre, trous et valeurs non finies tolérés). Renvoie un objet opaque à
 * passer à {@link sampleAttitude} (champs `t0`, `t1`, `duration`, `empty` lisibles).
 */
export function buildAttitudeTrack(points, options) {
    const o = {...DEFAULT_OPTIONS, ...(options || {})};
    const raw = cleanPoints(points || []);
    const n = raw.t.length;
    if (n === 0) return {empty: true, n: 0, t0: 0, t1: 0, duration: 0, options: o};

    const lat0 = raw.lat[0], lon0 = raw.lon[0];
    const ky = EARTH_RADIUS * RAD;
    const kx = ky * Math.cos(lat0 * RAD);
    const rx = new Float64Array(n), ry = new Float64Array(n);
    for (let i = 0; i < n; i++) {
        rx[i] = (raw.lon[i] - lon0) * kx;
        ry[i] = (raw.lat[i] - lat0) * ky;
    }
    const rz = despike(raw.alt, o.altSpikeM);
    const rt = raw.t;

    if (n === 1) {
        return makeTrack(o, lat0, lon0, kx, ky, rt[0], 1, 1, {
            x: [0], y: [0], z: [rz[0]], dx: [0], dy: [0], dz: [0], head: [0], omega: [0],
            bank: [0], bankRate: [0], pitch: [o.groundPitchDeg], pitchRate: [0], speed: [0], vario: [0],
        });
    }

    // --- grille uniforme ---
    const duration = rt[n - 1] - rt[0];
    const dts = [];
    for (let i = 1; i < n; i++) dts.push(rt[i] - rt[i - 1]);
    dts.sort((a, b) => a - b);
    let h = clamp(dts[dts.length >> 1], 0.25, 1);
    let N = Math.max(2, Math.ceil(duration / h) + 1);
    if (N > 500000) N = 500000;
    h = duration / (N - 1);

    const gx = new Float64Array(N), gy = new Float64Array(N), gz = new Float64Array(N);
    const gap = new Uint8Array(N);
    for (let k = 0, j = 0; k < N; k++) {
        const t = rt[0] + k * h;
        while (j < n - 2 && rt[j + 1] <= t) j++;
        const span = rt[j + 1] - rt[j];
        const f = clamp((t - rt[j]) / span, 0, 1);
        gx[k] = rx[j] + (rx[j + 1] - rx[j]) * f;
        gy[k] = ry[j] + (ry[j + 1] - ry[j]) * f;
        gz[k] = rz[j] + (rz[j + 1] - rz[j]) * f;
        if (span > o.gapSeconds && t > rt[j] + 1e-6 && t < rt[j + 1] - 1e-6) gap[k] = 1;
    }

    // --- lissage par tronçons continus ---
    const sx = new Float64Array(N), sy = new Float64Array(N), sz = new Float64Array(N);
    const hx = new Float64Array(N), hy = new Float64Array(N), vz = new Float64Array(N);
    const qx = new Float64Array(N), qy = new Float64Array(N);
    const mPos = halfWindow(o.positionWindowS, h), mHead = halfWindow(o.headingWindowS, h);
    const mAlt = halfWindow(o.altWindowS, h), mSpeed = halfWindow(o.speedWindowS, h);
    for (let a = 0; a < N;) {
        if (gap[a]) { a++; continue; }
        let b = a;
        while (b + 1 < N && !gap[b + 1]) b++;
        savitzkyGolay(gx, sx, a, b, mPos, 3, 0, h);
        savitzkyGolay(gy, sy, a, b, mPos, 3, 0, h);
        savitzkyGolay(gz, sz, a, b, mPos, 3, 0, h);
        savitzkyGolay(gx, hx, a, b, mHead, 2, 1, h);
        savitzkyGolay(gy, hy, a, b, mHead, 2, 1, h);
        savitzkyGolay(gz, vz, a, b, mAlt, 2, 1, h);
        savitzkyGolay(gx, qx, a, b, mSpeed, 4, 1, h);
        savitzkyGolay(gy, qy, a, b, mSpeed, 4, 1, h);
        a = b + 1;
    }
    // trous : segment linéaire entre les extrémités lissées
    for (let k = 0; k < N;) {
        if (!gap[k]) { k++; continue; }
        const a = k - 1;
        let b = k;
        while (gap[b]) b++;
        const span = (b - a) * h;
        const ux = (sx[b] - sx[a]) / span, uy = (sy[b] - sy[a]) / span, uz = (sz[b] - sz[a]) / span;
        for (let i = k; i < b; i++) {
            const f = (i - a) / (b - a);
            sx[i] = sx[a] + (sx[b] - sx[a]) * f;
            sy[i] = sy[a] + (sy[b] - sy[a]) * f;
            sz[i] = sz[a] + (sz[b] - sz[a]) * f;
            hx[i] = ux; hy[i] = uy; vz[i] = uz; qx[i] = ux; qy[i] = uy;
        }
        k = b;
    }

    // --- dérivées nodales (Catmull-Rom uniforme en temps) ---
    const dx = centralDiff(sx, h), dy = centralDiff(sy, h), dz = centralDiff(sz, h);

    // --- vitesse sol lissée et facteur « en vol » (0 au sol → 1 en vol) ---
    const speed = new Float64Array(N);
    for (let k = 0; k < N; k++) speed[k] = Math.hypot(qx[k], qy[k]);
    gaussianInPlace(speed, o.speedSmoothS / h);
    const air = new Float64Array(N);
    const vLo = (o.groundSpeedKmh - o.groundBlendKmh) / 3.6, vHi = (o.groundSpeedKmh + o.groundBlendKmh) / 3.6;
    for (let k = 0; k < N; k++) air[k] = smoothstep((speed[k] - vLo) / Math.max(1e-6, vHi - vLo));

    // --- cap : tangent à la trajectoire, figé au sol, déroulé puis lissé ---
    const head = new Float64Array(N);
    let first = -1, best = 0;
    for (let k = 0; k < N; k++) {
        if (air[k] > 0.5) { first = k; break; }
        if (speed[k] > speed[best]) best = k;
    }
    let H;
    if (first >= 0) H = Math.atan2(hx[first], hy[first]);
    else {
        const ex = sx[N - 1] - sx[0], ey = sy[N - 1] - sy[0];
        H = Math.hypot(ex, ey) > 5 ? Math.atan2(ex, ey) : Math.atan2(hx[best], hy[best]);
    }
    for (let k = 0; k < N; k++) {
        const target = Math.atan2(hx[k], hy[k]);
        H += air[k] * wrapPi(target - H);
        head[k] = H;
    }
    gaussianInPlace(head, o.headingSmoothS / h);
    // aucun planeur ne tourne plus vite que ~45 °/s : limiteur de taux à phase nulle (artefacts GPS)
    rateLimitZeroPhase(head, o.maxTurnRateDegS * RAD * 0.75 * h); // marge : l’Hermite peut dépasser la corde de 25 %
    const omega = centralDiff(head, h);

    // --- inclinaison : virage coordonné + bande morte + dynamique de roulis ---
    const maxOmega = o.maxTurnRateDegS * RAD;
    const target = new Float64Array(N);
    const d2 = o.bankDeadbandDeg * o.bankDeadbandDeg;
    for (let k = 0; k < N; k++) {
        const w = clamp(omega[k], -maxOmega, maxOmega);
        let phi = Math.atan(speed[k] * w / G) * DEG * air[k];
        phi = d2 > 0 ? phi * phi * phi / (phi * phi + d2) : phi;
        target[k] = clamp(phi, -o.maxBankDeg, o.maxBankDeg);
    }
    const bank = new Float64Array(N), bankRate = new Float64Array(N);
    zeroPhaseSecondOrder(target, bank, bankRate, h, o.rollTimeConstantS,
        o.maxRollRateDegS * 0.93, o.maxBankDeg);

    // --- tangage : pente de trajectoire + calage ---
    gaussianInPlace(vz, o.varioSmoothS / h);
    const ptarget = new Float64Array(N);
    for (let k = 0; k < N; k++) {
        const gamma = Math.atan2(vz[k], Math.max(speed[k], 1)) * DEG + o.pitchTrimDeg;
        const flying = clamp(gamma, -o.maxPitchDeg, o.maxPitchDeg);
        ptarget[k] = air[k] * flying + (1 - air[k]) * o.groundPitchDeg;
    }
    const pitch = new Float64Array(N), pitchRate = new Float64Array(N);
    zeroPhaseSecondOrder(ptarget, pitch, pitchRate, h, o.pitchTimeConstantS,
        o.maxPitchRateDegS * 0.93, o.maxPitchDeg);

    return makeTrack(o, lat0, lon0, kx, ky, rt[0], h, N, {
        x: sx, y: sy, z: sz, dx, dy, dz, head, omega, bank, bankRate, pitch, pitchRate, speed, vario: vz,
    });
}

function makeTrack(o, lat0, lon0, kx, ky, t0, h, N, c) {
    return {
        empty: false, n: N, t0, t1: t0 + (N - 1) * h, duration: (N - 1) * h, step: h,
        lat0, lon0, kx, ky, options: o,
        maxBank: o.maxBankDeg, maxPitch: o.maxPitchDeg,
        ...c,
    };
}

// ---------------------------------------------------------------------------------------------
// Échantillonnage (appelé à chaque image)
// ---------------------------------------------------------------------------------------------

/**
 * Pose lissée au temps `t` (s, même référence que les points ; borné à [t0, t1]).
 * Passer un objet `out` réutilisé évite toute allocation par image. Renvoie null si la piste est vide.
 */
export function sampleAttitude(track, t, out) {
    if (!track || track.empty) return null;
    const r = out || {lat: 0, lon: 0, alt: 0, heading: 0, pitch: 0, bank: 0, speed: 0, vario: 0, turnRate: 0};
    const N = track.n;
    let k = 0, s = 0;
    if (N > 1) {
        let u = (t - track.t0) / track.step;
        if (!(u > 0)) u = 0; // NaN → début
        if (u > N - 1) u = N - 1;
        k = Math.floor(u);
        if (k >= N - 1) k = N - 2;
        s = u - k;
    }
    const k1 = N > 1 ? k + 1 : 0;
    const h = track.step;
    const s2 = s * s, s3 = s2 * s;
    const h00 = 2 * s3 - 3 * s2 + 1, h10 = (s3 - 2 * s2 + s) * h, h01 = -2 * s3 + 3 * s2, h11 = (s3 - s2) * h;

    const x = h00 * track.x[k] + h10 * track.dx[k] + h01 * track.x[k1] + h11 * track.dx[k1];
    const y = h00 * track.y[k] + h10 * track.dy[k] + h01 * track.y[k1] + h11 * track.dy[k1];
    r.alt = h00 * track.z[k] + h10 * track.dz[k] + h01 * track.z[k1] + h11 * track.dz[k1];
    r.lat = track.lat0 + y / track.ky;
    r.lon = track.lon0 + x / track.kx;

    const H = h00 * track.head[k] + h10 * track.omega[k] + h01 * track.head[k1] + h11 * track.omega[k1];
    let hd = (H * DEG) % 360;
    if (hd < 0) hd += 360;
    r.heading = hd;

    const b = h00 * track.bank[k] + h10 * track.bankRate[k] + h01 * track.bank[k1] + h11 * track.bankRate[k1];
    r.bank = b > track.maxBank ? track.maxBank : b < -track.maxBank ? -track.maxBank : b;
    const p = h00 * track.pitch[k] + h10 * track.pitchRate[k] + h01 * track.pitch[k1] + h11 * track.pitchRate[k1];
    r.pitch = p > track.maxPitch ? track.maxPitch : p < -track.maxPitch ? -track.maxPitch : p;

    const l0 = 1 - s;
    r.speed = (l0 * track.speed[k] + s * track.speed[k1]) * 3.6;
    r.vario = l0 * track.vario[k] + s * track.vario[k1];
    r.turnRate = (l0 * track.omega[k] + s * track.omega[k1]) * DEG;
    return r;
}

// ---------------------------------------------------------------------------------------------
// Outils numériques
// ---------------------------------------------------------------------------------------------

function cleanPoints(points) {
    const list = [];
    for (const p of points) {
        if (!p) continue;
        const lat = Number(p.lat), lon = Number(p.lon), t = Number(p.t);
        if (!Number.isFinite(lat) || !Number.isFinite(lon) || !Number.isFinite(t)) continue;
        const alt = Number(p.alt);
        list.push({lat, lon, alt: Number.isFinite(alt) ? alt : NaN, t});
    }
    let sorted = true;
    for (let i = 1; i < list.length; i++) if (list[i].t < list[i - 1].t) { sorted = false; break; }
    if (!sorted) list.sort((a, b) => a.t - b.t);
    const t = [], lat = [], lon = [], alt = [];
    for (let i = 0; i < list.length;) {
        let j = i, sLat = 0, sLon = 0, sAlt = 0, cAlt = 0;
        while (j < list.length && list[j].t - list[i].t < 1e-6) {
            sLat += list[j].lat; sLon += list[j].lon;
            if (Number.isFinite(list[j].alt)) { sAlt += list[j].alt; cAlt++; }
            j++;
        }
        const c = j - i;
        t.push(list[i].t); lat.push(sLat / c); lon.push(sLon / c); alt.push(cAlt ? sAlt / cAlt : NaN);
        i = j;
    }
    // altitudes manquantes : dernière connue (ou première connue en tête, sinon 0)
    let last = alt.find(Number.isFinite);
    if (last === undefined) last = 0;
    for (let i = 0; i < alt.length; i++) {
        if (Number.isFinite(alt[i])) last = alt[i]; else alt[i] = last;
    }
    return {t, lat, lon, alt};
}

/** Filtre de Hampel simplifié : médiane sur 7 points, remplace l'altitude si l'écart dépasse `limit`. */
function despike(alt, limit) {
    const n = alt.length;
    const out = Float64Array.from(alt);
    if (n < 5) return out;
    const win = [];
    for (let i = 0; i < n; i++) {
        win.length = 0;
        for (let j = Math.max(0, i - 3); j <= Math.min(n - 1, i + 3); j++) win.push(alt[j]);
        win.sort((a, b) => a - b);
        const med = win[win.length >> 1];
        if (Math.abs(alt[i] - med) > limit) out[i] = med;
    }
    return out;
}

function halfWindow(seconds, h) {
    return Math.max(2, Math.round(seconds / h / 2));
}

const sgCache = new Map();

/** Coefficients Savitzky-Golay (moindres carrés polynomiaux) pour offsets -L..R, ordre, dérivée. */
function sgCoefficients(L, R, order, deriv) {
    const key = `${L},${R},${order},${deriv}`;
    let c = sgCache.get(key);
    if (c) return c;
    const m = order + 1;
    const M = new Float64Array(m * m);
    for (let k = -L; k <= R; k++) {
        for (let i = 0; i < m; i++) for (let j = 0; j < m; j++) M[i * m + j] += k ** (i + j);
    }
    const inv = invert(M, m);
    let fact = 1;
    for (let i = 2; i <= deriv; i++) fact *= i;
    c = new Float64Array(L + R + 1);
    for (let k = -L; k <= R; k++) {
        let v = 0;
        for (let j = 0; j < m; j++) v += inv[deriv * m + j] * k ** j;
        c[k + L] = v * fact;
    }
    if (sgCache.size > 4096) sgCache.clear();
    sgCache.set(key, c);
    return c;
}

function invert(A, m) {
    const a = Float64Array.from(A);
    const inv = new Float64Array(m * m);
    for (let i = 0; i < m; i++) inv[i * m + i] = 1;
    for (let col = 0; col < m; col++) {
        let piv = col;
        for (let r = col + 1; r < m; r++) if (Math.abs(a[r * m + col]) > Math.abs(a[piv * m + col])) piv = r;
        if (piv !== col) {
            for (let j = 0; j < m; j++) {
                let tmp = a[col * m + j]; a[col * m + j] = a[piv * m + j]; a[piv * m + j] = tmp;
                tmp = inv[col * m + j]; inv[col * m + j] = inv[piv * m + j]; inv[piv * m + j] = tmp;
            }
        }
        const d = a[col * m + col] || 1e-300;
        for (let j = 0; j < m; j++) { a[col * m + j] /= d; inv[col * m + j] /= d; }
        for (let r = 0; r < m; r++) {
            if (r === col) continue;
            const f = a[r * m + col];
            if (f === 0) continue;
            for (let j = 0; j < m; j++) { a[r * m + j] -= f * a[col * m + j]; inv[r * m + j] -= f * inv[col * m + j]; }
        }
    }
    return inv;
}

/**
 * Savitzky-Golay sur src[a..b] → dst[a..b]. Fenêtre 2m+1 décalée à l'intérieur du tronçon aux bords
 * (pas de remplissage artificiel) ; tronçon plus court → fenêtre = tronçon, ordre réduit.
 */
function savitzkyGolay(src, dst, a, b, m, order, deriv, h) {
    const len = b - a + 1;
    const scale = deriv ? 1 / h ** deriv : 1;
    if (len === 1) { dst[a] = deriv ? 0 : src[a]; return; }
    const width = Math.min(2 * m + 1, len);
    const ord = Math.min(order, width - 1);
    if (deriv > ord) { for (let i = a; i <= b; i++) dst[i] = 0; return; }
    const half = (width - 1) >> 1;
    for (let i = a; i <= b; i++) {
        let lo = i - half;
        if (lo < a) lo = a;
        if (lo + width - 1 > b) lo = b - width + 1;
        const L = i - lo, R = lo + width - 1 - i;
        // bords de tronçon (fenêtre décentrée) : un polynôme d'ordre élevé extrapole le bruit →
        // ordre réduit à deriv+1 (droite pour la position, parabole pour la vitesse)
        const c = sgCoefficients(L, R, L === R ? ord : Math.min(ord, deriv + 1), deriv);
        let v = 0;
        for (let k = 0; k < width; k++) v += c[k] * src[lo + k];
        dst[i] = v * scale;
    }
}

function centralDiff(v, h) {
    const N = v.length;
    const d = new Float64Array(N);
    if (N < 2) return d;
    d[0] = (v[1] - v[0]) / h;
    d[N - 1] = (v[N - 1] - v[N - 2]) / h;
    for (let k = 1; k < N - 1; k++) d[k] = (v[k + 1] - v[k - 1]) / (2 * h);
    return d;
}

/** Lissage gaussien à phase nulle (σ en échantillons), bords renormalisés. */
function gaussianInPlace(v, sigma) {
    const N = v.length;
    if (!(sigma > 0.3) || N < 3) return;
    const r = Math.ceil(3 * sigma);
    const w = new Float64Array(2 * r + 1);
    for (let k = -r; k <= r; k++) w[k + r] = Math.exp(-0.5 * (k / sigma) ** 2);
    const src = Float64Array.from(v);
    for (let i = 0; i < N; i++) {
        const lo = Math.max(0, i - r), hi = Math.min(N - 1, i + r);
        // pour préserver les rampes aux bords, fenêtre symétrique tronquée
        const rr = Math.min(i - lo, hi - i);
        let s = 0, ws = 0;
        for (let j = i - rr; j <= i + rr; j++) { const wj = w[j - i + r]; s += wj * src[j]; ws += wj; }
        v[i] = s / ws;
    }
}

/**
 * Filtre du 2e ordre critique (ωn = 1/τ) avec limitation de taux et saturation, exécuté dans le sens
 * du temps puis à rebours ; sortie = moyenne (phase nulle). Intégration semi-implicite à ~20 ms.
 */
function zeroPhaseSecondOrder(u, out, outRate, h, tau, maxRate, sat) {
    const N = u.length;
    const fwd = new Float64Array(N), fwdRate = new Float64Array(N);
    const bwd = new Float64Array(N), bwdRate = new Float64Array(N);
    runSecondOrder(u, fwd, fwdRate, h, tau, maxRate, sat, false);
    runSecondOrder(u, bwd, bwdRate, h, tau, maxRate, sat, true);
    for (let k = 0; k < N; k++) {
        out[k] = 0.5 * (fwd[k] + bwd[k]);
        outRate[k] = 0.5 * (fwdRate[k] - bwdRate[k]);
    }
}

function runSecondOrder(u, y, yRate, h, tau, maxRate, sat, reverse) {
    const N = u.length;
    const wn = 1 / tau;
    const S = Math.max(1, Math.ceil(h / 0.02));
    const ds = h / S;
    const idx = reverse ? (k) => N - 1 - k : (k) => k;
    let phi = u[idx(0)], p = 0;
    y[idx(0)] = phi; yRate[idx(0)] = 0;
    for (let k = 0; k < N - 1; k++) {
        const u0 = u[idx(k)], u1 = u[idx(k + 1)];
        for (let s = 0; s < S; s++) {
            const target = u0 + (u1 - u0) * ((s + 0.5) / S);
            p += (wn * wn * (target - phi) - 2 * wn * p) * ds;
            if (p > maxRate) p = maxRate; else if (p < -maxRate) p = -maxRate;
            phi += p * ds;
            if (phi > sat) { phi = sat; if (p > 0) p = 0; } else if (phi < -sat) { phi = -sat; if (p < 0) p = 0; }
        }
        y[idx(k + 1)] = phi; yRate[idx(k + 1)] = p;
    }
}

/** Moyenne d'un limiteur de pente causal et anti-causal : pente bornée par `maxStep`, sans retard. */
function rateLimitZeroPhase(v, maxStep) {
    const N = v.length;
    if (N < 2) return;
    const f = new Float64Array(N), b = new Float64Array(N);
    f[0] = v[0];
    for (let k = 1; k < N; k++) f[k] = clamp(v[k], f[k - 1] - maxStep, f[k - 1] + maxStep);
    b[N - 1] = v[N - 1];
    for (let k = N - 2; k >= 0; k--) b[k] = clamp(v[k], b[k + 1] - maxStep, b[k + 1] + maxStep);
    for (let k = 0; k < N; k++) v[k] = 0.5 * (f[k] + b[k]);
}

function clamp(v, lo, hi) { return v < lo ? lo : v > hi ? hi : v; }
function smoothstep(x) { const c = clamp(x, 0, 1); return c * c * (3 - 2 * c); }
function wrapPi(a) { return a - 2 * Math.PI * Math.floor((a + Math.PI) / (2 * Math.PI)); }
