# AI Security Scanner

![CI/CD](https://github.com/davidedellisanti90/ai-security-scanner-cyber-sentinel-group/workflows/CI%2FCD%20Pipeline/badge.svg)
![Python](https://img.shields.io/badge/python-3.8%2B-blue)
![License](https://img.shields.io/badge/license-MIT-green)

![Coverage](https://img.shields.io/codecov/c/github/davidedellisanti90/ai-security-scanner-cyber-sentinel-group)
![Issues](https://img.shields.io/github/issues/davidedellisanti90/ai-security-scanner-cyber-sentinel-group)
![Stars](https://img.shields.io/github/stars/davidedellisanti90/ai-security-scanner-cyber-sentinel-group)

AI-powered security scanner using Nmap...

--------------------------------------------------------------
Team Cyber Sentinel

Il progetto AI Security Scanner è sviluppato da un gruppo di appassionati di cybersecurity e intelligenza artificiale che credono in un futuro in cui la sicurezza sia automatizzata, trasparente e accessibile a tutti.

Membri del team:

- Ivan Robert D’Arcangelo

- Davide Delli Santi

- Salvatore Scaramuzzi

- Rosita Lavarra

- Nicola Marella

- Lorenzo Misino

- Sonia Rendina

- Vinicius Tadeu Anselmo Leite

-----------------------------------------------------------------

# 🛡️ AI Security Scanner

**AI Security Scanner** è uno strumento open-source per l’analisi automatizzata delle vulnerabilità in ambienti **DevSecOps**, **pipeline CI/CD** e infrastrutture applicative.  
Integra analisi basata su machine learning, normalizzazione dei punteggi di rischio, arricchimento NVD (CVSS) e reportistica interattiva.
```
✅ Ideale per penetration tester, analisti SOC, DevOps e ingegneri della sicurezza  
✅ Analizza e interpreta output XML di Nmap  
✅ Applica punteggi di rischio ML-driven normalizzati  
✅ Produce dashboard HTML interattive e grafici di rischio  
```
---

## 🔍 Funzionalità chiave
```
- Analisi avanzata delle vulnerabilità da file Nmap XML
- Arricchimento opzionale tramite API NVD (CVSS v3/v3.1)
- Normalizzazione del **risk_score** per coerenza dei punteggi
- Calcolo automatico delle **priorità di triage**
- Visualizzazioni grafiche:
  - Distribuzione della severità
  - Distribuzione delle priorità
  - Istogramma dei punteggi di rischio
  - Top vulnerabilità (deduplicate per CVE)
- Dashboard HTML responsive e stampabile
- Esportazione JSON completa per integrazione con altri sistemi
```
---

## 🧠 Come funziona
```
L’intera pipeline di elaborazione segue questi passaggi:

1. Estrazione delle vulnerabilità dal report Nmap XML  
2. (Opzionale) Recupero dei dati CVSS reali via API NVD  
3. Il modello ML genera segnali di rischio (risk signals)  
4. Normalizzazione dei punteggi in base a:
   - Punteggio ML
   - CVSS baseScore
   - Mappatura della severità
   - Mappatura della priorità
5. Generazione della **dashboard HTML interattiva**
6. Creazione di grafici e metadati JSON per audit o integrazione
```
## Struttura del progetto

```
ai-security-scanner/
├── examples/
│ └── generate_report.py (entry point del reporting)
├── reports/ (output generati)
├── src/
│ ├── parser/
│ │ └── xml_parser.py (ingestione Nmap XML)
│ ├── security/
│ │ ├── attack_surface.py
│ │ ├── threat_model.py
│ │ └── recommendations.py
│ └── visualization/
│ ├── plotter.py (grafici Matplotlib)
│ └── dashboard.py (rendering HTML)
├── requirements.txt
├── README.md
├── LICENSE
└── ...
```

## 📦 Installazione su Ubuntu
```
Il progetto utilizza Python 3.x e strumenti di sicurezza come Nmap.
Assicurati di avere entrambi installati con i comandi 

python3 --version nmap --version

nel caso installarli con 

sudo apt update
sudo apt install nmap python3 python3-pip -y
```
### Clona il progetto
```
git clone https://github.com/davidedellisanti90/ai-security-scanner-cyber-sentinel-group
cd ai-security-scanner
```
### installa ambiente virtuale 
```
python3 -m venv venv
```
### attiva ambiente virtuale
```
source venv/bin/activate
```
### installa le dipendenze
```
pip install -r requirements.txt
```
# 🧠 Come funziona
```
Lo script scanner.py avvia la scansione della rete.

I risultati vengono interpretati dal modulo parser/.

I dati elaborati vengono forniti in formato leggibile o pronti per essere analizzati da un modello AI.
```
### Esempio d’uso:
```

python3 ai-security-scanner-cyber-sentinel-group/examples/complete_scan.py 

Enter target (IP or hostname): < inserisci target >

```
---

### Genera un report completo partendo da un file XML Nmap:
```
python examples/generate_report.py scan_full.xml --nvd


Apri la dashboard HTML generata:

xdg-open reports/dashboard_*.html

```
## 📤 Esempio di output (CLI)
```
[STEP 1/5] Parsing e Analisi ML...
✓ 51 vulnerabilità trovate
✓ Arricchimento CVSS completato (NVD)

[STEP 2/5] Security Analysis...

Attack Surface Score: 293 (CRITICO)

Entry Points: 4

[STEP 3/5] Visualizzazioni...
✓ severity_dist.png
✓ priority_dist.png
✓ risk_dist.png
✓ top_vulns.png

[STEP 4/5] Dashboard generata

[STEP 5/5] Report JSON salvato: scan_full_complete_report.json

```
---

## ⚙️ Configurazione
```
Abilitazione/disabilitazione delle analisi:

nvd:
enable: true

analysis:
ml: true
risk_normalization: true

```
---

## 🧮 Normalizzazione del punteggio di rischio
```
La pipeline prende il **massimo** tra i punteggi disponibili per ogni vulnerabilità:

risk_normalized = max(
ml_risk_score,
cvss_score,
severity_mapping,
priority_mapping
)


| Punteggio di rischio | Priorità  | Azione consigliata           |
|----------------------|-----------|------------------------------|
| ≥ 9.0                | P1        | Mitigazione immediata        |
| ≥ 7.0                | P2        | Alta priorità                |
| ≥ 4.0                | P3        | Correzione pianificata       |
| < 4.0                | P4        | Monitoraggio periodico       |

```
---

## 📊 Grafici generati
```
- Distribuzione delle severità
- Distribuzione delle priorità
- Istogramma dei punteggi di rischio
- Top vulnerabilità (deduplicate per CVE)

Output generato:

reports/plots/severity_dist.png
reports/plots/priority_dist.png
reports/plots/risk_dist.png
reports/plots/top_vulns.png

```
---

## 🖥️ Dashboard
```
La dashboard interattiva fornisce:

- KPI principali (vulnerabilità, punteggi medi, criticità)
- Grafici di distribuzione
- Tabelle con breakdown per priorità/severità
- Top 10 vulnerabilità a maggior rischio
- Raccomandazioni di mitigazione

Visualizzazione:

xdg-open reports/dashboard_*.html
```

📘 Documentazione

La documentazione completa e la bozza dell’architettura del progetto sono disponibili nella cartella /docs.
Qui vengono descritti:

Documenti

- 🔍 **Automated Network Scanning** - Nmap wrapper with Python
- 📊 **XML Parsing** - Extract structured data from scan results
- 🔄 **JSON Conversion** - AI-ready data format
- 📈 **Summary Generation** - Key metrics and statistics
- 🧪 **Comprehensive Testing** - 80%+ code coverage with pytest
- 🚀 **CI/CD Pipeline** - Automated testing with GitHub Actions
- 📚 **Professional Documentation** - Complete usage guides

Il flusso logico interno del sistema.

L’utente inserisce l’indirizzo IP da analizzare.

Il modulo scanner/nmap_wrapper.py lancia la scansione con Nmap.

I risultati XML vengono generati in scan_results/.

Il modulo parser/xml_parser.py legge il file XML.

Il modulo parser/json_converter.py converte i dati in JSON.

Il sistema mostra i risultati in output o li salva. 

Le integrazioni AI previste.

Integrazione di modelli AI per l’analisi dei risultati.


# Esempio di scansione su target scanme.nmpap.org
```
python3 ai-security-scanner-cyber-sentinel-group/examples/complete_scan.py 

Enter target (IP or hostname): scanme.nmpap.org
```

![Immagine 2025-11-05 075120](https://github.com/user-attachments/assets/e92e3e50-7974-4c22-8414-754f4c4d688b)

```
python examples/generate_report.py scan_results/scanme_nmap_org_scan.xml -- nvd
```

![Immagine 2025-11-05 075358](https://github.com/user-attachments/assets/40f94028-9f58-4236-8e5f-7360ac590425)
![Immagine 2025-11-05 075826](https://github.com/user-attachments/assets/8c30203b-6009-418a-a7c4-fa6a16e3b912)

```
1. Open dashboard: xdg-open reports/dashboard_20251105_075313.html
```
![Immagine 2025-11-05 080229](https://github.com/user-attachments/assets/016c95fd-3b94-478f-865b-91511893de09)

```
dashboard
```
![Immagine 2025-11-05 080257](https://github.com/user-attachments/assets/d44d5c94-ad69-4801-bcf4-b7fb593b748f)


# 📱 PhoneGuard — App Android di monitoraggio del cellulare

Nella cartella [`android/PhoneGuard`](android/PhoneGuard/) è disponibile un'app Android
che controlla il telefono per rilevare comportamenti sospetti:

- quali app **inviano dati** verso l'esterno (traffico per-app Wi-Fi + mobile),
  con notifiche in caso di upload anomali;
- come viene **consumata la batteria** (livello, temperatura, salute, potenza),
  con avvisi per surriscaldamento o scarica anomala.

Vedi il [README dedicato](android/PhoneGuard/README.md) per compilazione, permessi e dettagli.

# 🤝 Contribuire

Le pull request sono benvenute!
Per idee, suggerimenti o collaborazioni, apri una issue o contatta il team.

# 🧾 Licenza

Distribuito sotto licenza MIT — libero di esplorare, modificare e migliorare.
