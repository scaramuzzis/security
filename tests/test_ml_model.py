import unittest
import sys
import os
import numpy as np

# Aggiungi src al path
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from src.ml.predict import load_model, prepare_input, predict_severity

class TestMLModel(unittest.TestCase):
    """Test suite per il modello ML"""
    
    @classmethod
    def setUpClass(cls):
        """Setup eseguito una volta prima di tutti i test"""
        cls.model = load_model("models/vulnerability_classifier.pkl")
    
    def test_model_loaded(self):
        """Test 1: Verifica che il modello sia caricato correttamente"""
        self.assertIsNotNone(self.model)
        self.assertTrue(hasattr(self.model, 'predict'))
        self.assertTrue(hasattr(self.model, 'predict_proba'))
    
    def test_prepare_input_shape(self):
        """Test 2: Verifica che prepare_input restituisca il formato corretto"""
        vuln_data = {
            "cvss_score": 9.8,
            "attack_vector": "NETWORK",
            "attack_complexity": "LOW",
            "privileges_required": "NONE",
            "user_interaction": "NONE",
            "scope": "UNCHANGED",
            "confidentiality_impact": "HIGH",
            "integrity_impact": "HIGH",
            "availability_impact": "HIGH"
        }
        
        X = prepare_input(vuln_data)
        
        # Deve essere un array numpy
        self.assertIsInstance(X, np.ndarray)
        
        # Deve avere shape (1, 9) - 1 sample, 9 features
        self.assertEqual(X.shape, (1, 9))
    
    def test_predict_critical_vulnerability(self):
        """Test 3: Predizione su vulnerabilità CRITICAL"""
        critical_vuln = {
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
        
        prediction, probability = predict_severity(self.model, critical_vuln)
        
        # Deve predire CRITICAL
        self.assertEqual(prediction, "CRITICAL")
        
        # Confidenza deve essere > 50%
        self.assertGreater(probability, 0.5)
    
    def test_predict_low_vulnerability(self):
        """Test 4: Predizione su vulnerabilità LOW"""
        low_vuln = {
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
        
        prediction, probability = predict_severity(self.model, low_vuln)
        
        # Deve predire LOW
        self.assertEqual(prediction, "LOW")
        
        # Confidenza deve essere > 50%
        self.assertGreater(probability, 0.5)
    
    def test_model_classes(self):
        """Test 5: Verifica che il modello conosca tutte le classi di gravità"""
        expected_classes = ['CRITICAL', 'HIGH', 'LOW', 'MEDIUM']
        model_classes = sorted(self.model.classes_)
        
        self.assertEqual(model_classes, expected_classes)
    
    def test_prediction_probability_sum(self):
        """Test 6: Le probabilità devono sommare a 1"""
        vuln_data = {
            "cvss_score": 7.5,
            "attack_vector": "NETWORK",
            "attack_complexity": "LOW",
            "privileges_required": "NONE",
            "user_interaction": "NONE",
            "scope": "UNCHANGED",
            "confidentiality_impact": "HIGH",
            "integrity_impact": "NONE",
            "availability_impact": "NONE"
        }
        
        X = prepare_input(vuln_data)
        probabilities = self.model.predict_proba(X)[0]
        
        # La somma delle probabilità deve essere ~1.0
        self.assertAlmostEqual(sum(probabilities), 1.0, places=5)

def run_tests():
    """Esegue tutti i test"""
    # Crea test suite
    loader = unittest.TestLoader()
    suite = loader.loadTestsFromTestCase(TestMLModel)
    
    # Esegui test con output verboso
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    
    # Ritorna 0 se successo, 1 se fallimento
    return 0 if result.wasSuccessful() else 1

if __name__ == '__main__':
    exit_code = run_tests()
    exit(exit_code)
