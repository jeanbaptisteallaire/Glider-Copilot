// Chargeur du modèle 3D de planeur du rejeu (format maison « glidy-mesh » v1).
//
// Modèle : « UNDERPOLY: Free Sailplane Glider » par UNDERPOLY Project, CC BY 4.0
// (voir vendor/licenses/GLIDER-MODEL-LICENSE.txt). Converti depuis
// V1_SailplaneGlider_1_Standard.fbx : ~8 000 triangles, livrée 2 (blanc / bandes rouges).
//
// Fichiers (dans baseUrl) :
//   glider.json         layout du binaire, matériaux, bbox, envergure, longueur
//   glider.bin          par pièce : positions float32x3, normales int8x3 normalisées,
//                       uv uint16x2 normalisées (convention glTF), indices uint16
//   glider-livery.png   texture de livrée (fuselage, ailes, gouvernes)
//   glider-palette.png  texture-palette (intérieur, train, roues)
//
// Repère : mètres, nez vers +Z, dessus vers +Y, aile droite vers −X, origine au centre
// de gravité approché (25 % de la corde d'emplanture). Échelle 1:1 (envergure ~15 m) :
// l'appelant applique son propre facteur d'exagération (group.scale.setScalar(k)).
//
// Aucune dépendance : l'objet THREE (r169) est passé par l'appelant. Rejette la promesse
// en cas d'erreur (réseau, format, texture) pour permettre le repli sur le modèle procédural.

const FORMAT = 'glidy-mesh';

async function fetchOk(url, kind) {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`glider-model: HTTP ${response.status} pour ${url}`);
    return kind === 'json' ? response.json() : response.arrayBuffer();
}

async function loadTexture(THREE, url, nearest) {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`glider-model: HTTP ${response.status} pour ${url}`);
    const blob = await response.blob();
    let image;
    if (typeof createImageBitmap === 'function') {
        // flipY reste à false : les UV sont en convention glTF (origine en haut à gauche).
        image = await createImageBitmap(blob, {imageOrientation: 'none', premultiplyAlpha: 'none', colorSpaceConversion: 'none'});
    } else {
        image = await new Promise((resolve, reject) => {
            const img = new Image();
            const objectUrl = URL.createObjectURL(blob);
            img.onload = () => { URL.revokeObjectURL(objectUrl); resolve(img); };
            img.onerror = () => { URL.revokeObjectURL(objectUrl); reject(new Error(`glider-model: image illisible ${url}`)); };
            img.src = objectUrl;
        });
    }
    const texture = new THREE.Texture(image);
    texture.flipY = false;
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.wrapS = texture.wrapT = THREE.ClampToEdgeWrapping;
    if (nearest) {
        // Texture-palette : pas de mipmaps pour éviter le débordement entre cases de couleur.
        texture.generateMipmaps = false;
        texture.minFilter = THREE.LinearFilter;
        texture.magFilter = THREE.NearestFilter;
    } else {
        texture.anisotropy = 4;
    }
    texture.needsUpdate = true;
    return texture;
}

function makeMaterial(THREE, name, spec, textures) {
    const material = new THREE.MeshStandardMaterial({
        name: `glider-${name}`,
        color: new THREE.Color(spec.color || '#ffffff'),
        roughness: spec.roughness ?? 0.5,
        metalness: spec.metalness ?? 0,
        flatShading: false, // le modèle a des normales lissées : on les garde
    });
    if (spec.map) material.map = textures[spec.map];
    if (spec.opacity != null && spec.opacity < 1) {
        material.transparent = true;
        material.opacity = spec.opacity;
        material.depthWrite = false;
    }
    return material;
}

/**
 * Charge le planeur et renvoie un THREE.Group (nom « GLIDY sailplane »), une pièce par
 * enfant : Body, Door, Aileron_L, Aileron_R, Rudder, LandingGear, LandingGear_Cable,
 * LandingGear_Door_L/R, Wheel_F, Wheel_R. `group.userData.glider` contient les
 * dimensions (wingSpan, length, height en m) et la bbox.
 *
 * @param {object} THREE     module three.js (r169)
 * @param {string} baseUrl   dossier contenant glider.json/bin et les PNG (défaut './')
 * @returns {Promise<THREE.Group>}
 */
export async function loadGliderModel(THREE, baseUrl = './') {
    if (!THREE || typeof THREE.Group !== 'function') throw new Error('glider-model: objet THREE manquant');
    const base = baseUrl.endsWith('/') ? baseUrl : `${baseUrl}/`;
    const meta = await fetchOk(`${base}glider.json`, 'json');
    if (meta.format !== FORMAT || meta.version !== 1) throw new Error('glider-model: format inattendu');
    const [bin, textures] = await Promise.all([
        fetchOk(`${base}glider.bin`, 'bin'),
        (async () => {
            const entries = Object.values(meta.materials).filter(m => m.map);
            const loaded = await Promise.all(entries.map(m => loadTexture(THREE, `${base}${m.map}`, !!m.nearest)));
            const byName = {};
            entries.forEach((m, i) => { byName[m.map] = loaded[i]; });
            return byName;
        })(),
    ]);
    if (meta.binByteLength && bin.byteLength !== meta.binByteLength) throw new Error('glider-model: glider.bin tronqué');

    const materials = {};
    for (const [name, spec] of Object.entries(meta.materials)) materials[name] = makeMaterial(THREE, name, spec, textures);

    const group = new THREE.Group();
    group.name = 'GLIDY sailplane';
    for (const part of meta.parts) {
        const n = part.vertexCount;
        const geometry = new THREE.BufferGeometry();
        geometry.setAttribute('position', new THREE.BufferAttribute(new Float32Array(bin, part.position, n * 3), 3));
        geometry.setAttribute('normal', new THREE.BufferAttribute(new Int8Array(bin, part.normal, n * 3), 3, true));
        geometry.setAttribute('uv', new THREE.BufferAttribute(new Uint16Array(bin, part.uv, n * 2), 2, true));
        geometry.setIndex(new THREE.BufferAttribute(new Uint16Array(bin, part.index, part.indexCount), 1));
        const partMaterials = [];
        for (const g of part.groups) {
            let slot = partMaterials.indexOf(materials[g.material]);
            if (slot < 0) { slot = partMaterials.length; partMaterials.push(materials[g.material]); }
            geometry.addGroup(g.start, g.count, slot);
        }
        geometry.computeBoundingBox();
        geometry.computeBoundingSphere();
        const mesh = new THREE.Mesh(geometry, partMaterials.length === 1 ? partMaterials[0] : partMaterials);
        if (partMaterials.length === 1) geometry.clearGroups();
        mesh.name = part.name;
        // Verrière transparente dessinée après l'opaque.
        if (part.groups.some(g => g.material === 'glass')) mesh.renderOrder = 1;
        group.add(mesh);
    }
    group.userData.glider = {
        wingSpan: meta.wingSpan,
        length: meta.length,
        height: meta.height,
        bbox: meta.bbox,
        triangles: meta.triangles,
        attribution: meta.source,
    };
    return group;
}

export default loadGliderModel;
