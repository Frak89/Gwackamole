# Nero Block

App Android anti-distrazioni, **versione senza minigioco**. È la gemella di Gwackamole (branch `gwackamole-v0.1`): stesso scopo e stesse protezioni, ma al posto delle talpe da martellare il blocco è secco — **uscita forzata dall'app e telefono in pausa per un minuto** (durata regolabile).

> Le due edizioni hanno `applicationId` diversi (`com.frak89.neroblock` qui, `com.frak89.gwackamole` nell'altra), quindi si possono installare entrambe sullo stesso telefono.

## Cosa fa

| Funzione | Come |
|---|---|
| **Blocco secco** | Quando scatta un blocco l'app in primo piano si chiude (torni alla Home) e lo schermo si spegne. Al riavvio trovi un conto alla rovescia (default 1 min, +60 s per ogni blocco successivo nella stessa giornata). Nessun gioco, nessun modo di “vincere” tempo. |
| **Testi personalizzati** | Messaggi scritti da te che compaiono a caso nella schermata di pausa e quando provi ad aggirare le regole. |
| **Timer per app** | Ogni app in blacklist ha minuti al giorno. Accorciarli vale subito; allungarli o togliere l'app passa dal cooldown. Finito il tempo → blocco. |
| **Blocchi a sorpresa** | Opzionali, per app, con probabilità adattata a quanto quell'app ti “cattura”. Più un limite per troppo tempo di schermo continuo. |
| **Siti per adulti** | Blocco per dominio (lista inclusa + imparati dalle ricerche + liste pubbliche) o per parole nell'URL. |
| **Filtro sui risultati di ricerca** | Nei risultati di Google/Bing/DuckDuckGo… i link vietati vengono coperti da un banner **dello stesso colore di fondo della pagina** (chiaro o scuro), senza bordo, così si confonde con essa. Toccarlo blocca il telefono; la ricerca resta intatta. I banner si riusano e si spostano con lo scroll, così reagiscono subito. |
| **Impara i siti** | Se titolo o snippet di un risultato contiene porn/porno/xxx/hentai…, il dominio va in blacklist da solo. Nessuno scraping dei motori: usa solo i risultati che hai davanti. |
| **Pulizia cronologia** | Quando un sito viene bloccato, le sue voci spariscono dalla cronologia del browser (browser Chromium: Chrome, Brave, Edge, Kiwi, Vivaldi…). Durante la pulizia uno scudo copre lo schermo. |
| **Blocco impostazioni mirato** | Scatta solo quando sei davvero sulla pagina di gestione di Nero Block (Info app, disinstalla, forza arresto, togli accessibilità/admin), non su impostazioni qualsiasi che la nominano. Il blocco = chiusura forzata delle impostazioni + un testo. |
| **Device Owner** | Blocca disinstallazione, arresto forzato e modalità provvisoria; opzionale anche il debug USB. |
| **Analisi del comportamento** | Aperture, minuti, sessione media, orario di picco, blocchi scattati, tentativi di bypass (ultimi 7 giorni, tutto sul telefono). |

## Build

Il workflow `.github/workflows/android.yml` compila l'APK a ogni push (Actions › ultimo run › Artifacts).

In locale (JDK 17, Android SDK 35):

```bash
gradle assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Nessuna dipendenza esterna: solo API di piattaforma (minSdk 26, targetSdk 35).

## Installazione

1. Installa l'APK.
2. Android 13+: Impostazioni › App › Nero Block › ⋮ › **Consenti impostazioni limitate**.
3. Apri Nero Block e attiva i permessi dalla lista.
4. **Device Owner (consigliato)** — rimuovi temporaneamente gli account, poi da PC:
   ```bash
   adb shell dpm set-device-owner com.frak89.neroblock/com.frak89.gwackamole.admin.GwackAdminReceiver
   ```
   e riaggiungi gli account.
5. Aggiungi le app in blacklist, poi **Arma le protezioni**.

### Come uscirne
Apri l'app › **Disattiva protezioni**. Parte dopo il cooldown; con Device Owner viene rimosso anche quello, così puoi disinstallare.

## Rapporto con Gwackamole

Il codice condivide il package interno `com.frak89.gwackamole`. Rispetto a `gwackamole-v0.1` qui mancano il minigioco (`game/`) e tutta la logica di premi/vittorie: `Guardian` ha un'unica strada, `block()`, che chiude l'app e avvia la pausa.

## Limiti noti

- La pulizia della cronologia guida l'interfaccia del browser via accessibilità: è best-effort e i nomi dei pulsanti variano tra browser e lingue (`HISTORY_DELETE_WORDS`).
- Senza Device Owner la gesture Home non si può bloccare del tutto.
- Le parole per riconoscere le pagine pericolose nelle Impostazioni sono in italiano e inglese (`DANGER_WORDS`).
- Compila senza errori con kotlinc, ma il comportamento runtime non è stato provato su un dispositivo.
