# Response to App Review — Guideline 2.1 (Information Needed)

To paste in App Store Connect (reply to the message) **and** in App Review Information > Notes.
The screen recording (point 1) is attached separately.

---

Hello, thank you for your review. Please find the requested information below.

**1. Screen recording**
Attached: recording made on a physical iPad Pro running iPadOS 27 (latest), with the iPhone app in compatibility mode (the app targets iPhone). It shows: guest access, Prévol (weather), offline map download, Pilotage (flight screen, DÉMO simulated flight), REC recording, Mes vols (logbook), Sign in with Apple, then account deletion (Mes vols > Sauvegarde en ligne > "Supprimer mon compte et mes vols en ligne").
The app has no user-generated content shared with other users (flights are private to their owner) and no paid content.

**2. Purpose and target audience**
Wind Glider is a secondary soaring aid for glider pilots (gliding club members, students with their instructor's approval). It answers three questions: will the day be soarable (thermal trigger time, cloud base, end of thermals at the pilot's airfield), can I get back safely (required glide ratio and safety margin to the chosen airfield, wind included, computed offline), and how did my flight go (IGC track recording and 3D replay). It does not replace certified instruments, FLARM or lookout; this is stated in the app before first use.

**3. How to access the features**
No login is required: tap "Continuer en invité" on the welcome screen, then "J'ai compris".
- Prévol tab: weather for the nearest airfield (or pick another at the top). "Télécharger" downloads the offline map pack (relief and airfields).
- Pilotage tab: tap "Compris", then the orange "DÉMO" button to see a simulated flight (no need to fly).
- Mes vols tab: logbook; "Importer un IGC" accepts any standard IGC file.
- Optional account (online backup of flights): Sign in with Apple or Google in Mes vols > Sauvegarde en ligne. Deletion: same card, "Supprimer mon compte et mes vols en ligne", which deletes the account and all stored flights immediately.

**4. External services**
- PRECOG (precog-api.com): weather forecasts, source Météo-France (ARPEGE/AROME models).
- Open Glider Network (APRS servers and device database): nearby glider traffic, read-only; the app never broadcasts the user's position.
- openAIP: airfields and airspace data; OpenStreetMap/Protomaps: map background; Copernicus GLO-30: terrain elevation. Offline packs are downloaded from GitHub.
- Apple MapKit: map display.
- Supabase (EU, Ireland): optional account and flight backup.
- Sign in with Apple and Google Sign-In: optional authentication.
No payment processor, no advertising, no analytics, no AI service.

**5. Regional differences**
The app works the same way in every region. Weather forecasts and the bundled club directory cover France (Météo-France models); elsewhere, the flight screen, safety computer, recording and logbook work identically, and weather data may be unavailable.

**6. Regulated industry / third-party material**
The app is not a certified aviation instrument and is presented as a secondary aid only. Third-party data are used under their licences, with attribution shown in the app: Météo-France data via PRECOG (licensed API key), OpenStreetMap (ODbL), openAIP (CC BY-NC), Copernicus DEM, Open Glider Network (ODbL, respecting the OGN DDB privacy settings).

Best regards,
Jean-Baptiste Allaire
