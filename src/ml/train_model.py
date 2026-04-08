#!/usr/bin/env python3
"""
ML Model Training with NVD Data
Addestra modello ML usando dati reali da NVD
"""

import json
import pickle
import argparse
import numpy as np
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, accuracy_score, confusion_matrix
import os


def load_dataset(filepath='data/nvd_cve_dataset.json'):
    """
    Carica dataset da file JSON
    
    Args:
        filepath: Path al file dataset
        
    Returns:
        list: Lista di vulnerabilità
    """
    print(f"📂 Caricamento dataset: {filepath}")
    
    if not os.path.exists(filepath):
        print(f"❌ File non trovato: {filepath}")
        print("\n💡 Genera dataset con:")
        print(f"   python3 src/ml/nvd_data_collector.py")
        return None
    
    with open(filepath, 'r') as f:
        data = json.load(f)
    
    print(f"✓ Caricati {len(data)} campioni")
    
    # Statistiche dataset
    severity_counts = {}
    for item in data:
        sev = item.get('severity', 'UNKNOWN')
        severity_counts[sev] = severity_counts.get(sev, 0) + 1
    
    print(f"\n📊 Distribuzione severity:")
    for sev in ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']:
        count = severity_counts.get(sev, 0)
        pct = (count / len(data) * 100) if data else 0
        print(f"  {sev:10s}: {count:3d} ({pct:5.1f}%)")
    
    return data


def prepare_features(vulnerabilities):
    """
    Prepara features e labels per training
    
    Args:
        vulnerabilities: Lista di dict con dati CVE
        
    Returns:
        tuple: (X, y) features e labels
    """
    print("\n🔧 Preparazione features...")
    
    # Mapping per encoding categorico
    attack_vector_map = {
        'NETWORK': 2, 'ADJACENT_NETWORK': 1, 'ADJACENT': 1,
        'LOCAL': 0, 'PHYSICAL': 0
    }
    attack_complexity_map = {'LOW': 1, 'HIGH': 0}
    privileges_map = {'NONE': 2, 'LOW': 1, 'HIGH': 0}
    user_interaction_map = {'NONE': 1, 'REQUIRED': 0}
    scope_map = {'CHANGED': 1, 'UNCHANGED': 0}
    impact_map = {'HIGH': 2, 'LOW': 1, 'NONE': 0}
    
    X = []
    y = []
    skipped = 0
    
    for vuln in vulnerabilities:
        try:
            # Verifica campi richiesti
            if not all(key in vuln for key in ['cvss_score', 'severity']):
                skipped += 1
                continue
            
            # Estrai features (9 features totali)
            features = [
                float(vuln.get('cvss_score', 0.0)),
                attack_vector_map.get(vuln.get('attack_vector', 'LOCAL'), 0),
                attack_complexity_map.get(vuln.get('attack_complexity', 'HIGH'), 0),
                privileges_map.get(vuln.get('privileges_required', 'HIGH'), 0),
                user_interaction_map.get(vuln.get('user_interaction', 'REQUIRED'), 0),
                scope_map.get(vuln.get('scope', 'UNCHANGED'), 0),
                impact_map.get(vuln.get('confidentiality_impact', 'NONE'), 0),
                impact_map.get(vuln.get('integrity_impact', 'NONE'), 0),
                impact_map.get(vuln.get('availability_impact', 'NONE'), 0)
            ]
            
            X.append(features)
            y.append(vuln['severity'])
            
        except Exception as e:
            skipped += 1
            continue
    
    if skipped > 0:
        print(f"⚠️  Saltati {skipped} campioni con dati mancanti")
    
    print(f"✓ Preparati {len(X)} campioni")
    print(f"  Features: 9")
    print(f"  Classes: {len(set(y))}")
    
    return np.array(X), np.array(y)


def train_model(X, y, test_size=0.2, random_state=42):
    """
    Addestra modello Random Forest
    
    Args:
        X: Features
        y: Labels
        test_size: Percentuale test set
        random_state: Random seed
        
    Returns:
        tuple: (model, X_test, y_test, y_pred)
    """
    print(f"\n🤖 Training Random Forest Classifier...")
    
    # Split train/test
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=test_size, random_state=random_state, stratify=y
    )
    
    print(f"  Train set: {len(X_train)} samples")
    print(f"  Test set:  {len(X_test)} samples")
    
    # Addestra modello
    model = RandomForestClassifier(
        n_estimators=100,
        max_depth=10,
        random_state=random_state,
        n_jobs=-1
    )
    
    print(f"\n  Training in corso...")
    model.fit(X_train, y_train)
    
    print(f"✓ Training completato")
    
    # Predizioni
    y_pred = model.predict(X_test)
    
    return model, X_test, y_test, y_pred


def evaluate_model(model, X_test, y_test, y_pred):
    """
    Valuta performance del modello
    
    Args:
        model: Modello addestrato
        X_test: Test features
        y_test: Test labels
        y_pred: Predizioni
    """
    print(f"\n{'='*70}")
    print("📊 EVALUATION RESULTS")
    print(f"{'='*70}")
    
    # Accuracy
    accuracy = accuracy_score(y_test, y_pred)
    print(f"\n🎯 Accuracy: {accuracy:.2%}")
    
    # Classification report
    print(f"\n📈 Classification Report:")
    print(classification_report(y_test, y_pred, zero_division=0))
    
    # Confusion matrix
    print(f"🔢 Confusion Matrix:")
    cm = confusion_matrix(y_test, y_pred, labels=model.classes_)
    
    # Header
    print(f"\n{'':12s}", end='')
    for label in model.classes_:
        print(f"{label:>10s}", end='')
    print()
    
    # Righe
    for i, label in enumerate(model.classes_):
        print(f"{label:12s}", end='')
        for j in range(len(model.classes_)):
            print(f"{cm[i][j]:>10d}", end='')
        print()
    
    # Feature importance
    print(f"\n🔍 Feature Importance:")
    feature_names = [
        'CVSS Score',
        'Attack Vector',
        'Attack Complexity',
        'Privileges Required',
        'User Interaction',
        'Scope',
        'Confidentiality Impact',
        'Integrity Impact',
        'Availability Impact'
    ]
    
    importances = model.feature_importances_
    indices = np.argsort(importances)[::-1]
    
    for i in range(len(feature_names)):
        idx = indices[i]
        print(f"  {i+1}. {feature_names[idx]:25s}: {importances[idx]:.4f}")
    
    return accuracy


def save_model(model, filepath='models/vulnerability_classifier.pkl'):
    """
    Salva modello su disco
    
    Args:
        model: Modello addestrato
        filepath: Path dove salvare
    """
    # Crea directory se non esiste
    os.makedirs(os.path.dirname(filepath), exist_ok=True)
    
    # Salva modello
    with open(filepath, 'wb') as f:
        pickle.dump(model, f)
    
    print(f"\n💾 Modello salvato: {filepath}")
    
    # Info file
    size = os.path.getsize(filepath)
    print(f"  Dimensione: {size / 1024:.1f} KB")


def main():
    parser = argparse.ArgumentParser(
        description='Addestra modello ML con dati NVD'
    )
    parser.add_argument(
        '--dataset',
        type=str,
        default='data/nvd_cve_dataset.json',
        help='Path al dataset (default: data/nvd_cve_dataset.json)'
    )
    parser.add_argument(
        '--output',
        type=str,
        default='models/vulnerability_classifier.pkl',
        help='Path output modello (default: models/vulnerability_classifier.pkl)'
    )
    parser.add_argument(
        '--test-size',
        type=float,
        default=0.2,
        help='Percentuale test set (default: 0.2)'
    )
    parser.add_argument(
        '--min-accuracy',
        type=float,
        default=0.70,
        help='Accuracy minima richiesta (default: 0.70)'
    )
    
    args = parser.parse_args()
    
    print("="*70)
    print("ML MODEL TRAINING WITH NVD DATA")
    print("="*70)
    print()
    
    # 1. Carica dataset
    data = load_dataset(args.dataset)
    if not data:
        return
    
    if len(data) < 50:
        print(f"\n⚠️  ATTENZIONE: Dataset piccolo ({len(data)} campioni)")
        print("   Raccomandazione: almeno 100 campioni per buona accuracy")
        print("\n   Genera più dati con:")
        print("   python3 src/ml/nvd_data_collector.py --samples 200 --days 180")
    
    # 2. Prepara features
    X, y = prepare_features(data)
    
    if len(X) == 0:
        print("\n❌ Errore: Nessun campione valido")
        return
    
    # 3. Train modello
    model, X_test, y_test, y_pred = train_model(
        X, y,
        test_size=args.test_size
    )
    
    # 4. Valuta performance
    accuracy = evaluate_model(model, X_test, y_test, y_pred)
    
    # 5. Verifica accuracy minima
    if accuracy < args.min_accuracy:
        print(f"\n⚠️  WARNING: Accuracy ({accuracy:.2%}) < soglia ({args.min_accuracy:.2%})")
        print("   Il modello potrebbe non essere affidabile.")
        print("\n   Suggerimenti:")
        print("   - Raccogli più dati (--samples 300)")
        print("   - Aumenta range temporale (--days 180)")
        print("   - Aggiungi ricerca keywords (--keywords)")
        print("\n   Salvare comunque? (s/n): ", end='')
        
        if input().lower() != 's':
            print("   Modello non salvato.")
            return
    
    # 6. Salva modello
    save_model(model, args.output)
    
    # 7. Istruzioni finali
    print(f"\n{'='*70}")
    print("✅ TRAINING COMPLETATO")
    print(f"{'='*70}")
    
    print(f"\n🎯 Performance:")
    print(f"  Accuracy: {accuracy:.2%}")
    print(f"  Samples:  {len(X)}")
    print(f"  Features: 9")
    print(f"  Classes:  {len(set(y))}")
    
    print(f"\n🚀 Prossimi passi:")
    print(f"  1. Testa il modello:")
    print(f"     python3 src/ml/predict.py")
    print(f"\n  2. Usa nel scanner:")
    print(f"     python3 examples/ml_enhanced_scan.py scan.xml")
    print(f"\n  3. Con NVD enrichment:")
    print(f"     python3 examples/ml_enhanced_scan.py scan.xml --nvd")


if __name__ == '__main__':
    try:
        main()
    except KeyboardInterrupt:
        print("\n\n⚠️  Training interrotto dall'utente")
    except Exception as e:
        print(f"\n❌ Errore: {e}")
        import traceback
        traceback.print_exc()
