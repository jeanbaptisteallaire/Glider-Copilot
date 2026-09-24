# Règles R8 de la version publiée (minification activée en session 9).
# Le code GLIDY n'utilise ni réflexion ni sérialisation par annotations : les bibliothèques
# (MapLibre, OkHttp, Compose, DataStore) apportent leurs propres règles « consumer ».
# Garder numéros de ligne et nom de fichier pour lire les traces de plantage de la Play Console
# (le fichier mapping.txt est à téléverser avec chaque AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
