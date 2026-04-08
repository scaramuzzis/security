#!/usr/bin/env python3
"""
NVD Data Collector for ML Training
Raccoglie CVE reali da NVD per addestrare il modello ML
"""

import sys
import os
import json
import time
import argparse
from pathlib import Path
from datetime import datetime, timedelta

# Aggiungi src al path
sys.path.insert(0, str(Path(__file__).parent.parent))

from nvd.nvd_client import NVDClient


def collect_recent_cves(client, days=30, max_per_severity=50):
    """
    Raccoglie CVE recenti da NVD, bilanciate per severity
    
    Args:
        client: NVDClient instance
        days: Numero di giorni indietro da cercare
        max_per_severity: Max CVE per ogni livello di severity
        
    Returns:
        list: Lista di CVE bilanciate per severity
    """
    print(f"\n{'='*70}")
    print(f"RACCOLTA CVE DA NVD (ultimi {days} giorni)")
    print(f"{'='*70}\n")
    
    all_cves = []
    severity_counts = {'CRITICAL': 0, 'HIGH': 0, 'MEDIUM': 0, 'LOW': 0}
    
    # Cerca CVE recenti
    print(f"🔍 Cercando CVE recenti...")
    recent_cves = client.search_recent_cves(days=days, max_results=500)
    
    if not recent_cves:
        print("⚠️  Nessun CVE trovato. Prova ad aumentare i giorni.")
        return []
    
    print(f"✓ Trovati {len(recent_cves)} CVE")
    
    # Organizza per severity
    cves_by_severity = {
        'CRITICAL': [],
        'HIGH': [],
        'MEDIUM': [],
        'LOW': []
    }
    
    for cve in recent_cves:
        severity = cve.get('cvss_severity', 'UNKNOWN')
        if severity in cves_by_severity:
            cves_by_severity[severity].append(cve)
    
    # Stampa distribuzione trovata
    print(f"\n📊 Distribuzione CVE trovati:")
    for sev in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']:
        print(f"  {sev:10s}: {len(cves_by_severity[sev]):3d}")
    
    # Bilancia dataset
    print(f"\n⚖️  Bilanciamento dataset (max {max_per_severity} per severity)...")
    
    for severity in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']:
        available = cves_by_severity[severity]
        to_take = min(len(available), max_per_severity)
        
        if to_take > 0:
            selected = available[:to_take]
            all_cves.extend(selected)
            severity_counts[severity] = to_take
            print(f"  {severity:10s}: {to_take:3d} CVE selezionati")
        else:
            print(f"  {severity:10s}: {to_take:3d} CVE (⚠️ pochi disponibili)")
    
    return all_cves, severity_counts


def search_by_keywords(client, keywords, max_per_keyword=20):
    """
    Cerca CVE per keywords specifiche
    
    Args:
        client: NVDClient instance
        keywords: Lista di keywords
        max_per_keyword: Max risultati per keyword
        
    Returns:
        list: Lista di CVE trovati
    """
    print(f"\n🔍 Ricerca per keywords...")
    
    all_cves = []
    seen_ids = set()
    
    for keyword in keywords:
        print(f"  Cercando: {keyword}...")
        cves = client.search_by_keyword(keyword, max_results=max_per_keyword)
        
        # Rimuovi duplicati
        new_cves = []
        for cve in cves:
            if cve['cve_id'] not in seen_ids:
                new_cves.append(cve)
                seen_ids.add(cve['cve_id'])
        
        print(f"    Trovati: {len(new_cves)} CVE nuovi")
        all_cves.extend(new_cves)
        
        # Rate limiting
        time.sleep(1)
    
    return all_cves


def prepare_ml_dataset(cves):
    """
    Prepara dataset per ML training
    
    Args:
        cves: Lista di CVE da NVD
        
    Returns:
        list: Dataset formattato per training
    """
    dataset = []
    
    for cve in cves:
        # Verifica che abbia tutti i campi necessari
        required_fields = [
            'cve_id', 'cvss_score', 'cvss_severity',
            'attack_vector', 'attack_complexity',
            'privileges_required', 'user_interaction'
        ]
        
        if all(field in cve for field in required_fields):
            # Prepara record per ML
            record = {
                'cve_id': cve['cve_id'],
                'description': cve.get('description', ''),
                'cvss_score': cve['cvss_score'],
                'severity': cve['cvss_severity'],
                'attack_vector': cve['attack_vector'],
                'attack_complexity': cve['attack_complexity'],
                'privileges_required': cve['privileges_required'],
                'user_interaction': cve['user_interaction'],
                'scope': cve.get('scope', 'UNCHANGED'),
                'confidentiality_impact': cve.get('confidentiality_impact', 'NONE'),
                'integrity_impact': cve.get('integrity_impact', 'NONE'),
                'availability_impact': cve.get('availability_impact', 'NONE'),
                'published_date': cve.get('published_date', ''),
                'last_modified': cve.get('last_modified', '')
            }
            dataset.append(record)
    
    return dataset


def save_dataset(dataset, filename='data/nvd_cve_dataset.json'):
    """Salva dataset su file"""
    
    # Crea directory se non esiste
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    
    # Salva JSON
    with open(filename, 'w') as f:
        json.dump(dataset, f, indent=2)
    
    print(f"\n✓ Dataset salvato: {filename}")
    print(f"  Total CVE: {len(dataset)}")
    
    # Statistiche
    severity_counts = {}
    for item in dataset:
        sev = item['severity']
        severity_counts[sev] = severity_counts.get(sev, 0) + 1
    
    print(f"\n📊 Distribuzione finale:")
    for sev in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']:
        count = severity_counts.get(sev, 0)
        pct = (count / len(dataset) * 100) if dataset else 0
        print(f"  {sev:10s}: {count:3d} ({pct:5.1f}%)")


def main():
    parser = argparse.ArgumentParser(
        description='Raccoglie CVE reali da NVD per training ML'
    )
    parser.add_argument(
        '--samples',
        type=int,
        default=200,
        help='Numero totale di campioni da raccogliere (default: 200)'
    )
    parser.add_argument(
        '--days',
        type=int,
        default=90,
        help='Giorni di storia da cercare (default: 90)'
    )
    parser.add_argument(
        '--keywords',
        action='store_true',
        help='Aggiungi ricerca per keywords comuni'
    )
    parser.add_argument(
        '--output',
        type=str,
        default='data/nvd_cve_dataset.json',
        help='File di output (default: data/nvd_cve_dataset.json)'
    )
    
    args = parser.parse_args()
    
    print("="*70)
    print("NVD DATA COLLECTOR FOR ML TRAINING")
    print("="*70)
    
    # Inizializza client NVD
    print("\n🔌 Connessione a NVD...")
    client = NVDClient()
    
    if not client.api_key:
        print("\n⚠️  ATTENZIONE: API Key non trovata!")
        print("   Il processo sarà MOLTO più lento (rate limit: 5 req/30sec)")
        print("   Tempo stimato: ~20-30 minuti per 200 campioni")
        print("\n   Per velocizzare:")
        print("   1. Ottieni API key: https://nvd.nist.gov/developers/request-an-api-key")
        print("   2. Aggiungi a .env: NVD_API_KEY=tua-key")
        print("\n   Continuare? (s/n): ", end='')
        
        if input().lower() != 's':
            print("   Operazione annullata.")
            return
    
    # Calcola samples per severity
    max_per_severity = args.samples // 4
    
    # Raccolta CVE recenti
    all_cves, severity_counts = collect_recent_cves(
        client,
        days=args.days,
        max_per_severity=max_per_severity
    )
    
    # Se richiesto, aggiungi ricerca per keywords
    if args.keywords:
        print(f"\n{'='*70}")
        print("RICERCA KEYWORD-BASED")
        print(f"{'='*70}")
        
        keywords = [
            'apache', 'linux', 'windows', 'microsoft',
            'remote code execution', 'sql injection',
            'cross-site scripting', 'buffer overflow'
        ]
        
        keyword_cves = search_by_keywords(client, keywords, max_per_keyword=10)
        
        # Merge evitando duplicati
        existing_ids = {cve['cve_id'] for cve in all_cves}
        for cve in keyword_cves:
            if cve['cve_id'] not in existing_ids:
                all_cves.append(cve)
                existing_ids.add(cve['cve_id'])
        
        print(f"\n✓ Totale CVE con keywords: {len(all_cves)}")
    
    # Verifica che abbiamo abbastanza dati
    if len(all_cves) < 50:
        print("\n⚠️  ATTENZIONE: Pochi CVE raccolti!")
        print(f"   Raccolti: {len(all_cves)}")
        print(f"   Richiesti: {args.samples}")
        print("\n   Suggerimenti:")
        print("   - Aumenta --days (es. --days 180)")
        print("   - Aggiungi --keywords")
        print("   - Ottieni API key NVD per rate limit migliori")
    
    # Prepara dataset per ML
    print(f"\n{'='*70}")
    print("PREPARAZIONE DATASET ML")
    print(f"{'='*70}")
    
    dataset = prepare_ml_dataset(all_cves)
    
    if not dataset:
        print("\n❌ Errore: Nessun CVE valido nel dataset")
        return
    
    # Salva dataset
    save_dataset(dataset, args.output)
    
    # Istruzioni finali
    print(f"\n{'='*70}")
    print("✅ RACCOLTA COMPLETATA")
    print(f"{'='*70}")
    
    print("\n🎯 Prossimi passi:")
    print(f"  1. Addestra modello:")
    print(f"     python3 src/ml/train_model.py --dataset {args.output}")
    print(f"\n  2. Testa modello:")
    print(f"     python3 src/ml/predict.py")
    print(f"\n  3. Usa nel scanner:")
    print(f"     python3 examples/ml_enhanced_scan.py scan.xml")
    
    # Tempo stimato
    total_time = len(all_cves) * (6 if not client.api_key else 0.6)
    print(f"\n⏱️  Tempo impiegato: ~{total_time/60:.1f} minuti")


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  Operazione interrotta dall'utente")
        sys.exit(1)
    except Exception as e:
        print(f"\n❌ Errore: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)
