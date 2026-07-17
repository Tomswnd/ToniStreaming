# ToniStreaming

ToniStreaming e un client Android nativo sviluppato in Kotlin e Jetpack Compose per la fruizione di contenuti anime da AnimeUnity. L'applicazione effettua lo scraping dei contenuti in modo asincrono, estrae i flussi video ad alta risoluzione (1080p) da Vixcloud e gestisce localmente lo stato di avanzamento della riproduzione ("Continua a guardare") tramite un database locale Room.

Il progetto e strutturato in due varianti di compilazione indipendenti (Product Flavors) per ottimizzare le prestazioni e ridurre le dimensioni del pacchetto finale:
- Variante Android TV: Ottimizzata per la navigazione tramite telecomando (D-pad).
- Variante Mobile: Ottimizzata per l'interazione touch su smartphone e tablet, con rotazione automatica dello schermo in orizzontale durante la riproduzione video.

---

## Funzionalita Principali

- Estrazione automatica dello streaming MP4 a 1080p da Vixcloud bypassando i controlli di sicurezza.
- Cronologia locale con salvataggio automatico del punto di riproduzione.
- Sezione "Continua a guardare" in Home Screen, con badge dell'ultimo episodio visto.
- Download degli episodi per la visione offline (variante Mobile): avvio dalla pagina dell'anime con notifica di avanzamento, sezione "Download" in Home Screen e riproduzione della copia locale anche senza connessione.
- Sincronizzazione automatica e silenziosa di cronologia e preferiti tra dispositivi con l'app sulla stessa rete Wi-Fi.
- Funzione di ricerca globale reattiva.
- Pulsante per la riproduzione dell'episodio successivo integrato nel lettore (con countdown automatico).
- Controlli del lettore condivisi tra le varianti, con fallback automatico di qualità sui dispositivi con decoder limitati.
- Grafica scura personalizzata basata su componenti Material 3.

---

## Guida all'Installazione (Senza PC / Solo Smartphone o TV)

Di seguito sono descritti i passaggi per scaricare e installare l'applicazione direttamente dal dispositivo, senza utilizzare un computer o comandi da terminale.

### Installazione su Smartphone (Android)

1. Apri il browser dello smartphone e naviga sulla pagina GitHub del progetto, nella sezione Releases:
   `https://github.com/Tomswnd/ToniStreaming/releases`
2. Individua l'ultima release pubblicata e scarica il file denominato `app-mobile-release.apk`.
3. Una volta completato il download, fai tap sul file scaricato per avviarlo.
4. Se il sistema Android mostra un avviso di sicurezza relativo a sorgenti sconosciute:
   - Fai tap su Impostazioni nella finestra di avviso.
   - Attiva l'interruttore per consentire l'installazione dal browser o dal file manager in uso.
   - Torna indietro e conferma l'installazione facendo tap su Installa.

### Installazione su Android TV o Amazon Fire TV

Per installare l'applicazione sulla televisione senza PC, il metodo piu semplice consiste nell'utilizzare l'applicazione gratuita Downloader o trasferire il file dal telefono.

#### Metodo A: Tramite l'applicazione Downloader (Consigliato)

1. Installa l'applicazione gratuita Downloader dallo store ufficiale della tua TV (Google Play Store o Amazon Appstore).
2. Abilita le Opzioni Sviluppatore sulla TV:
   - Vai in Impostazioni -> Informazioni (o Informazioni sul sistema).
   - Fai tap per 7 volte consecutive sulla voce Numero build (o sul nome del modello).
3. Abilita l'installazione da sorgenti sconosciute per l'applicazione Downloader:
   - Vai in Impostazioni -> Applicazioni -> Accesso speciale per le applicazioni (o Sicurezza e limitazioni) -> Installa app sconosciute.
   - Attiva l'interruttore per Downloader.
4. Apri l'app Downloader sulla TV, inserisci l'indirizzo web della pagina di download o il link abbreviato diretto all'APK TV (`app-tv-release.apk`) e avvia il download.
5. Al termine dello scaricamento, Downloader avviera automaticamente l'installazione dell'applicazione.

#### Metodo B: Trasferimento da Smartphone (Send Files to TV)

1. Installa l'applicazione gratuita Send Files to TV sia sullo smartphone sia sulla TV (disponibile su Google Play Store).
2. Scarica il file `app-tv-release.apk` sul tuo smartphone seguendo la guida per smartphone descritta sopra.
3. Apri Send Files to TV su entrambi i dispositivi.
4. Sullo smartphone seleziona Invia e scegli il file `app-tv-release.apk` appena scaricato.
5. Sulla TV seleziona Ricevi.
6. Una volta completato il trasferimento, utilizza un'applicazione di gestione file (File Manager) sulla TV per aprire la cartella Downloads ed eseguire il file APK installandolo sulla TV.

---

## Architettura e Varianti di Compilazione (Per Sviluppatori)

Il codice sorgente e suddiviso per massimizzare la condivisione delle logiche di business isolando i componenti grafici specifici:
- `src/main`: Contiene i modelli dati, lo scraper, il database locale, i repository e i ViewModel condivisi.
- `src/tv`: Contiene la MainActivity Leanback, le schermate TV (HomeScreen, DetailScreen, PlayerScreen) ottimizzate per D-pad e la dipendenza da `androidx.tv.material3`.
- `src/mobile`: Contiene la MainActivity touch, la navigazione mobile, le relative schermate touch-optimized e il package `download` (cache Media3, servizio in foreground e ViewModel per i download offline).

### Prerequisiti per la compilazione
- JDK 17 o versioni successive
- Android SDK installato e configurato nel file `local.properties` (es. `sdk.dir=/home/utente/Android/Sdk`)

### Comandi per la compilazione da terminale

#### Compilazione Varianti di Debug (Suffix .debug)
- Genera l'APK TV di debug:
  ```bash
  ./gradlew assembleTvDebug
  ```
- Genera l'APK Mobile di debug:
  ```bash
  ./gradlew assembleMobileDebug
  ```

#### Compilazione Varianti di Release (Firma automatica integrata)
- Genera l'APK TV di release:
  ```bash
  ./gradlew assembleTvRelease
  ```
- Genera l'APK Mobile di release:
  ```bash
  ./gradlew assembleMobileRelease
  ```

Gli APK compilati saranno disponibili rispettivamente nelle cartelle:
- `app/build/outputs/apk/tv/`
- `app/build/outputs/apk/mobile/`
