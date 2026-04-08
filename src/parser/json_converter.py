
'''
JSON Converter for Parsed Nmap Data
Converts parsed dictionaries to JSON format
'''

import json
from typing import Dict
from datetime import datetime

class NmapJSONConverter:
    '''Converts parsed Nmap data to JSON'''
    
    def __init__(self, parsed_data: Dict):
        '''
        Initialize with parsed data
        
        Args:
            parsed_data (dict): Output from NmapXMLParser.parse_complete()
        '''
        self.parsed_data = parsed_data
    
    def to_json_string(self, pretty: bool = True) -> str:
        '''
        Convert to JSON string
        
        Args:
            pretty (bool): Pretty print with indentation
        
        Returns:
            str: JSON string
        '''
        if pretty:
            return json.dumps(self.parsed_data, indent=2)
        return json.dumps(self.parsed_data)
    
    def to_json_file(self, output_file: str, pretty: bool = True):
        '''
        Save to JSON file
        
        Args:
            output_file (str): Path to output JSON file
            pretty (bool): Pretty print with indentation
        '''
        try:
            with open(output_file, 'w') as f:
                if pretty:
                    json.dump(self.parsed_data, f, indent=2)
                else:
                    json.dump(self.parsed_data, f)
            print(f'[+] JSON saved to {output_file}')
        except Exception as e:
            raise Exception(f'Error saving JSON: {e}')
    
    def get_summary(self) -> Dict:
        '''
        Get scan summary with key metrics
        
        Returns:
            dict: Summary with statistics
        '''
        summary = {
            'total_hosts': len(self.parsed_data.get('hosts', [])),
            'hosts_up': 0,
            'total_open_ports': 0,
            'unique_services': set()
        }
        
        for host in self.parsed_data.get('hosts', []):
            if host.get('status', {}).get('state') == 'up':
                summary['hosts_up'] += 1
            
            for port in host.get('ports', []):
                if port.get('state', {}).get('state') == 'open':
                    summary['total_open_ports'] += 1
                    service = port.get('service', {}).get('name')
                    if service:
                        summary['unique_services'].add(service)
        
        # Convert set to list for JSON serialization
        summary['unique_services'] = list(summary['unique_services'])
        
        return summary


# Test code
if __name__ == '__main__':
    import sys
    import os
    from xml_parser import NmapXMLParser
    
    xml_file = 'test_scan.xml'
    
    if not os.path.exists(xml_file):
        print(f'Error: {xml_file} not found!')
        print('Run nmap_wrapper.py first.')
        sys.exit(1)
    
    try:
        # Parse XML
        print('[*] Parsing XML...')
        parser = NmapXMLParser(xml_file)
        parsed_data = parser.parse_complete()
        
        # Convert to JSON
        print('[*] Converting to JSON...')
        converter = NmapJSONConverter(parsed_data)
        
        # Save to file
        json_file = 'test_scan.json'
        converter.to_json_file(json_file)
        
        # Print summary
        print('\\n=== Scan Summary ===')
        summary = converter.get_summary()
        for key, value in summary.items():
            print(f'{key}: {value}')
        
        print(f'\\n[+] JSON file created: {json_file}')
        
    except Exception as e:
        print(f'Error: {e}')
        sys.exit(1)

