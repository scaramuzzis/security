'''
ML Vulnerability Analyzer
Analizza vulnerabilità usando modello ML addestrato
'''

import os
import pickle
import numpy as np
from pathlib import Path


class VulnerabilityAnalyzer:
    '''Analizzatore ML per vulnerabilità'''
    
    def __init__(self, model_path=None):
        '''
        Inizializza analyzer
        
        Args:
            model_path: Path al modello (default: models/vulnerability_classifier.pkl)
        '''
        if model_path is None:
            # Cerca modello nella directory corretta
            base_dir = Path(__file__).parent.parent.parent
            model_path = base_dir / 'models' / 'vulnerability_classifier.pkl'
        
        self.model_path = model_path
        self.model = None
        self.load_model()
    
    def load_model(self):
        '''Carica modello ML'''
        try:
            if os.path.exists(self.model_path):
                with open(self.model_path, 'rb') as f:
                    self.model = pickle.load(f)
                print(f"✓ ML Analyzer inizializzato")
            else:
                print(f"⚠ Modello non trovato: {self.model_path}")
                print(f"  Genera il modello con: python3 src/ml/train_model.py")
                self.model = None
        except Exception as e:
            print(f"⚠ Errore caricamento modello: {e}")
            self.model = None
    
    def prepare_features(self, vuln_data):
        '''
        Prepara features per predizione
        
        Args:
            vuln_data: Dizionario con dati vulnerabilità
            
        Returns:
            numpy array con features
        '''
        # Mapping per valori categorici
        attack_vector_map = {'NETWORK': 2, 'ADJACENT': 1, 'LOCAL': 0, 'PHYSICAL': 0}
        attack_complexity_map = {'LOW': 1, 'HIGH': 0}
        privileges_map = {'NONE': 2, 'LOW': 1, 'HIGH': 0}
        user_interaction_map = {'NONE': 1, 'REQUIRED': 0}
        scope_map = {'CHANGED': 1, 'UNCHANGED': 0}
        impact_map = {'HIGH': 2, 'LOW': 1, 'NONE': 0}
        
        # Estrai features
        features = [
            float(vuln_data.get('cvss_score', 0.0)),
            attack_vector_map.get(vuln_data.get('attack_vector', 'LOCAL'), 0),
            attack_complexity_map.get(vuln_data.get('attack_complexity', 'HIGH'), 0),
            privileges_map.get(vuln_data.get('privileges_required', 'HIGH'), 0),
            user_interaction_map.get(vuln_data.get('user_interaction', 'REQUIRED'), 0),
            scope_map.get(vuln_data.get('scope', 'UNCHANGED'), 0),
            impact_map.get(vuln_data.get('confidentiality_impact', 'NONE'), 0),
            impact_map.get(vuln_data.get('integrity_impact', 'NONE'), 0),
            impact_map.get(vuln_data.get('availability_impact', 'NONE'), 0)
        ]
        
        return np.array(features).reshape(1, -1)
    
    def analyze(self, vuln_data):
        '''
        Analizza vulnerabilità con ML
        
        Args:
            vuln_data: Dizionario con dati vulnerabilità
            
        Returns:
            dict: Risultato analisi con predizione e confidenza
        '''
        result = {
            'ml_available': self.model is not None,
            'predicted_severity': None,
            'ml_confidence': 0.0,
            'original_severity': vuln_data.get('severity', 'UNKNOWN')
        }
        
        if self.model is None:
            return result
        
        try:
            # Prepara features
            X = self.prepare_features(vuln_data)
            
            # Predici
            prediction = self.model.predict(X)[0]
            probabilities = self.model.predict_proba(X)[0]
            
            # Trova confidenza per la classe predetta
            class_idx = list(self.model.classes_).index(prediction)
            confidence = probabilities[class_idx]
            
            result['predicted_severity'] = prediction
            result['ml_confidence'] = float(confidence)
            result['severity_agreement'] = (prediction == result['original_severity'])
            
        except Exception as e:
            print(f"⚠ Errore analisi ML: {e}")
        
        return result


# Test standalone
if __name__ == '__main__':
    print("="*60)
    print("ML VULNERABILITY ANALYZER TEST")
    print("="*60)
    
    analyzer = VulnerabilityAnalyzer()
    
    if analyzer.model is None:
        print("\n❌ Modello non disponibile")
        print("\nPer generare il modello:")
        print("  1. python3 src/ml/cve_collector.py")
        print("  2. python3 src/ml/train_model.py")
        print("\nOppure usa il file di test:")
        print("  python3 check_and_create_test.py")
    else:
        print("\n✓ Modello caricato con successo")
        
        # Test case
        test_vuln = {
            'cve_id': 'CVE-TEST-0001',
            'cvss_score': 9.8,
            'severity': 'CRITICAL',
            'attack_vector': 'NETWORK',
            'attack_complexity': 'LOW',
            'privileges_required': 'NONE',
            'user_interaction': 'NONE',
            'scope': 'UNCHANGED',
            'confidentiality_impact': 'HIGH',
            'integrity_impact': 'HIGH',
            'availability_impact': 'HIGH'
        }
        
        print("\n📋 Test vulnerabilità:")
        print(f"  CVE: {test_vuln['cve_id']}")
        print(f"  CVSS: {test_vuln['cvss_score']}")
        print(f"  Severity originale: {test_vuln['severity']}")
        
        result = analyzer.analyze(test_vuln)
        
        print(f"\n🔮 Risultato analisi ML:")
        print(f"  Severity predetta: {result['predicted_severity']}")
        print(f"  Confidenza: {result['ml_confidence']:.1%}")
        print(f"  Accordo: {'✓' if result['severity_agreement'] else '✗'}")
        
        print("\n" + "="*60)
        print("✅ TEST COMPLETATO")
        print("="*60)
