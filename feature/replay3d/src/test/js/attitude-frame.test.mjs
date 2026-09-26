// S14 — le planeur du rejeu 3D doit toujours s'incliner vers l'intérieur du virage.
// Réimplémente la chaîne de three.js (Euler 'YXZ' → matrice) sans dépendance, et vérifie le résultat
// dans le repère physique local (x est, y haut, z nord).
import test from 'node:test';
import assert from 'node:assert/strict';
import {MODEL_MIRROR_X, rigEuler} from '../../main/assets/replay3d/attitude-frame.mjs';

const mul = (a, b) => a.map((row, i) => b[0].map((_, j) => row.reduce((s, _, k) => s + a[i][k] * b[k][j], 0)));
const Rx = a => [[1, 0, 0], [0, Math.cos(a), -Math.sin(a)], [0, Math.sin(a), Math.cos(a)]];
const Ry = a => [[Math.cos(a), 0, Math.sin(a)], [0, 1, 0], [-Math.sin(a), 0, Math.cos(a)]];
const Rz = a => [[Math.cos(a), -Math.sin(a), 0], [Math.sin(a), Math.cos(a), 0], [0, 0, 1]];
const apply = (m, v) => m.map(row => row[0] * v[0] + row[1] * v[1] + row[2] * v[2]);

/** position (x est, y haut, z nord) du saumon droit et du nez, pour une attitude donnée */
function pose(heading, pitch, bank) {
    const e = rigEuler(heading, pitch, bank);
    const R = mul(mul(Ry(e.y), Rx(e.x)), Rz(e.z)); // three.js 'YXZ' : R = Ry · Rx · Rz
    const mirror = [[MODEL_MIRROR_X, 0, 0], [0, 1, 0], [0, 0, 1]];
    const M = mul(R, mirror);
    return {rightTip: apply(M, [-7.5, 0, 0]), nose: apply(M, [0, 0, 4.9])}; // modèle : aile droite −X, nez +Z
}

test('cap nord, ailes à plat : nez au nord, aile droite à l\'est', () => {
    const p = pose(0, 0, 0);
    assert.ok(p.nose[2] > 4.8 && Math.abs(p.nose[0]) < 1e-9);
    assert.ok(p.rightTip[0] > 7.4, `aile droite x=${p.rightTip[0]}`);
});

test('cap est : nez à l\'est, aile droite au sud', () => {
    const p = pose(90, 0, 0);
    assert.ok(p.nose[0] > 4.8);
    assert.ok(p.rightTip[2] < -7.4);
});

test('virage à droite (bank +40°) : aile droite basse, pour tous les caps', () => {
    for (let h = 0; h < 360; h += 30) {
        const p = pose(h, 0, 40);
        assert.ok(p.rightTip[1] < -4.5, `cap ${h} : saumon droit y=${p.rightTip[1].toFixed(2)}`);
    }
});

test('virage à gauche (bank −40°) : aile droite haute', () => {
    for (let h = 0; h < 360; h += 30) assert.ok(pose(h, 0, -40).rightTip[1] > 4.5);
});

test('repère physique : l\'aile la plus basse est TOUJOURS du côté intérieur du virage', () => {
    for (const bank of [40, -40]) {
        for (let h = 0; h < 360; h += 15) {
            const p = pose(h, 0, bank);
            const r = Math.PI / 180;
            // côté intérieur (horizontal, x est / z nord) : droite du cap pour un virage à droite
            const inside = bank > 0 ? [Math.sin((h + 90) * r), Math.cos((h + 90) * r)] : [Math.sin((h - 90) * r), Math.cos((h - 90) * r)];
            // le saumon le plus bas (l'autre saumon est l'opposé du droit, le modèle étant symétrique)
            const low = p.rightTip[1] < 0 ? p.rightTip : p.rightTip.map(v => -v);
            const side = low[0] * inside[0] + low[2] * inside[1];
            assert.ok(side > 5, `cap ${h}, bank ${bank} : aile basse côté ${side > 0 ? 'intérieur' : 'EXTÉRIEUR'}`);
        }
    }
});

test('tangage +10° : nez haut', () => {
    assert.ok(pose(45, 10, 0).nose[1] > 0.8);
});
