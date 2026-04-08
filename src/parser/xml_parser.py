'''
XML Parser for Nmap Output - ENHANCED VERSION
Parses Nmap XML output with ML and NVD integration
'''

import xml.etree.ElementTree as ET
import os
import re
from typing import Dict, List, Optional


# ============================================================================
# PARTE 1: FUNZIONI DI PARSING BASE (necessarie per ML/NVD)
# ============================================================================

def parse_nmap_xml(xml_file):
    """
    Parse Nmap XML file and extract vulnerabilities
    
    Args:
        xml_file: Path to Nmap XML file
        
    Returns:
        list: List of vulnerability dictionaries
    """
    vulnerabilities = []
    
    try:
        tree = ET.parse(xml_file)
        root = tree.getroot()
        
        # Parse hosts
        for host in root.findall('.//host'):
            # Get host info
            addr_elem = host.find('.//address[@addrtype="ipv4"]')
            if addr_elem is None:
                addr_elem = host.find('.//address[@addrtype="ipv6"]')
            
            ip_address = addr_elem.get('addr') if addr_elem is not None else 'unknown'
            
            # Get hostname if available
            hostname_elem = host.find('.//hostname')
            hostname = hostname_elem.get('name') if hostname_elem is not None else ''
            
            # Parse ports and scripts
            for port in host.findall('.//port'):
                port_id = port.get('portid')
                protocol = port.get('protocol')
                
                # Get service info
                service = port.find('service')
                service_name = service.get('name', 'unknown') if service is not None else 'unknown'
                service_product = service.get('product', '') if service is not None else ''
                service_version = service.get('version', '') if service is not None else ''
                
                # Parse NSE scripts for vulnerabilities
                for script in port.findall('.//script'):
                    script_id = script.get('id')
                    script_output = script.get('output', '')
                    
                    # Check if it's a vulnerability script
                    if 'vuln' in script_id or 'cve' in script_id.lower():
                        # Parse CVE IDs from script output
                        cve_pattern = r'CVE-\d{4}-\d{4,7}'
                        cves = re.findall(cve_pattern, script_output)
                        
                        # Extract CVSS scores if present
                        cvss_pattern = r'CVSS(?:v[23])?:\s*(\d+\.?\d*)'
                        cvss_matches = re.findall(cvss_pattern, script_output)
                        cvss_score = float(cvss_matches[0]) if cvss_matches else 0.0
                        
                        # Create vulnerability entry for each CVE
                        if cves:
                            for cve_id in cves:
                                vuln = {
                                    'cve_id': cve_id,
                                    'ip_address': ip_address,
                                    'hostname': hostname,
                                    'port': port_id,
                                    'protocol': protocol,
                                    'service': service_name,
                                    'product': service_product,
                                    'version': service_version,
                                    'script_id': script_id,
                                    'description': script_output[:500],
                                    'cvss_score': cvss_score,
                                    'severity': _determine_severity(cvss_score),
                                    'attack_vector': 'NETWORK',
                                    'attack_complexity': 'LOW',
                                    'privileges_required': 'NONE',
                                    'user_interaction': 'NONE',
                                    'confidentiality_impact': 'HIGH',
                                    'integrity_impact': 'HIGH',
                                    'availability_impact': 'HIGH'
                                }
                                vulnerabilities.append(vuln)
                        else:
                            # No CVE but vulnerability script found
                            vuln = {
                                'cve_id': f'NMAP-{script_id}',
                                'ip_address': ip_address,
                                'hostname': hostname,
                                'port': port_id,
                                'protocol': protocol,
                                'service': service_name,
                                'product': service_product,
                                'version': service_version,
                                'script_id': script_id,
                                'description': script_output[:500],
                                'cvss_score': cvss_score,
                                'severity': _determine_severity(cvss_score),
                                'attack_vector': 'NETWORK',
                                'attack_complexity': 'LOW',
                                'privileges_required': 'NONE',
                                'user_interaction': 'NONE',
                                'confidentiality_impact': 'HIGH',
                                'integrity_impact': 'HIGH',
                                'availability_impact': 'HIGH'
                            }
                            vulnerabilities.append(vuln)
        
        print(f"✓ Parsed {len(vulnerabilities)} vulnerabilities from XML")
        return vulnerabilities
        
    except Exception as e:
        print(f"✗ Error parsing XML: {e}")
        return []


def _determine_severity(cvss_score):
    """
    Determine severity level from CVSS score
    
    Args:
        cvss_score: CVSS score (0-10)
        
    Returns:
        str: Severity level
    """
    if cvss_score >= 9.0:
        return 'CRITICAL'
    elif cvss_score >= 7.0:
        return 'HIGH'
    elif cvss_score >= 4.0:
        return 'MEDIUM'
    else:
        return 'LOW'


# ============================================================================
# PARTE 2: CLASSE ENHANCED PARSER (per ML e NVD)
# ============================================================================

class EnhancedParser:
    """Enhanced XML Parser with ML and NVD integration"""
    
    def __init__(self, use_ml=False, use_risk_scorer=False, use_nvd=False):
        """
        Initialize enhanced parser
        
        Args:
            use_ml: Enable ML analysis
            use_risk_scorer: Enable risk scoring
            use_nvd: Enable NVD enrichment
        """
        self.use_ml = use_ml
        self.use_risk_scorer = use_risk_scorer
        self.use_nvd = use_nvd
        
        # Initialize components
        self.ml_analyzer = None
        self.risk_scorer = None
        self.nvd_client = None
        
        if use_ml:
            try:
                from src.ml.analyzer import VulnerabilityAnalyzer
                self.ml_analyzer = VulnerabilityAnalyzer()
            except Exception as e:
                print(f"⚠ ML Analyzer non disponibile: {e}")
        
        if use_risk_scorer:
            try:
                from src.utils.risk_scorer import RiskScorer
                self.risk_scorer = RiskScorer()
            except Exception as e:
                print(f"⚠ Risk Scorer non disponibile: {e}")
        
        if use_nvd:
            try:
                from src.nvd.nvd_client import NVDClient
                self.nvd_client = NVDClient()
            except Exception as e:
                print(f"⚠ NVD Client non disponibile: {e}")
    
    def parse_and_enhance(self, xml_file):
        """
        Parse XML and enhance with ML/NVD
        
        Args:
            xml_file: Path to XML file
            
        Returns:
            dict: Enhanced results
        """
        print(f"[1/4] Parsing XML: {xml_file}")
        
        # Parse base vulnerabilities
        vulnerabilities = parse_nmap_xml(xml_file)
        
        if not vulnerabilities:
            print("⚠ No vulnerabilities found")
            return {
                'vulnerabilities': [],
                'summary': {
                    'total': 0,
                    'by_severity': {},
                    'by_priority': {},
                    'top_risks': [],
                    'nvd_enriched_count': 0
                }
            }
        
        print(f"[2/4] Found {len(vulnerabilities)} vulnerabilities")
        
        # ML Enhancement
        if self.ml_analyzer:
            print("[3/4] Applying ML analysis...")
            for vuln in vulnerabilities:
                try:
                    ml_result = self.ml_analyzer.analyze(vuln)
                    vuln.update(ml_result)
                except Exception as e:
                    print(f"⚠ ML analysis failed for {vuln.get('cve_id')}: {e}")
        else:
            print("[3/4] ML analysis: SKIPPED")
        
        # Risk Scoring
        if self.risk_scorer:
            print("[3.5/4] Calculating risk scores...")
            for vuln in vulnerabilities:
                try:
                    risk_score = self.risk_scorer.calculate_risk_score(vuln)
                    vuln['risk_score'] = risk_score
                    vuln['priority'] = self.risk_scorer.get_priority(risk_score)
                except Exception as e:
                    print(f"⚠ Risk scoring failed for {vuln.get('cve_id')}: {e}")
        
        # NVD Enrichment
        nvd_enriched = 0
        if self.nvd_client:
            print("[4/4] Enriching with NVD data...")
            for vuln in vulnerabilities:
                cve_id = vuln.get('cve_id', '')
                if cve_id.startswith('CVE-'):
                    try:
                        nvd_data = self.nvd_client.get_cve(cve_id)
                        if nvd_data:
                            vuln['nvd_data'] = nvd_data
                            # Update CVSS if NVD has better data
                            if nvd_data.get('cvss_score', 0) > vuln.get('cvss_score', 0):
                                vuln['cvss_score'] = nvd_data['cvss_score']
                                vuln['severity'] = nvd_data['cvss_severity']
                            nvd_enriched += 1
                    except Exception as e:
                        print(f"⚠ NVD enrichment failed for {cve_id}: {e}")
        else:
            print("[4/4] NVD enrichment: SKIPPED")
        
        # Generate summary
        summary = self._generate_summary(vulnerabilities, nvd_enriched)
        
        return {
            'vulnerabilities': vulnerabilities,
            'summary': summary
        }
    
    def _generate_summary(self, vulnerabilities, nvd_enriched):
        """Generate summary statistics"""
        summary = {
            'total': len(vulnerabilities),
            'by_severity': {},
            'by_priority': {},
            'top_risks': [],
            'nvd_enriched_count': nvd_enriched
        }
        
        # Count by severity
        for vuln in vulnerabilities:
            severity = vuln.get('severity', 'UNKNOWN')
            summary['by_severity'][severity] = summary['by_severity'].get(severity, 0) + 1
        
        # Count by priority
        for vuln in vulnerabilities:
            priority = vuln.get('priority', 4)
            summary['by_priority'][priority] = summary['by_priority'].get(priority, 0) + 1
        
        # Top risks
        if self.risk_scorer:
            sorted_vulns = sorted(
                vulnerabilities,
                key=lambda v: v.get('risk_score', 0),
                reverse=True
            )
            summary['top_risks'] = [
                {
                    'cve_id': v['cve_id'],
                    'cvss': v.get('cvss_score', 0),
                    'risk_score': v.get('risk_score', 0),
                    'priority': v.get('priority', 4)
                }
                for v in sorted_vulns[:5]
            ]
        
        return summary


def parse_with_ml(xml_file, use_ml=True, use_risk_scorer=True, use_nvd=False):
    """
    Convenience function to parse with ML enhancement
    
    Args:
        xml_file: Path to XML file
        use_ml: Enable ML analysis
        use_risk_scorer: Enable risk scoring
        use_nvd: Enable NVD enrichment
        
    Returns:
        dict: Enhanced results
    """
    parser = EnhancedParser(
        use_ml=use_ml,
        use_risk_scorer=use_risk_scorer,
        use_nvd=use_nvd
    )
    return parser.parse_and_enhance(xml_file)


# ============================================================================
# PARTE 3: CLASSE ORIGINALE (mantenuta per compatibilità)
# ============================================================================

class NmapXMLParser:
    '''Parser for Nmap XML output files - Original version'''
    
    def __init__(self, xml_file: str):
        '''
        Initialize parser with XML file
        
        Args:
            xml_file (str): Path to Nmap XML output file
        '''
        self.xml_file = xml_file
        self.tree = None
        self.root = None
        self._parse_file()
    
    def _parse_file(self):
        '''Parse XML file and set tree/root'''
        try:
            self.tree = ET.parse(self.xml_file)
            self.root = self.tree.getroot()
        except ET.ParseError as e:
            raise Exception(f'XML parsing error: {e}')
        except FileNotFoundError:
            raise Exception(f'XML file not found: {self.xml_file}')
    
    def get_scan_info(self) -> Dict:
        '''
        Extract scan metadata
        
        Returns:
            dict: Scan information
        '''
        return {
            'scanner': self.root.get('scanner'),
            'args': self.root.get('args'),
            'start_time': self.root.get('start'),
            'version': self.root.get('version')
        }
    
    def parse_host(self, host_elem) -> Dict:
        '''
        Parse single host element
        
        Args:
            host_elem: XML element for host
        
        Returns:
            dict: Parsed host data
        '''
        host_data = {
            'status': {},
            'addresses': [],
            'hostnames': [],
            'ports': []
        }
        
        # Status
        status = host_elem.find('status')
        if status is not None:
            host_data['status'] = {
                'state': status.get('state'),
                'reason': status.get('reason')
            }
        
        # Addresses
        for addr in host_elem.findall('address'):
            host_data['addresses'].append({
                'addr': addr.get('addr'),
                'addrtype': addr.get('addrtype')
            })
        
        # Hostnames
        hostnames = host_elem.find('hostnames')
        if hostnames is not None:
            for hostname in hostnames.findall('hostname'):
                host_data['hostnames'].append({
                    'name': hostname.get('name'),
                    'type': hostname.get('type')
                })
        
        # Ports
        ports_elem = host_elem.find('ports')
        if ports_elem is not None:
            for port in ports_elem.findall('port'):
                port_data = self._parse_port(port)
                if port_data:
                    host_data['ports'].append(port_data)
        
        return host_data
    
    def _parse_port(self, port_elem) -> Optional[Dict]:
        '''
        Parse single port element
        
        Args:
            port_elem: XML element for port
        
        Returns:
            dict: Parsed port data
        '''
        port_data = {
            'portid': port_elem.get('portid'),
            'protocol': port_elem.get('protocol'),
            'state': {},
            'service': {}
        }
        
        # State
        state = port_elem.find('state')
        if state is not None:
            port_data['state'] = {
                'state': state.get('state'),
                'reason': state.get('reason')
            }
        
        # Service
        service = port_elem.find('service')
        if service is not None:
            port_data['service'] = {
                'name': service.get('name'),
                'product': service.get('product'),
                'version': service.get('version')
            }
        
        return port_data
    
    def parse_all_hosts(self) -> List[Dict]:
        '''
        Parse all hosts in scan
        
        Returns:
            list: List of parsed host dictionaries
        '''
        hosts = []
        for host_elem in self.root.findall('host'):
            host_data = self.parse_host(host_elem)
            hosts.append(host_data)
        return hosts
    
    def parse_complete(self) -> Dict:
        '''
        Parse complete scan output
        
        Returns:
            dict: Complete parsed data
        '''
        return {
            'scan_info': self.get_scan_info(),
            'hosts': self.parse_all_hosts()
        }


# ============================================================================
# MAIN - Test both parsers
# ============================================================================

if __name__ == '__main__':
    import sys
    import json
    
    xml_file = 'test_scan.xml'
    
    if len(sys.argv) > 1:
        xml_file = sys.argv[1]
    
    if not os.path.exists(xml_file):
        print(f'Error: {xml_file} not found!')
        print('Run nmap scan first to create XML file.')
        sys.exit(1)
    
    print("=" * 70)
    print("TESTING ENHANCED PARSER (with ML/NVD)")
    print("=" * 70)
    
    try:
        # Test enhanced parser
        results = parse_with_ml(xml_file, use_ml=False, use_risk_scorer=False, use_nvd=False)
        
        print("\n" + "="*70)
        print("RESULTS")
        print("="*70)
        print(json.dumps(results['summary'], indent=2))
        
        if results['vulnerabilities']:
            print("\nFirst vulnerability:")
            print(json.dumps(results['vulnerabilities'][0], indent=2))
        
    except Exception as e:
        print(f'Error: {e}')
        sys.exit(1)
    
    print("\n" + "=" * 70)
    print("TESTING ORIGINAL PARSER (backward compatibility)")
    print("=" * 70)
    
    try:
        # Test original parser
        parser = NmapXMLParser(xml_file)
        
        print('\n=== Scan Info ===')
        scan_info = parser.get_scan_info()
        for key, value in scan_info.items():
            print(f'{key}: {value}')
        
        print('\n=== Hosts ===')
        hosts = parser.parse_all_hosts()
        print(f'Found {len(hosts)} host(s)')
        
        for host in hosts:
            ip = host['addresses'][0]['addr'] if host['addresses'] else 'N/A'
            state = host['status'].get('state', 'unknown')
            print(f'\nHost: {ip} - {state}')
            print(f'  Ports: {len(host["ports"])} found')
    
    except Exception as e:
        print(f'Error: {e}')
        sys.exit(1)
