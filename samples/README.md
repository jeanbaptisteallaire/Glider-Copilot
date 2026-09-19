# Traces IGC de test

`saint-martin-de-londres-vol-synthetique.igc` est une trace générée, déterministe et non certifiée. Elle ne correspond à aucun vol réel.

Le scénario dure 1 h 53 min à un point par seconde. Il comprend un décollage, trois ascendances en spirale, plusieurs transitions et un retour à Saint-Martin-de-Londres. Il sert à contrôler le parseur, la déduplication, la reconstruction de l'archive, le profil d'altitude et le dessin de la trace.

Pour la régénérer :

```bash
./tools/generate_test_igc.py
```
