/**
 * GLIDY — S14 : passage de l'attitude (cap, tangage, inclinaison) au repère de rendu du rejeu 3D.
 *
 * Le repère local du rejeu est x = est, y = haut, z = nord : c'est un repère MAIN GAUCHE (est × haut = sud).
 * Le modèle du planeur est modélisé en main droite (nez +Z, dessus +Y, aile droite −X). Posé tel quel, il
 * apparaissait en miroir : son aile « droite » à l'ouest quand il vole vers le nord, c'est-à-dire à gauche,
 * et l'inclinaison « aile droite basse » abaissait l'aile extérieure au virage (défaut vu par JB en V0.9.3).
 *
 * Correction : le modèle est symétrisé en X (aile droite → +X = est en cap nord), et l'angle de roulis est
 * appliqué avec le signe opposé dans l'ordre d'Euler 'YXZ' de three.js. Vérifié par
 * `src/test/js/attitude-frame.test.mjs` (aile basse = côté intérieur du virage, dans les deux sens).
 */
export const MODEL_MIRROR_X = -1;
export const BANK_SIGN = -1;

/** Angles d'Euler three.js (radians, ordre 'YXZ') du rig pour une attitude en degrés. */
export function rigEuler(headingDeg, pitchDeg, bankDeg) {
    const r = Math.PI / 180;
    return {x: -pitchDeg * r, y: headingDeg * r, z: BANK_SIGN * bankDeg * r, order: 'YXZ'};
}
