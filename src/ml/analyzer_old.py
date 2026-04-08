import pickle
import numpy as np
import os


class VulnerabilityAnalyzer:
    '''Analyzes vulnerabilities using ML'''
    
    def __init__(self, model_path='models/vulnerability_model.pkl'):
        '''Initialize analyzer'''
        self.model = None
        self.model_path = model_path
        self.is_initialized = False
        
        # Encoding mappings
        self.attack_vector_map = {
            'NETWORK': 0, 'ADJACENT': 1, 'ADJACENT_NETWORK': 1,
            'LOCAL': 2, 'PHYSICAL': 3
        }
        self.attack_complexity_map = {
            'LOW': 0, 'HIGH': 1
        }
        self.privileges_map = {
            'NONE': 0, 'LOW': 1, 'HIGH': 2
        }
        self.interaction_map = {
            'NONE': 0, 'REQUIRED': 1
        }
        self.impact_map = {
            'NONE': 0, 'LOW': 1, 'HIGH': 2
        }
        
        self.severity_classes = ['CRITICAL', 'HIGH', 'LOW', 'MEDIUM']
        
        self._initialize()
    
    def _initialize(self):
        '''Load ML model'''
        try:
            if not os.path.exists(self.model_path):
                print(f"⚠ Modello non trovato: {self.model_path}")
                return
            
            print(f"Caricamento modello ML...")
            with open(self.model_path, 'rb') as f:
                self.model = pickle.load(f)
            
            self.is_initialized = True
            print("✓ Modello ML caricato")
            
        except Exception as e:
            print(f"⚠ Errore caricamento modello: {e}")
            self.is_initialized = False
    
    def analyze(self, vulnerability_data):
        '''Analyze single vulnerability'''
        try:
            if not self.is_initialized or self.model is None:
                return self._fallback_analysis(vulnerability_data)
            
            features = self._prepare_features(vulnerability_data)
            prediction = self._predict(features)
            risk_score = self._calculate_risk_score(vulnerability_data, prediction)
            priority = self._assign_priority(risk_score)
            recommendation = self._generate_recommendation(
                prediction['severity'],
                risk_score,
                priority
            )
            
            return {
                'ml_available': True,
                'predicted_severity': prediction['severity'],
                'confidence': float(prediction['confidence']),
                'risk_score': float(risk_score),
                'priority': int(priority),
                'recommendation': recommendation,
                'probabilities': prediction['probabilities']
            }
            
        except Exception as e:
            print(f"⚠ Errore analisi ML: {e}")
            return self._fallback_analysis(vulnerability_data)
    
    def _prepare_features(self, vuln_data):
        '''Transform vulnerability data to ML features'''
        features = []
        
        # CVSS Score
        cvss = float(vuln_data.get('cvss_score', 5.0))
        features.append(cvss)
        
        # Attack Vector
        av = vuln_data.get('attack_vector', 'LOCAL').upper()
        features.append(self.attack_vector_map.get(av, 2))
        
        # Attack Complexity
        ac = vuln_data.get('attack_complexity', 'HIGH').upper()
        features.append(self.attack_complexity_map.get(ac, 1))
        
        # Privileges Required
        pr = vuln_data.get('privileges_required', 'LOW').upper()
        features.append(self.privileges_map.get(pr, 1))
        
        # User Interaction
        ui = vuln_data.get('user_interaction', 'NONE').upper()
        features.append(self.interaction_map.get(ui, 0))
        
        # Impacts
        ci = vuln_data.get('confidentiality_impact', 'NONE').upper()
        features.append(self.impact_map.get(ci, 0))
        
        ii = vuln_data.get('integrity_impact', 'NONE').upper()
        features.append(self.impact_map.get(ii, 0))
        
        ai = vuln_data.get('availability_impact', 'NONE').upper()
        features.append(self.impact_map.get(ai, 0))
        
        return np.array(features).reshape(1, -1)
    
    def _predict(self, features):
        '''Execute ML prediction'''
        prediction_class = self.model.predict(features)[0]
        probabilities = self.model.predict_proba(features)[0]
        
        severity = self.severity_classes[prediction_class]
        confidence = probabilities[prediction_class]
        
        prob_dict = {}
        for i, sev in enumerate(self.severity_classes):
            prob_dict[sev] = float(probabilities[i])
        
        return {
            'severity': severity,
            'confidence': confidence,
            'probabilities': prob_dict
        }
    
    def _calculate_risk_score(self, vuln_data, prediction):
        '''Calculate risk score combining CVSS and ML'''
        cvss = float(vuln_data.get('cvss_score', 5.0))
        ml_confidence = prediction['confidence']
        
        # Base from CVSS (70%)
        risk = cvss * 0.7
        
        # ML adjustment (30%)
        severity_weights = {
            'LOW': 0.0,
            'MEDIUM': 0.3,
            'HIGH': 0.6,
            'CRITICAL': 1.0
        }
        
        ml_adjustment = severity_weights.get(prediction['severity'], 0.5) * 3.0
        risk += ml_adjustment * 0.3
        
        # Boost if high confidence
        if ml_confidence > 0.8:
            risk *= 1.1
        
        return max(0.0, min(10.0, risk))
    
    def _assign_priority(self, risk_score):
        '''Assign priority 1-4'''
        if risk_score >= 9.0:
            return 1
        elif risk_score >= 7.0:
            return 2
        elif risk_score >= 4.0:
            return 3
        else:
            return 4
    
    def _generate_recommendation(self, severity, risk_score, priority):
        '''Generate recommendation text'''
        recommendations = {
            (1, 'CRITICAL'): "🔴 AZIONE IMMEDIATA! Patch entro 24 ore.",
            (1, 'HIGH'): "🔴 URGENTE: Patch entro 48 ore.",
            (2, 'HIGH'): "🟠 IMPORTANTE: Patch entro 1 settimana.",
            (2, 'MEDIUM'): "🟠 Patch entro 2 settimane.",
            (3, 'MEDIUM'): "🟡 Patch entro 1 mese.",
            (3, 'LOW'): "🟡 Patch quando possibile.",
            (4, 'LOW'): "🟢 Priorità bassa."
        }
        
        key = (priority, severity)
        return recommendations.get(key, "Valuta remediation.")
    
    def _fallback_analysis(self, vuln_data):
        '''Fallback when ML not available'''
        cvss = float(vuln_data.get('cvss_score', 5.0))
        
        if cvss >= 9.0:
            severity = 'CRITICAL'
        elif cvss >= 7.0:
            severity = 'HIGH'
        elif cvss >= 4.0:
            severity = 'MEDIUM'
        else:
            severity = 'LOW'
        
        risk_score = cvss
        priority = self._assign_priority(risk_score)
        recommendation = self._generate_recommendation(severity, risk_score, priority)
        
        return {
            'ml_available': False,
            'predicted_severity': severity,
            'confidence': 0.7,
            'risk_score': float(risk_score),
            'priority': int(priority),
            'recommendation': recommendation,
            'probabilities': None
        }


if __name__ == '__main__':
    # Test
    print("="*60)
    print("TEST ML ANALYZER")
    print("="*60)
    
    analyzer = VulnerabilityAnalyzer()
    
    test_vuln = {
        'cve_id': 'CVE-TEST',
        'cvss_score': 9.8,
        'attack_vector': 'NETWORK',
        'attack_complexity': 'LOW',
        'privileges_required': 'NONE',
        'user_interaction': 'NONE',
        'confidentiality_impact': 'HIGH',
        'integrity_impact': 'HIGH',
        'availability_impact': 'HIGH'
    }
    
    result = analyzer.analyze(test_vuln)
    
    print(f"\nSeverity: {result['predicted_severity']}")
    print(f"Confidence: {result['confidence']*100:.1f}%")
    print(f"Risk Score: {result['risk_score']:.2f}/10")
    print(f"Priority: {result['priority']}")
    print(f"Recommendation: {result['recommendation']}")
    
    print("\n" + "="*60)
