'''
Nmap Scanner Wrapper
Executes Nmap scans and returns results
'''

import subprocess
import os
from typing import Dict, Optional

class NmapScanner:
    '''Wrapper for Nmap command-line tool'''
    
    def __init__(self):
        '''Initialize scanner'''
        self.nmap_path = 'nmap'
        self._check_nmap_installed()
    
    def _check_nmap_installed(self):
        '''Verify Nmap is installed'''
        try:
            result = subprocess.run (
                [self.nmap_path, '--version'],
                capture_output=True,
                text=True,
                timeout=5
            )
            if result.returncode != 0:
                raise Exception('Nmap not found')
        except FileNotFoundError:
            raise Exception('Nmap is not installed')
        except subprocess.TimeoutExpired:
            raise Exception('Nmap check timeout')
    
    def scan(self, target: str, arguments: str = '-sV -T4') -> Dict:
        '''
        Execute Nmap scan on target
        
        Args:
            target (str): Target IP or hostname
            arguments (str): Nmap arguments (default: -sV -T4)
        
        Returns:
            dict: Scan results with stdout, stderr, returncode
        '''
        # Build command
        cmd = [self.nmap_path] + arguments.split() + [target]
        
        print(f'[*] Executing: {" ".join(cmd)}')
        
        try:
            result = subprocess.run (
                cmd,
                capture_output=True,
                text=True,
                timeout=300  # 5 minutes max
            )
            
            return {
                'success': result.returncode == 0,
                'stdout': result.stdout,
                'stderr': result.stderr,
                'returncode': result.returncode,
                'target': target,
                'arguments': arguments
            }
        
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Scan timeout (>5 minutes)',
                'target': target
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e),
                'target': target
            }
    
    def scan_to_xml(self, target: str, output_file: str, 
                    arguments: str = '-sV -T4') -> Dict:
        '''
        Execute scan and save output to XML file
        
        Args:
            target (str): Target IP or hostname
            output_file (str): Path to output XML file
            arguments (str): Nmap arguments
        
        Returns:
            dict: Scan results
        '''
        # Add XML output argument
        cmd = [self.nmap_path, '-oX', output_file] + \
              arguments.split() + [target]
        
        print(f'[*] Executing: {" ".join(cmd)}')
        print(f'[*] Output will be saved to: {output_file}')
        
        try:
            result = subprocess.run (
                cmd,
                capture_output=True,
                text=True,
                timeout=300
            )
            
            return {
                'success': result.returncode == 0,
                'output_file': output_file,
                'file_exists': os.path.exists(output_file),
                'stdout': result.stdout,
                'stderr': result.stderr,
                'target': target
            }
        
        except subprocess.TimeoutExpired:
            return {
                'success': False,
                'error': 'Scan timeout',
                'target': target
            }
        except Exception as e:
            return {
                'success': False,
                'error': str(e),
                'target': target
            }


# Test code
if __name__ == '__main__':
    print('Testing NmapScanner...')
    
    scanner = NmapScanner()
    
    # Test 1: Basic scan localhost
    print('\\n=== Test 1: Scan localhost ===')
    result = scanner.scan('127.0.0.1', '-F')
    print(f'Success: {result["success"]}')
    if result['success']:
        print('Scan completed!')
    
    # Test 2: XML output
    print('\\n=== Test 2: XML Output ===')
    result = scanner.scan_to_xml('127.0.0.1', 'test_scan.xml', '-F')
    print(f'Success: {result["success"]}')
    print(f'XML file exists: {result.get("file_exists", False)}') 
