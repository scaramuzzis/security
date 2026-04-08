#!/usr/bin/env python3
"""Security Recommendations Generator"""

class SecurityRecommendations:
    """Generate actionable security recommendations"""
    
    def __init__(self):
        self.templates = {
            'CRITICAL': {
                'urgency': 'IMMEDIATE ACTION REQUIRED',
                'timeline': 'Within 24 hours',
                'priority': 1
            },
            'HIGH': {
                'urgency': 'High Priority',
                'timeline': 'Within 1 week',
                'priority': 2
            },
            'MEDIUM': {
                'urgency': 'Medium Priority',
                'timeline': 'Within 1 month',
                'priority': 3
            },
            'LOW': {
                'urgency': 'Low Priority',
                'timeline': 'Next maintenance window',
                'priority': 4
            }
        }
    
    def generate_recommendations(self, analysis_results):
        """Generate comprehensive recommendations"""
        
        # Safely extract vulnerabilities
        vulnerabilities = self._safe_get_list(analysis_results, 'vulnerabilities')
        
        if not vulnerabilities:
            return {
                'recommendations': [],
                'summary': 'No immediate recommendations',
                'action_items': [],
                'general_recommendations': []
            }
        
        recommendations = []
        action_items = []
        
        # Sort by severity and exploit availability
        sorted_vulns = sorted(
            vulnerabilities,
            key=lambda x: (
                self._get_priority(x),
                not self._safe_get(x, 'exploit_available', False)
            )
        )
        
        # Generate recommendations for each vulnerability
        for vuln in sorted_vulns[:20]:  # Top 20
            rec = self._generate_vuln_recommendation(vuln)
            recommendations.append(rec)
            
            # Add to action items if high priority
            if rec.get('priority', 4) <= 2:
                action_items.append({
                    'action': rec.get('immediate_action', 'Review and patch'),
                    'vulnerability': self._safe_get(vuln, 'name', 'Unknown'),
                    'deadline': rec.get('timeline', 'ASAP')
                })
        
        # General recommendations
        general = self._generate_general_recommendations(vulnerabilities)
        
        return {
            'recommendations': recommendations,
            'action_items': action_items,
            'general_recommendations': general,
            'summary': f"{len(recommendations)} specific recommendations, {len(action_items)} immediate actions"
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
    
    def _safe_get_list(self, obj, key):
        """Safely get list from object"""
        result = self._safe_get(obj, key, [])
        if not isinstance(result, list):
            return []
        return result
    
    def _get_priority(self, vuln):
        """Get priority number for sorting"""
        severity = str(self._safe_get(vuln, 'severity', 'LOW')).upper()
        template = self.templates.get(severity, self.templates['LOW'])
        return template.get('priority', 4)
    
    def _generate_vuln_recommendation(self, vuln):
        """Generate recommendation for specific vulnerability"""
        
        severity = str(self._safe_get(vuln, 'severity', 'LOW')).upper()
        template = self.templates.get(severity, self.templates['LOW'])
        
        rec = {
            'vulnerability': self._safe_get(vuln, 'name', 'Unknown'),
            'host': self._safe_get(vuln, 'host', 'unknown'),
            'port': self._safe_get(vuln, 'port', 0),
            'severity': severity,
            'urgency': template.get('urgency', 'Review'),
            'timeline': template.get('timeline', 'When possible'),
            'priority': template.get('priority', 4)
        }
        
        # Specific actions based on vulnerability type
        vuln_name = str(self._safe_get(vuln, 'name', '')).lower()
        
        if 'outdated' in vuln_name or 'version' in vuln_name or 'eol' in vuln_name:
            rec['immediate_action'] = 'Update to latest stable version'
            rec['long_term'] = 'Implement automated update process'
        
        elif any(x in vuln_name for x in ['ssl', 'tls', 'certificate', 'crypto']):
            rec['immediate_action'] = 'Update SSL/TLS configuration to secure protocols'
            rec['long_term'] = 'Implement certificate management and monitoring system'
        
        elif any(x in vuln_name for x in ['auth', 'password', 'credential', 'login']):
            rec['immediate_action'] = 'Enforce strong password policy and enable MFA'
            rec['long_term'] = 'Implement centralized authentication system (SSO/LDAP)'
        
        elif 'injection' in vuln_name or 'xss' in vuln_name or 'csrf' in vuln_name:
            rec['immediate_action'] = 'Implement input validation and sanitization'
            rec['long_term'] = 'Code review and security training for developers'
        
        elif any(x in vuln_name for x in ['smb', 'netbios', 'cifs']):
            rec['immediate_action'] = 'Disable SMBv1, update to SMBv3'
            rec['long_term'] = 'Network segmentation and access control'
        
        elif 'ssh' in vuln_name:
            rec['immediate_action'] = 'Update SSH configuration, disable weak ciphers'
            rec['long_term'] = 'Implement key-based authentication only'
        
        else:
            rec['immediate_action'] = 'Apply latest security patches'
            rec['long_term'] = 'Regular vulnerability scanning and patch management'
        
        # Add exploit-specific recommendation
        if self._safe_get(vuln, 'exploit_available', False):
            rec['urgent_note'] = '⚠️ PUBLIC EXPLOIT AVAILABLE - IMMEDIATE ACTION REQUIRED'
            rec['priority'] = min(rec['priority'], 1)  # Escalate to highest
        
        return rec
    
    def _generate_general_recommendations(self, vulnerabilities):
        """Generate general security recommendations"""
        
        general = []
        
        # Count by severity
        severity_counts = {}
        for vuln in vulnerabilities:
            sev = str(self._safe_get(vuln, 'severity', 'LOW')).upper()
            severity_counts[sev] = severity_counts.get(sev, 0) + 1
        
        # Network security
        general.append({
            'category': 'Network Security',
            'recommendations': [
                'Implement network segmentation to isolate critical systems',
                'Deploy firewall rules with default deny policy',
                'Enable intrusion detection/prevention system (IDS/IPS)',
                'Regular security audits and penetration testing',
                'Disable unnecessary services and ports'
            ]
        })
        
        # Patch management
        if any(s in severity_counts for s in ['CRITICAL', 'HIGH']):
            general.append({
                'category': 'Patch Management',
                'recommendations': [
                    'Establish automated patch management system',
                    'Create patch testing environment before production',
                    'Schedule regular maintenance windows for updates',
                    'Monitor vendor security advisories and CVE databases',
                    'Document patch deployment procedures'
                ]
            })
        
        # Access control
        general.append({
            'category': 'Access Control & Authentication',
            'recommendations': [
                'Implement principle of least privilege across all systems',
                'Enable multi-factor authentication (MFA) for all accounts',
                'Regular access reviews and privilege audits',
                'Strong password policy enforcement (length, complexity, rotation)',
                'Disable default accounts and change default credentials'
            ]
        })
        
        # Monitoring
        general.append({
            'category': 'Monitoring & Logging',
            'recommendations': [
                'Enable comprehensive logging on all systems',
                'Implement Security Information and Event Management (SIEM)',
                'Set up real-time alerting for critical security events',
                'Regular log reviews and anomaly detection',
                'Ensure log retention meets compliance requirements'
            ]
        })
        
        # Incident response
        if severity_counts.get('CRITICAL', 0) > 0:
            general.append({
                'category': 'Incident Response',
                'recommendations': [
                    'Develop and maintain incident response plan',
                    'Conduct regular incident response drills and tabletop exercises',
                    'Establish clear communication protocols and escalation paths',
                    'Create forensics and evidence preservation capabilities',
                    'Designate incident response team with defined roles'
                ]
            })
        
        # Encryption
        general.append({
            'category': 'Data Protection & Encryption',
            'recommendations': [
                'Encrypt sensitive data at rest and in transit',
                'Use industry-standard encryption protocols (TLS 1.2+)',
                'Implement proper key management procedures',
                'Regular encryption audit and certificate monitoring',
                'Data classification and handling policies'
            ]
        })
        
        return general
    
    def generate_report(self, recommendations_data):
        """Generate human-readable recommendations report"""
        
        report = []
        report.append("=" * 60)
        report.append("SECURITY RECOMMENDATIONS REPORT")
        report.append("=" * 60)
        report.append(f"\n{recommendations_data.get('summary', 'No summary')}\n")
        
        action_items = recommendations_data.get('action_items', [])
        
        # Immediate action items
        if action_items:
            report.append("\nIMMEDIATE ACTIONS REQUIRED:")
            report.append("=" * 60)
            
            for i, item in enumerate(action_items[:10], 1):
                report.append(f"\n{i}. {item.get('vulnerability', 'Unknown')}")
                report.append(f"   Action: {item.get('action', 'Review')}")
                report.append(f"   Deadline: {item.get('deadline', 'ASAP')}")
        
        # Top recommendations
        recommendations = recommendations_data.get('recommendations', [])
        if recommendations:
            report.append("\n\nTOP RECOMMENDATIONS:")
            report.append("=" * 60)
            
            for i, rec in enumerate(recommendations[:10], 1):
                report.append(f"\n{i}. {rec.get('vulnerability', 'Unknown')}")
                report.append(f"   Host: {rec.get('host', 'N/A')}:{rec.get('port', 'N/A')}")
                report.append(f"   Severity: {rec.get('severity', 'N/A')} - {rec.get('urgency', 'N/A')}")
                report.append(f"   Timeline: {rec.get('timeline', 'N/A')}")
                report.append(f"   Action: {rec.get('immediate_action', 'N/A')}")
                
                if 'urgent_note' in rec:
                    report.append(f"   {rec['urgent_note']}")
        
        # General recommendations
        general = recommendations_data.get('general_recommendations', [])
        if general:
            report.append("\n\nGENERAL SECURITY RECOMMENDATIONS:")
            report.append("=" * 60)
            
            for gen_rec in general:
                report.append(f"\n{gen_rec.get('category', 'General')}:")
                for rec in gen_rec.get('recommendations', []):
                    report.append(f"  • {rec}")
        
        return "\n".join(report)


if __name__ == '__main__':
    # Test
    rec_gen = SecurityRecommendations()
    
    test_results = {
        'vulnerabilities': [
            {
                'name': 'Outdated OpenSSL',
                'host': '192.168.1.1',
                'port': 443,
                'severity': 'CRITICAL',
                'exploit_available': True
            },
            {
                'name': 'Weak SSH Configuration',
                'host': '192.168.1.1',
                'port': 22,
                'severity': 'HIGH',
                'exploit_available': False
            }
        ]
    }
    
    result = rec_gen.generate_recommendations(test_results)
    print(rec_gen.generate_report(result))
    print(f"\nTotal recommendations: {len(result['recommendations'])}")
    print(f"Immediate actions: {len(result['action_items'])}")
