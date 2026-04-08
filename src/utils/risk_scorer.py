#!/usr/bin/env python3
"""
Risk Scoring System
"""

class RiskScorer:
    '''Calculate advanced risk score'''
    
    def __init__(self):
        '''Initialize scoring weights'''
        self.attack_vector_weights = {
            'NETWORK': 1.0, 'ADJACENT': 0.8, 'ADJACENT_NETWORK': 0.8,
            'LOCAL': 0.5, 'PHYSICAL': 0.2
        }
        
        self.attack_complexity_weights = {
            'LOW': 1.0, 'HIGH': 0.7
        }
        
        self.privileges_weights = {
            'NONE': 1.0, 'LOW': 0.8, 'HIGH': 0.5
        }
        
        self.user_interaction_weights = {
            'NONE': 1.0, 'REQUIRED': 0.7
        }
        
        self.impact_weights = {
            'HIGH': 1.0, 'LOW': 0.5, 'NONE': 0.0
        }
        
        self.context_weights = {
            'public_facing': 1.2,
            'internal': 1.0,
            'isolated': 0.8
        }
    
    def calculate_risk_score(self, vuln_data, ml_prediction=None, context='internal'):
        '''Calculate comprehensive risk score'''
        base_score = float(vuln_data.get('cvss_score', 5.0))
        
        if ml_prediction and ml_prediction.get('ml_available'):
            ml_confidence = ml_prediction.get('confidence', 0.7)
            base_score *= (0.85 + (ml_confidence * 0.3))
        
        exploit_score = self._calculate_exploitability(vuln_data)
        impact_score = self._calculate_impact(vuln_data)
        context_mult = self.context_weights.get(context, 1.0)
        
        # 🔧 FIX: Aggiungi bonus exploit
        exploit_bonus = self._calculate_exploit_bonus(vuln_data)
        
        risk = base_score * exploit_score * impact_score * context_mult * exploit_bonus
        
        return max(0.0, min(10.0, risk))
    
    def calculate_risk(self, vuln_data):
        """
        🆕 NUOVO: Alias per compatibilità con xml_parser
        Questo viene chiamato da xml_parser.py
        """
        return self.calculate_risk_score(vuln_data)
    
    def _calculate_exploitability(self, vuln_data):
        '''Calculate exploitability score'''
        av = vuln_data.get('attack_vector', 'LOCAL').upper()
        av_weight = self.attack_vector_weights.get(av, 0.5)
        
        ac = vuln_data.get('attack_complexity', 'HIGH').upper()
        ac_weight = self.attack_complexity_weights.get(ac, 0.7)
        
        pr = vuln_data.get('privileges_required', 'LOW').upper()
        pr_weight = self.privileges_weights.get(pr, 0.8)
        
        ui = vuln_data.get('user_interaction', 'NONE').upper()
        ui_weight = self.user_interaction_weights.get(ui, 1.0)
        
        return (av_weight + ac_weight + pr_weight + ui_weight) / 4.0
    
    def _calculate_impact(self, vuln_data):
        '''Calculate impact score'''
        conf = vuln_data.get('confidentiality_impact', 'NONE').upper()
        conf_weight = self.impact_weights.get(conf, 0.0)
        
        integ = vuln_data.get('integrity_impact', 'NONE').upper()
        integ_weight = self.impact_weights.get(integ, 0.0)
        
        avail = vuln_data.get('availability_impact', 'NONE').upper()
        avail_weight = self.impact_weights.get(avail, 0.0)
        
        impact_score = (conf_weight + integ_weight + avail_weight) / 3.0
        
        if impact_score == 0.0:
            impact_score = 0.3
        
        return impact_score
    
    def _calculate_exploit_bonus(self, vuln_data):
        """
        🆕 NUOVO: Calcola bonus se exploit disponibile
        """
        exploit_available = vuln_data.get('exploit_available', False)
        description = str(vuln_data.get('description', '')).upper()
        
        # Check for exploit indicators
        if exploit_available or '*EXPLOIT*' in description:
            return 1.3  # 30% bonus for available exploits
        
        return 1.0
    
    def get_priority(self, risk_score, vuln_data=None):
        '''
        Get priority 1-4
        
        🔧 MIGLIORATO: Considera anche severity ed exploits
        '''
        
        # Se abbiamo i dati della vulnerabilità, usiamoli
        if vuln_data:
            severity = vuln_data.get('severity', 'LOW').upper()
            exploit_available = vuln_data.get('exploit_available', False)
            description = str(vuln_data.get('description', '')).upper()
            has_exploit = exploit_available or '*EXPLOIT*' in description
            
            # PRIORITY 1 - URGENTE
            # CRITICAL con exploit pubblico = massima priorità
            if severity == 'CRITICAL' and has_exploit:
                return 1
            
            # Risk score molto alto
            if risk_score >= 9.0:
                return 1
            
            # PRIORITY 2 - ALTO
            # CRITICAL senza exploit
            if severity == 'CRITICAL':
                return 2
            
            # HIGH con exploit
            if severity == 'HIGH' and has_exploit:
                return 2
            
            # Risk score alto
            if risk_score >= 7.0:
                return 2
            
            # PRIORITY 3 - MEDIO
            # HIGH senza exploit
            if severity == 'HIGH':
                return 3
            
            # MEDIUM con alto risk score
            if severity == 'MEDIUM' and risk_score >= 5.0:
                return 3
            
            # Risk score medio
            if risk_score >= 4.0:
                return 3
        
        else:
            # Fallback: solo basato su risk_score
            if risk_score >= 9.0:
                return 1
            elif risk_score >= 7.0:
                return 2
            elif risk_score >= 4.0:
                return 3
        
        # PRIORITY 4 - BASSO
        return 4
    
    def get_priority_label(self, priority):
        '''Get priority label'''
        labels = {1: 'URGENTE', 2: 'ALTO', 3: 'MEDIO', 4: 'BASSO'}
        return labels.get(priority, 'MEDIO')
    
    def get_timeframe(self, priority):
        '''Get recommended timeframe'''
        timeframes = {
            1: '24 ore',
            2: '1 settimana',
            3: '1 mese',
            4: 'Ciclo manutenzione'
        }
        return timeframes.get(priority, '1 mese')


if __name__ == '__main__':
    # Test con diversi scenari
    scorer = RiskScorer()
    
    print("="*70)
    print("RISK SCORER - TEST SCENARIOS")
    print("="*70)
    
    scenarios = [
        {
            'name': 'CRITICAL + EXPLOIT + NETWORK',
            'data': {
                'cvss_score': 9.8,
                'severity': 'CRITICAL',
                'attack_vector': 'NETWORK',
                'attack_complexity': 'LOW',
                'privileges_required': 'NONE',
                'user_interaction': 'NONE',
                'confidentiality_impact': 'HIGH',
                'integrity_impact': 'HIGH',
                'availability_impact': 'HIGH',
                'exploit_available': True,
                'description': 'Stack overflow *EXPLOIT*'
            }
        },
        {
            'name': 'HIGH + NO EXPLOIT + NETWORK',
            'data': {
                'cvss_score': 7.5,
                'severity': 'HIGH',
                'attack_vector': 'NETWORK',
                'attack_complexity': 'LOW',
                'privileges_required': 'NONE',
                'user_interaction': 'NONE',
                'confidentiality_impact': 'HIGH',
                'integrity_impact': 'HIGH',
                'availability_impact': 'LOW',
                'exploit_available': False
            }
        },
        {
            'name': 'MEDIUM + LOCAL',
            'data': {
                'cvss_score': 5.0,
                'severity': 'MEDIUM',
                'attack_vector': 'LOCAL',
                'attack_complexity': 'LOW',
                'privileges_required': 'LOW',
                'user_interaction': 'NONE',
                'confidentiality_impact': 'HIGH',
                'integrity_impact': 'LOW',
                'availability_impact': 'NONE',
                'exploit_available': False
            }
        },
        {
            'name': 'LOW + LOCAL + HIGH COMPLEXITY',
            'data': {
                'cvss_score': 2.5,
                'severity': 'LOW',
                'attack_vector': 'LOCAL',
                'attack_complexity': 'HIGH',
                'privileges_required': 'HIGH',
                'user_interaction': 'REQUIRED',
                'confidentiality_impact': 'LOW',
                'integrity_impact': 'NONE',
                'availability_impact': 'NONE',
                'exploit_available': False
            }
        }
    ]
    
    for scenario in scenarios:
        print(f"\n{scenario['name']}")
        print("-" * 70)
        
        data = scenario['data']
        
        # Calculate risk
        risk = scorer.calculate_risk_score(data)
        
        # Get priority (con dati vulnerabilità)
        priority = scorer.get_priority(risk, data)
        priority_label = scorer.get_priority_label(priority)
        timeframe = scorer.get_timeframe(priority)
        
        # Display results
        print(f"  Severity: {data['severity']:8s} | CVSS: {data['cvss_score']:.1f}")
        print(f"  Attack Vector: {data['attack_vector']:8s} | Complexity: {data['attack_complexity']}")
        print(f"  Exploit Available: {data['exploit_available']}")
        print(f"  → Risk Score: {risk:.2f}/10.0")
        print(f"  → Priority: {priority} ({priority_label})")
        print(f"  → Timeframe: {timeframe}")
    
    print("\n" + "="*70)
    print("✅ Test completato!")
    print("\nVerifica che le priorità siano variate (1, 2, 3, 4)")
