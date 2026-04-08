#!/usr/bin/env python3
"""Threat Modeling Module - STRIDE Framework"""

class ThreatModeler:
    """Implement STRIDE threat modeling"""
    
    def __init__(self):
        self.stride_categories = {
            'Spoofing': 'Identity spoofing attacks',
            'Tampering': 'Data modification attacks',
            'Repudiation': 'Denial of actions',
            'Information Disclosure': 'Information leakage',
            'Denial of Service': 'Service availability attacks',
            'Elevation of Privilege': 'Unauthorized access escalation'
        }
    
    def generate_threat_report(self, vulnerabilities):
        """Generate STRIDE threat analysis"""
        
        if not vulnerabilities:
            return {
                'threats': [],
                'summary': 'No threats identified',
                'by_category': {}
            }
        
        # Ensure we have a list
        if not isinstance(vulnerabilities, list):
            vulnerabilities = [vulnerabilities]
        
        threats = []
        
        for vuln in vulnerabilities:
            # Skip if not a dictionary
            if not isinstance(vuln, dict):
                continue
            
            # Map vulnerability to STRIDE categories
            stride_threats = self._map_to_stride(vuln)
            
            for threat_type, threat_data in stride_threats.items():
                threats.append({
                    'type': threat_type,
                    'vulnerability': self._safe_get(vuln, 'name', 'Unknown'),
                    'host': self._safe_get(vuln, 'host', 'unknown'),
                    'port': self._safe_get(vuln, 'port', 0),
                    'severity': self._safe_get(vuln, 'severity', 'UNKNOWN'),
                    'description': threat_data.get('description', ''),
                    'dread_score': self._calculate_dread(vuln, threat_type),
                    'mitigation': threat_data.get('mitigation', '')
                })
        
        # Sort by DREAD score
        threats.sort(key=lambda x: x['dread_score'], reverse=True)
        
        return {
            'threats': threats,
            'summary': f"{len(threats)} threats identified across STRIDE categories",
            'by_category': self._group_by_category(threats)
        }
    
    def _safe_get(self, obj, key, default):
        """Safely get value from object"""
        try:
            if isinstance(obj, dict):
                return obj.get(key, default)
            elif hasattr(obj, key):
                return getattr(obj, key, default)
            else:
                return default
        except (AttributeError, TypeError, KeyError):
            return default
    
    def _map_to_stride(self, vuln):
        """Map vulnerability to STRIDE threat categories"""
        
        threats = {}
        severity = self._safe_get(vuln, 'severity', 'LOW')
        service = str(self._safe_get(vuln, 'service', '')).lower()
        vuln_name = str(self._safe_get(vuln, 'name', '')).lower()
        
        # Spoofing
        if any(x in vuln_name for x in ['auth', 'login', 'credential', 'password']):
            threats['Spoofing'] = {
                'description': 'Weak authentication mechanism allows identity spoofing',
                'mitigation': 'Implement strong authentication (MFA, certificates)'
            }
        
        # Tampering
        if any(x in vuln_name for x in ['injection', 'xss', 'csrf', 'upload']):
            threats['Tampering'] = {
                'description': 'Input validation weakness allows data tampering',
                'mitigation': 'Implement input validation and sanitization'
            }
        
        # Information Disclosure
        if any(x in vuln_name for x in ['disclosure', 'leak', 'exposure', 'directory', 'sensitive']):
            threats['Information Disclosure'] = {
                'description': 'Sensitive information exposed to unauthorized users',
                'mitigation': 'Implement access controls and encrypt sensitive data'
            }
        
        # Denial of Service
        if any(x in vuln_name for x in ['dos', 'flood', 'overflow', 'resource']):
            threats['Denial of Service'] = {
                'description': 'Service can be disrupted or made unavailable',
                'mitigation': 'Implement rate limiting and resource quotas'
            }
        
        # Elevation of Privilege
        if any(x in vuln_name for x in ['privilege', 'escalation', 'root', 'admin', 'elevation']):
            threats['Elevation of Privilege'] = {
                'description': 'Attacker can gain elevated privileges',
                'mitigation': 'Apply principle of least privilege, patch vulnerabilities'
            }
        
        # Default if no specific match
        if not threats:
            threats['Information Disclosure'] = {
                'description': 'Vulnerability may lead to information disclosure',
                'mitigation': 'Apply security patches and follow hardening guidelines'
            }
        
        return threats
    
    def _calculate_dread(self, vuln, threat_type):
        """Calculate DREAD score (0-50)"""
        
        severity = str(self._safe_get(vuln, 'severity', 'LOW')).upper()
        exploit_available = self._safe_get(vuln, 'exploit_available', False)
        
        # Base scores by severity
        damage_map = {'CRITICAL': 10, 'HIGH': 7, 'MEDIUM': 5, 'LOW': 3, 'INFO': 1}
        damage = damage_map.get(severity, 3)
        
        reproducibility = 8 if exploit_available else 5
        exploitability = 9 if exploit_available else 5
        
        affected_map = {'CRITICAL': 10, 'HIGH': 7, 'MEDIUM': 5, 'LOW': 3, 'INFO': 1}
        affected_users = affected_map.get(severity, 3)
        
        discoverability = 7  # Assume moderate discoverability
        
        total = damage + reproducibility + exploitability + affected_users + discoverability
        
        return total
    
    def _group_by_category(self, threats):
        """Group threats by STRIDE category"""
        
        by_category = {}
        
        for threat in threats:
            category = threat.get('type', 'Unknown')
            if category not in by_category:
                by_category[category] = []
            by_category[category].append(threat)
        
        return by_category
    
    def generate_report(self, threat_analysis):
        """Generate human-readable threat report"""
        
        report = []
        report.append("=" * 60)
        report.append("THREAT MODELING REPORT (STRIDE)")
        report.append("=" * 60)
        report.append(f"\n{threat_analysis.get('summary', 'No summary available')}\n")
        
        by_category = threat_analysis.get('by_category', {})
        
        if by_category:
            # By category
            for category, threats in by_category.items():
                report.append(f"\n{category.upper()}")
                report.append("-" * 60)
                
                for threat in threats[:3]:  # Top 3 per category
                    report.append(f"\n• {threat.get('vulnerability', 'Unknown')}")
                    report.append(f"  Host: {threat.get('host', 'N/A')}:{threat.get('port', 'N/A')}")
                    report.append(f"  DREAD Score: {threat.get('dread_score', 0)}/50")
                    report.append(f"  Mitigation: {threat.get('mitigation', 'N/A')}")
        else:
            report.append("\nNo threats identified.")
        
        return "\n".join(report)


if __name__ == '__main__':
    # Test
    modeler = ThreatModeler()
    
    test_vulns = [
        {
            'name': 'Weak Authentication',
            'host': '192.168.1.1',
            'port': 22,
            'service': 'SSH',
            'severity': 'HIGH',
            'exploit_available': True
        },
        {
            'name': 'SQL Injection vulnerability',
            'host': '192.168.1.1',
            'port': 80,
            'service': 'HTTP',
            'severity': 'CRITICAL',
            'exploit_available': True
        }
    ]
    
    result = modeler.generate_threat_report(test_vulns)
    print(modeler.generate_report(result))
    print(f"\nTotal threats: {len(result['threats'])}")
    print(f"Categories: {list(result['by_category'].keys())}")
