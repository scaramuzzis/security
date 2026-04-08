import requests
import time
import os
from dotenv import load_dotenv
from datetime import datetime, timedelta


# Load environment variables
load_dotenv()


class NVDClient:
    '''Client per NVD API 2.0'''
    
    def __init__(self, api_key=None):
        '''
        Initialize NVD client
        
        Args:
            api_key: NVD API key (optional, reads from env if not provided)
        '''
        self.api_key = api_key or os.getenv('NVD_API_KEY')
        self.base_url = 'https://services.nvd.nist.gov/rest/json/cves/2.0'
        
        # Rate limiting
        self.request_delay = 0.6 if self.api_key else 6  # seconds
        self.last_request_time = 0
        
        print(f"✓ NVD Client inizializzato")
        if self.api_key:
            print(f"  API Key: presente (rate limit: {1/self.request_delay:.1f} req/sec)")
        else:
            print(f"  ⚠ API Key mancante (rate limit: {1/self.request_delay:.2f} req/sec)")
    
    def _wait_rate_limit(self):
        '''Wait for rate limiting'''
        current_time = time.time()
        time_since_last = current_time - self.last_request_time
        
        if time_since_last < self.request_delay:
            wait_time = self.request_delay - time_since_last
            time.sleep(wait_time)
        
        self.last_request_time = time.time()
    
    def get_cve(self, cve_id):
        '''
        Get single CVE by ID
        
        Args:
            cve_id: CVE ID (e.g., 'CVE-2024-1234')
            
        Returns:
            dict: CVE data or None if not found
        '''
        self._wait_rate_limit()
        
        try:
            headers = {}
            if self.api_key:
                headers['apiKey'] = self.api_key
            
            params = {'cveId': cve_id}
            
            response = requests.get(
                self.base_url,
                headers=headers,
                params=params,
                timeout=30
            )
            
            if response.status_code == 200:
                data = response.json()
                if data.get('vulnerabilities'):
                    return self._parse_cve(data['vulnerabilities'][0])
                return None
            
            elif response.status_code == 404:
                print(f"⚠ CVE non trovato: {cve_id}")
                return None
            
            else:
                print(f"⚠ Errore API: {response.status_code}")
                return None
                
        except Exception as e:
            print(f"⚠ Errore richiesta CVE {cve_id}: {e}")
            return None
    
    def search_recent_cves(self, days=7, max_results=20):
        '''
        Search recent CVEs
        
        Args:
            days: Number of days to look back
            max_results: Max number of results
            
        Returns:
            list: List of CVE dicts
        '''
        self._wait_rate_limit()
        
        try:
            headers = {}
            if self.api_key:
                headers['apiKey'] = self.api_key
            
            # Calculate date range
            end_date = datetime.now()
            start_date = end_date - timedelta(days=days)
            
            params = {
                'pubStartDate': start_date.strftime('%Y-%m-%dT00:00:00.000'),
                'pubEndDate': end_date.strftime('%Y-%m-%dT23:59:59.999'),
                'resultsPerPage': min(max_results, 2000)
            }
            
            response = requests.get(
                self.base_url,
                headers=headers,
                params=params,
                timeout=30
            )
            
            if response.status_code == 200:
                data = response.json()
                cves = []
                
                for vuln in data.get('vulnerabilities', []):
                    parsed = self._parse_cve(vuln)
                    if parsed:
                        cves.append(parsed)
                
                print(f"✓ Trovate {len(cves)} CVE recenti")
                return cves
            
            else:
                print(f"⚠ Errore ricerca: {response.status_code}")
                return []
                
        except Exception as e:
            print(f"⚠ Errore ricerca CVE: {e}")
            return []
    
    def search_by_keyword(self, keyword, max_results=20):
        '''
        Search CVEs by keyword
        
        Args:
            keyword: Keyword to search (e.g., 'apache', 'mysql')
            max_results: Max results
            
        Returns:
            list: List of CVE dicts
        '''
        self._wait_rate_limit()
        
        try:
            headers = {}
            if self.api_key:
                headers['apiKey'] = self.api_key
            
            params = {
                'keywordSearch': keyword,
                'resultsPerPage': min(max_results, 2000)
            }
            
            response = requests.get(
                self.base_url,
                headers=headers,
                params=params,
                timeout=30
            )
            
            if response.status_code == 200:
                data = response.json()
                cves = []
                
                for vuln in data.get('vulnerabilities', []):
                    parsed = self._parse_cve(vuln)
                    if parsed:
                        cves.append(parsed)
                
                print(f"✓ Trovate {len(cves)} CVE per '{keyword}'")
                return cves
            
            else:
                print(f"⚠ Errore ricerca: {response.status_code}")
                return []
                
        except Exception as e:
            print(f"⚠ Errore ricerca keyword: {e}")
            return []
    
    def _parse_cve(self, vuln_data):
        '''Parse CVE data from NVD format'''
        try:
            cve = vuln_data.get('cve', {})
            
            # Basic info
            cve_id = cve.get('id', 'UNKNOWN')
            
            # Description
            descriptions = cve.get('descriptions', [])
            description = descriptions[0].get('value', '') if descriptions else ''
            
            # CVSS metrics (try v3.1 first, then v3.0, then v2.0)
            metrics = cve.get('metrics', {})
            cvss_data = None
            
            if 'cvssMetricV31' in metrics and metrics['cvssMetricV31']:
                cvss_data = metrics['cvssMetricV31'][0]['cvssData']
            elif 'cvssMetricV30' in metrics and metrics['cvssMetricV30']:
                cvss_data = metrics['cvssMetricV30'][0]['cvssData']
            elif 'cvssMetricV2' in metrics and metrics['cvssMetricV2']:
                cvss_data = metrics['cvssMetricV2'][0]['cvssData']
            
            if not cvss_data:
                return None  # Skip CVEs without CVSS
            
            # Parse CVSS metrics
            result = {
                'cve_id': cve_id,
                'description': description[:500],  # Truncate
                'cvss_score': float(cvss_data.get('baseScore', 0.0)),
                'cvss_severity': cvss_data.get('baseSeverity', 'UNKNOWN'),
                'attack_vector': cvss_data.get('attackVector', 'LOCAL'),
                'attack_complexity': cvss_data.get('attackComplexity', 'HIGH'),
                'privileges_required': cvss_data.get('privilegesRequired', 'HIGH'),
                'user_interaction': cvss_data.get('userInteraction', 'REQUIRED'),
                'confidentiality_impact': cvss_data.get('confidentialityImpact', 'NONE'),
                'integrity_impact': cvss_data.get('integrityImpact', 'NONE'),
                'availability_impact': cvss_data.get('availabilityImpact', 'NONE'),
                'published_date': cve.get('published', ''),
                'last_modified': cve.get('lastModified', '')
            }
            
            # Normalize severity
            score = result['cvss_score']
            if score >= 9.0:
                result['severity'] = 'CRITICAL'
            elif score >= 7.0:
                result['severity'] = 'HIGH'
            elif score >= 4.0:
                result['severity'] = 'MEDIUM'
            else:
                result['severity'] = 'LOW'
            
            return result
            
        except Exception as e:
            print(f"⚠ Errore parsing CVE: {e}")
            return None
    
    def enrich_vulnerability(self, vuln_data):
        '''
        Enrich vulnerability data with NVD info
        
        Args:
            vuln_data: Vulnerability dict with 'cve_id' field
            
        Returns:
            dict: Enriched vulnerability data
        '''
        cve_id = vuln_data.get('cve_id')
        if not cve_id:
            return vuln_data
        
        # Get NVD data
        nvd_data = self.get_cve(cve_id)
        
        if nvd_data:
            # Merge data (NVD overwrites existing if present)
            enriched = vuln_data.copy()
            enriched.update({
                'nvd_data': nvd_data,
                'nvd_enriched': True,
                'description': nvd_data.get('description', vuln_data.get('description', '')),
                'published_date': nvd_data.get('published_date', ''),
                'last_modified': nvd_data.get('last_modified', '')
            })
            return enriched
        
        return vuln_data


# Helper functions
def quick_cve_lookup(cve_id):
    '''Quick lookup single CVE'''
    client = NVDClient()
    return client.get_cve(cve_id)


def search_recent_vulns(days=7):
    '''Search recent vulnerabilities'''
    client = NVDClient()
    return client.search_recent_cves(days=days)


if __name__ == '__main__':
    # Test
    print("="*60)
    print("TEST NVD API CLIENT")
    print("="*60)
    
    client = NVDClient()
    
    # Test 1: Get specific CVE
    print("\nTest 1: Get CVE-2021-44228 (Log4Shell)")
    cve = client.get_cve('CVE-2021-44228')
    if cve:
        print(f"✓ CVE ID: {cve['cve_id']}")
        print(f"  Score: {cve['cvss_score']}")
        print(f"  Severity: {cve['severity']}")
        print(f"  Description: {cve['description'][:100]}...")
    
    # Test 2: Search recent
    print("\nTest 2: Recent CVEs (last 7 days)")
    recent = client.search_recent_cves(days=7, max_results=5)
    print(f"✓ Found {len(recent)} recent CVEs")
    for cve in recent[:3]:
        print(f"  - {cve['cve_id']}: {cve['cvss_score']} ({cve['severity']})")
    
    print("\n" + "="*60)
