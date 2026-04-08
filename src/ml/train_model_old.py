import json
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
import joblib
import os

def load_dataset(filename="data/cve_dataset.json"):
    """
    Carica il dataset CVE
    
    Args:
        filename: Path del file JSON
    
    Returns:
        DataFrame pandas
    """
    with open(filename, 'r') as f:
        data = json.load(f)
    
    df = pd.DataFrame(data)
    print(f"✅ Dataset caricato: {len(df)} vulnerabilità")
    return df

def prepare_features(df):
    """
    Prepara le feature per il training
    
    Args:
        df: DataFrame con i dati grezzi
    
    Returns:
        X (features), y (target)
    """
    print("\n🔧 Preparazione features...")
    
    # Feature 1: CVSS Score (già numerico)
    df['cvss_score_feature'] = df['cvss_score']
    
    # Feature 2: Attack Vector (categorico → numerico)
    attack_vector_map = {
        'NETWORK': 3,      # Più pericoloso
        'ADJACENT': 2,
        'LOCAL': 1,
        'PHYSICAL': 0      # Meno pericoloso
    }
    df['attack_vector_numeric'] = df['attack_vector'].map(attack_vector_map)
    
    # Feature 3: Attack Complexity (categorico → numerico)
    complexity_map = {
        'LOW': 1,     # Più facile = più pericoloso
        'HIGH': 0     # Più difficile = meno pericoloso
    }
    df['complexity_numeric'] = df['attack_complexity'].map(complexity_map)
    
    # Feature 4: Privileges Required (categorico → numerico)
    privileges_map = {
        'NONE': 2,    # Nessun privilegio richiesto = molto pericoloso
        'LOW': 1,
        'HIGH': 0     # Serve admin = meno pericoloso
    }
    df['privileges_numeric'] = df['privileges_required'].map(privileges_map)
    
    # Feature 5: User Interaction (categorico → numerico)
    interaction_map = {
        'NONE': 1,       # Nessuna interazione = più pericoloso
        'REQUIRED': 0    # Serve click utente = meno pericoloso
    }
    df['interaction_numeric'] = df['user_interaction'].map(interaction_map)
    
    # Feature 6: Scope (categorico → numerico)
    scope_map = {
        'CHANGED': 1,     # Impatta oltre il componente vulnerabile
        'UNCHANGED': 0
    }
    df['scope_numeric'] = df['scope'].map(scope_map)
    
    # Feature 7-9: Impact metrics (categorico → numerico)
    impact_map = {
        'HIGH': 2,
        'LOW': 1,
        'NONE': 0
    }
    df['confidentiality_numeric'] = df['confidentiality_impact'].map(impact_map)
    df['integrity_numeric'] = df['integrity_impact'].map(impact_map)
    df['availability_numeric'] = df['availability_impact'].map(impact_map)
    
    # Seleziona feature e target
    feature_columns = [
        'cvss_score_feature',
        'attack_vector_numeric',
        'complexity_numeric',
        'privileges_numeric',
        'interaction_numeric',
        'scope_numeric',
        'confidentiality_numeric',
        'integrity_numeric',
        'availability_numeric'
    ]
    
    X = df[feature_columns]
    y = df['severity']
    
    print(f"   Features selezionate: {len(feature_columns)}")
    print(f"   Feature names: {feature_columns}")
    
    return X, y

def train_model(X, y):
    """
    Addestra il modello Random Forest
    
    Args:
        X: Features
        y: Target (severity)
    
    Returns:
        model, X_train, X_test, y_train, y_test
    """
    print("\n🌲 Training Random Forest...")
    
    # Split train/test (80/20)
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    print(f"   Training set: {len(X_train)} samples")
    print(f"   Test set: {len(X_test)} samples")
    
    # Crea e addestra il modello
    model = RandomForestClassifier(
        n_estimators=100,      # 100 alberi
        max_depth=10,          # Profondità massima
        random_state=42,
        n_jobs=-1              # Usa tutti i core CPU
    )
    
    print("   Addestramento in corso...")
    model.fit(X_train, y_train)
    print("   ✅ Modello addestrato!")
    
    return model, X_train, X_test, y_train, y_test

def evaluate_model(model, X_train, X_test, y_train, y_test):
    """
    Valuta le performance del modello
    
    Args:
        model: Modello addestrato
        X_train, X_test, y_train, y_test: Dati di training e test
    """
    print("\n📊 Valutazione modello...")
    
    # Accuracy su training set
    train_predictions = model.predict(X_train)
    train_accuracy = accuracy_score(y_train, train_predictions)
    print(f"\n   Training Accuracy: {train_accuracy:.2%}")
    
    # Accuracy su test set
    test_predictions = model.predict(X_test)
    test_accuracy = accuracy_score(y_test, test_predictions)
    print(f"   Test Accuracy: {test_accuracy:.2%}")
    
    # Check overfitting
    if train_accuracy - test_accuracy > 0.1:
        print("   ⚠️  Possibile overfitting!")
    else:
        print("   ✅ Nessun overfitting rilevato")
    
    # Classification Report dettagliato
    print("\n📋 Classification Report:")
    print(classification_report(y_test, test_predictions))
    
    # Confusion Matrix
    print("🔢 Confusion Matrix:")
    cm = confusion_matrix(y_test, test_predictions, 
                          labels=['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'])
    print(cm)
    
    # Feature Importance
    print("\n🔍 Feature Importance:")
    feature_names = [
        'CVSS Score',
        'Attack Vector',
        'Complexity',
        'Privileges',
        'User Interaction',
        'Scope',
        'Confidentiality',
        'Integrity',
        'Availability'
    ]
    
    importances = model.feature_importances_
    for name, importance in sorted(zip(feature_names, importances), 
                                   key=lambda x: x[1], reverse=True):
        print(f"   {name}: {importance:.3f}")

def save_model(model, filename="models/vulnerability_classifier.pkl"):
    """
    Salva il modello addestrato
    
    Args:
        model: Modello da salvare
        filename: Path dove salvare
    """
    # Crea directory se non esiste
    os.makedirs(os.path.dirname(filename), exist_ok=True)
    
    # Salva il modello
    joblib.dump(model, filename)
    print(f"\n💾 Modello salvato in: {filename}")
    print(f"   Dimensione file: {os.path.getsize(filename) / 1024:.2f} KB")

def main():
    """
    Pipeline completa di training
    """
    print("=" * 60)
    print("🤖 TRAINING MODELLO ML - VULNERABILITY CLASSIFIER")
    print("=" * 60)
    
    # 1. Carica dataset
    df = load_dataset()
    
    # 2. Prepara features
    X, y = prepare_features(df)
    
    # 3. Addestra modello
    model, X_train, X_test, y_train, y_test = train_model(X, y)
    
    # 4. Valuta performance
    evaluate_model(model, X_train, X_test, y_train, y_test)
    
    # 5. Salva modello
    save_model(model)
    
    print("\n" + "=" * 60)
    print("✅ TRAINING COMPLETATO CON SUCCESSO!")
    print("=" * 60)

if __name__ == "__main__":
    main()
