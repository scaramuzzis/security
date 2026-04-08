#!/usr/bin/env python3
"""Attack Surface Analysis Module"""

class AttackSurfaceAnalyzer:
    """Analyze attack surface of scanned system"""
    
    def __init__(self):
        self.surface_weights = {
            'remote': 10,
            'local': 5,
            'adjacent': 7
        }
        
        self.port_risk = {
            21: ('FTP', 8),
            22: ('SSH', 5),
            23: ('Telnet', 10),
            25: ('SMTP', 6),
            80: ('HTTP', 4),
            443: ('HTTPS', 3),
            445: ('SMB', 10),
            3306: ('MySQL', 7),
            3389: ('RDP', 9),
            5432: ('PostgreSQL', 7),
            8080: ('HTTP-Alt', 5)
        }
    
    def analyze_surface(self, vulnerabilities):
        """Analyze attack surface from vulnerabilities"""
        
        # Robust handling of empty/None input
        if not vulnerabilities:
            return {
                'total_score': 0,
                'entry_points': [],
                'risk_level': 'LOW',
                'summary': 'No vulnerabilities detected'
            }
        
        # Ensure we have a list
        if not isinstance(vulnerabilities, list):
            vulnerabilities = [vulnerabilities]
        
        entry_points = []
        total_score = 0
        
        # Group by host and port
        by_host = {}
        for vuln in vulnerabilities:
            # Robust field extraction with defaults
            host = self._safe_get(vuln, 'host', 'unknown')
            port = self._safe_get(vuln, 'port', 0)
            
            # Convert port to int if it's a string
            if isinstance(port, str):
                try:
                    port = int(port)
                except (ValueError, TypeError):
                    port = 0
            
            key = f"{host}:{port}"
            if key not in by_host:
                by_host[key] = []
            by_host[key].append(vuln)
        
        # Analyze each entry point
        for key, vulns in by_host.items():
            try:
                host, port_str = key.split(':')
                port = int(port_str) if port_str.isdigit() else 0
            except (ValueError, AttributeError):
                host = 'unknown'
                port = 0
            
            # Calculate entry point score
            service_name = self._safe_get(vulns[0], 'service', 'unknown')
            base_risk = self.port_risk.get(port, (service_name, 5))[1]
            
            # Add vulnerability scores
            vuln_score = sum(self._vuln_score(v) for v in vulns)
            
            entry_score = base_risk + vuln_score
            total_score += entry_score
            
            entry_points.append({
                'host': host,
                'port': port,
                'service': service_name,
                'vulnerabilities': len(vulns),
                'score': entry_score,
                'risk': self._score_to_risk(entry_score)
            })
        
        # Sort by score
        entry_points.sort(key=lambda x: x['score'], reverse=True)
        
        return {
            'total_score': total_score,
            'entry_points': entry_points[:10],  # Top 10
            'risk_level': self._score_to_risk(total_score),
            'summary': f"{len(entry_points)} entry points, total risk: {total_score}"
        }
    
    def _safe_get(self, dictionary, key, default):
        """Safely get value from dictionary"""
        try:
            return dictionary.get(key, default)
        except (AttributeError, TypeError):
            return default
    
    def _vuln_score(self, vuln):
        """Calculate vulnerability contribution to score"""
        severity = self._safe_get(vuln, 'severity', 'LOW')
        
        # Handle different severity formats
        if isinstance(severity, str):
            severity = severity.upper()
        
        scores = {
            'CRITICAL': 10,
            'HIGH': 7,
            'MEDIUM': 4,
            'LOW': 2,
            'INFO': 1,
            'NONE': 0
        }
        
        base = scores.get(severity, 2)
        
        # Bonus if exploit available
        exploit_available = self._safe_get(vuln, 'exploit_available', False)
        if exploit_available:
            base *= 1.5
        
        return base
    
    def _score_to_risk(self, score):
        """Convert score to risk level"""
        if score >= 50:
            return 'CRITICAL'
        elif score >= 30:
            return 'HIGH'
        elif score >= 15:
            return 'MEDIUM'
        else:
            return 'LOW'
    
    def generate_report(self, analysis):
        """Generate human-readable report"""
        
        report = []
        report.append("=" * 60)
        report.append("ATTACK SURFACE ANALYSIS")
        report.append("=" * 60)
        report.append(f"\nTotal Risk Score: {analysis.get('total_score', 0)}")
        report.append(f"Risk Level: {analysis.get('risk_level', 'UNKNOWN')}")
        report.append(f"Summary: {analysis.get('summary', 'N/A')}\n")
        
        entry_points = analysis.get('entry_points', [])
        
        if entry_points:
            report.append("TOP ENTRY POINTS:")
            report.append("-" * 60)
            
            for i, ep in enumerate(entry_points, 1):
                report.append(f"\n{i}. {ep.get('host', 'N/A')}:{ep.get('port', 'N/A')} ({ep.get('service', 'N/A')})")
                report.append(f"   Vulnerabilities: {ep.get('vulnerabilities', 0)}")
                report.append(f"   Score: {ep.get('score', 0)} - {ep.get('risk', 'N/A')}")
        else:
            report.append("\nNo entry points identified.")
        
        return "\n".join(report)


if __name__ == '__main__':
    # Test
    analyzer = AttackSurfaceAnalyzer()
    
    test_vulns = [
        {'host': '192.168.1.1', 'port': 445, 'service': 'SMB', 
         'severity': 'CRITICAL', 'exploit_available': True},
        {'host': '192.168.1.1', 'port': 22, 'service': 'SSH', 
         'severity': 'MEDIUM', 'exploit_available': False}
    ]
    
    result = analyzer.analyze_surface(test_vulns)
    print(analyzer.generate_report(result))
    
    # Test with empty
    print("\n" + "="*60)
    print("Testing with empty list:")
    result2 = analyzer.analyze_surface([])
    print(analyzer.generate_report(result2))
