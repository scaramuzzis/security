import pytest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from src.ml.analyzer import VulnerabilityAnalyzer
from src.utils.risk_scorer import RiskScorer
from src.nvd.nvd_client import NVDClient


class TestIntegration:
    '''Integration test suite'''
    
    def test_analyzer_initialization(self):
        '''Test analyzer initializes'''
        analyzer = VulnerabilityAnalyzer()
        assert analyzer is not None
    
    def test_analyzer_analysis(self):
        '''Test analysis works'''
        analyzer = VulnerabilityAnalyzer()
        
        test_vuln = {
            'cvss_score': 7.5,
            'attack_vector': 'NETWORK',
            'attack_complexity': 'LOW',
            'privileges_required': 'NONE',
            'user_interaction': 'NONE',
            'confidentiality_impact': 'HIGH',
            'integrity_impact': 'NONE',
            'availability_impact': 'NONE'
        }
        
        result = analyzer.analyze(test_vuln)
        
        # L'analyzer restituisce solo ML predictions
        assert 'ml_available' in result
        assert 'predicted_severity' in result
        assert 'ml_confidence' in result
        assert 'original_severity' in result
    
        # Se il modello è disponibile, verifica la predizione
        if result['ml_available']:
            assert result['predicted_severity'] is not None
            assert 0.0 <= result['ml_confidence'] <= 1.0 
    
    def test_risk_scorer(self):
        '''Test risk scorer'''
        scorer = RiskScorer()
        
        test_vuln = {
            'cvss_score': 8.5,
            'attack_vector': 'NETWORK',
            'attack_complexity': 'LOW',
            'privileges_required': 'NONE',
            'user_interaction': 'NONE',
            'confidentiality_impact': 'HIGH',
            'integrity_impact': 'HIGH',
            'availability_impact': 'HIGH'
        }
        
        risk = scorer.calculate_risk_score(test_vuln)
        
        assert 0.0 <= risk <= 10.0
        
        priority = scorer.get_priority(risk)
        assert 1 <= priority <= 4
    
    def test_nvd_client_initialization(self):
        '''Test NVD client initializes'''
        try:
            client = NVDClient()
            assert client is not None
        except:
            pytest.skip("NVD client requires network")


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
