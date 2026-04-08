import joblib
import numpy as np

def load_model(model_path="models/vulnerability_classifier.pkl"):
    """
    Carica il modello addestrato
    
    Args:
        model_path: Path del modello salvato
    
    Returns:
        Modello caricato
    """
    model = joblib.load(model_path)
    print(f"✅ Modello caricato da {model_path}")
    return model

def prepare_input(vulnerability_data):
    """
    Prepara i dati di input per la predizione
    
    Args:
        vulnerability_data: Dict con i dati della vulnerabilità
    
    Returns:
        Array numpy con le features
    """
    # Mapping per conversione categorico → numerico
    attack_vector_map = {'NETWORK': 3, 'ADJACENT': 2, 'LOCAL': 1, 'PHYSICAL': 0}
    complexity_map = {'LOW': 1, 'HIGH': 0}
    privileges_map = {'NONE': 2, 'LOW': 1, 'HIGH': 0}
    interaction_map = {'NONE': 1, 'REQUIRED': 0}
    scope_map = {'CHANGED': 1, 'UNCHANGED': 0}
    impact_map = {'HIGH': 2, 'LOW': 1, 'NONE': 0}
    
    # Estrai e converti le features
    features = [
        vulnerability_data.get('cvss_score', 0),
        attack_vector_map.get(vulnerability_data.get('attack_vector', 'LOCAL'), 1),
        complexity_map.get(vulnerability_data.get('attack_complexity', 'HIGH'), 0),
        privileges_map.get(vulnerability_data.get('privileges_required', 'HIGH'), 0),
        interaction_map.get(vulnerability_data.get('user_interaction', 'REQUIRED'), 0),
        scope_map.get(vulnerability_data.get('scope', 'UNCHANGED'), 0),
        impact_map.get(vulnerability_data.get('confidentiality_impact', 'NONE'), 0),
        impact_map.get(vulnerability_data.get('integrity_impact', 'NONE'), 0),
        impact_map.get(vulnerability_data.get('availability_impact', 'NONE'), 0)
    ]
    
    return np.array(features).reshape(1, -1)

def predict_severity(model, vulnerability_data):
    """
    Predice la gravità di una vulnerabilità
    
    Args:
        model: Modello addestrato
        vulnerability_data: Dict con i dati della vulnerabilità
    
    Returns:
        Predizione (severity) e probabilità
    """
    # Prepara input
    X = prepare_input(vulnerability_data)
    
    # Fai predizione
    prediction = model.predict(X)[0]
    probabilities = model.predict_proba(X)[0]
    
    # Trova la probabilità della classe predetta
    class_names = model.classes_
    predicted_probability = probabilities[np.where(class_names == prediction)[0][0]]
    
    return prediction, predicted_probability

def main():
    """
    Test del modello con esempi
    """
    print("=" * 60)
    print("🔮 TEST PREDIZIONI MODELLO ML")
    print("=" * 60)
    
    # Carica modello
    model = load_model()
    
    # Test Case 1: Vulnerabilità CRITICAL
    print("\n📌 TEST 1: Remote Code Execution (dovrebbe essere CRITICAL)")
    vuln1 = {
        "cvss_score": 10.0,
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "NONE",
        "user_interaction": "NONE",
        "scope": "CHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "HIGH",
        "availability_impact": "HIGH"
    }
    
    prediction, probability = predict_severity(model, vuln1)
    print(f"   Predizione: {prediction}")
    print(f"   Confidenza: {probability:.2%}")
    
    # Test Case 2: Vulnerabilità HIGH
    print("\n📌 TEST 2: XSS con privilegi bassi (dovrebbe essere HIGH)")
    vuln2 = {
        "cvss_score": 8.8,
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "LOW",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "HIGH",
        "availability_impact": "HIGH"
    }
    
    prediction, probability = predict_severity(model, vuln2)
    print(f"   Predizione: {prediction}")
    print(f"   Confidenza: {probability:.2%}")
    
    # Test Case 3: Vulnerabilità MEDIUM
    print("\n📌 TEST 3: Information Disclosure (dovrebbe essere MEDIUM)")
    vuln3 = {
        "cvss_score": 6.5,
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "LOW",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "NONE",
        "availability_impact": "NONE"
    }
    
    prediction, probability = predict_severity(model, vuln3)
    print(f"   Predizione: {prediction}")
    print(f"   Confidenza: {probability:.2%}")
    
    # Test Case 4: Vulnerabilità LOW
    print("\n📌 TEST 4: Missing Headers (dovrebbe essere LOW)")
    vuln4 = {
        "cvss_score": 3.7,
        "attack_vector": "NETWORK",
        "attack_complexity": "HIGH",
        "privileges_required": "NONE",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "LOW",
        "integrity_impact": "NONE",
        "availability_impact": "NONE"
    }
    
    prediction, probability = predict_severity(model, vuln4)
    print(f"   Predizione: {prediction}")
    print(f"   Confidenza: {probability:.2%}")
    
    print("\n" + "=" * 60)
    print("✅ TEST COMPLETATI!")
    print("=" * 60)

if __name__ == "__main__":
    main()
