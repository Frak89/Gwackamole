# Gwackamole

App Android anti-distrazioni: quando arriva una distrazione arrivano le talpe.
Martellale tutte e guadagni minuti di utilizzo; se vincono loro lo schermo si spegne, il telefono si blocca e parte un conto alla rovescia.

## Cosa fa

| Funzione | Come |
|---|---|
| **Timer per app** | Ogni app in blacklist ha minuti al giorno. Si allungano subito solo vincendo il minigioco (+5/10/15 min). Allungarli dalle impostazioni passa dal cooldown. |
| **Secondi prima del minigioco** | Tolleranza per app (0–10 s) e per i siti per adulti (subito o fino a 5 s). |
| **Minigioco progressivo** | Si parte con 1 talpa; ogni nuovo tentativo sullo stesso obiettivo ne aggiunge una, fino a 10. Dentro al round ogni 2 talpe si sale di livello e tutto accelera. Talpa d'oro = vale 2; talpa col caschetto = esca, se la colpisci perdi una vita. La difficoltà sale anche con le sconfitte del giorno e col "rischio" dell'app. |
| **Le talpe vincono** | `lockNow()` spegne lo schermo; al riavvio c'è il conto alla rovescia (15 min + 5 per ogni sconfitta del giorno). Chiamate d'emergenza sempre possibili. |
| **Niente fughe dal gioco** | Con Device Owner: lock-task mode (Home, Recenti e swipe dal basso disattivati dal sistema). Senza: immersivo, Indietro ignorato, gesture laterali escluse, e il servizio riporta il gioco in primo piano se esci. |
| **Talpe a sorpresa** | Opzionali, per app. Probabilità adattata a quanto quella app ti "cattura". |
| **Troppo schermo** | Dopo N minuti di schermo acceso di fila (configurabile). |
| **Siti per adulti** | Blocco per dominio (lista inclusa + imparati + liste pubbliche) o per parole nell'URL. |
| **Filtro sui risultati di ricerca** | Nei risultati di Google/Bing/DuckDuckGo… i link con parole vietate vengono coperti da un banner bianco con la talpa (overlay di accessibilità). Tocchi → minigioco; la ricerca resta intatta. |
| **Impara i siti** | Se titolo o snippet di un risultato contiene porn/porno/xxx/hentai…, il dominio va in blacklist da solo. Nessuno scraping dei motori di ricerca: usa solo i risultati che hai davanti. |
| **Protezioni (stile AppBlock)** | Con le protezioni armate: Impostazioni › App › Gwackamole, il dialogo di disinstallazione e la pagina Accessibilità ti riportano alla Home. Ogni modifica che allenta le regole parte dopo il cooldown (default 24 h). |
| **Device Owner** | Blocca disinstallazione, arresto forzato e modalità provvisoria; opzionale anche il debug USB. |
| **Analisi del comportamento** | Aperture, minuti, sessione media, orario di picco, vittorie/sconfitte, tentativi di bypass (ultimi 7 giorni, tutto sul telefono). |

## Build

Il workflow `.github/workflows/android.yml` compila l'APK a ogni push: lo trovi in **Actions › ultimo run › Artifacts**.

In locale (Android Studio o riga di comando, JDK 17, Android SDK 35):

```bash
gradle assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Il progetto non ha dipendenze esterne: solo API di piattaforma (minSdk 26, targetSdk 35).

## Installazione sul telefono

1. Installa l'APK.
2. Android 13+: Impostazioni › App › Gwackamole › ⋮ › **Consenti impostazioni limitate** (altrimenti l'Accessibilità resta grigia per le app non dal Play Store).
3. Apri Gwackamole e attiva i permessi dalla lista.
4. **Device Owner (consigliato)** — rimuovi temporaneamente gli account (Google compreso), poi da PC:
   ```bash
   adb shell dpm set-device-owner com.frak89.gwackamole/.admin.GwackAdminReceiver
   ```
   e riaggiungi gli account.
5. Aggiungi le app in blacklist, poi **Arma le protezioni**.

### Come uscirne
Apri l'app › **Disattiva protezioni**. Parte dopo il cooldown; con Device Owner viene rimosso anche quello, così puoi disinstallare.

## Struttura

```
core/     Guardian (decisioni), Behavior (analisi), Protection (admin/owner), UsageTracker, PendingApplier (cooldown)
data/     Store (impostazioni), Blocklist (domini e parole), EventLog (diario)
game/     MoleGameView (gioco), MoleGameActivity, MolePainter (la talpa, disegnata in codice)
service/  GwackAccessibilityService, MoleOverlay (banner sui risultati)
ui/       Main, Apps, Sites, Rules, Stats, Lock, Guard
```

## Limiti noti

- L'ID della barra degli indirizzi cambia tra browser e versioni: quelli noti sono in `URL_BAR_IDS`; per gli altri c'è un riconoscimento generico.
- Senza Device Owner la gesture Home non si può bloccare del tutto: il gioco viene riportato in primo piano in ~0,3 s.
- Le parole per riconoscere le pagine pericolose nelle Impostazioni sono in italiano e inglese; altri produttori/lingue possono richiedere aggiunte in `DANGER_WORDS`.
