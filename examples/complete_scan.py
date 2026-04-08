'''
Complete Scan Pipeline Example
Demonstrates full workflow: Scan → Parse → Convert → Save
Runs exactly: nmap -sV --script vuln -oX <xml_file> <target>
'''

import sys
import os
import shlex
import subprocess

# Add parent directory to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src.parser.xml_parser import NmapXMLParser
from src.parser.json_converter import NmapJSONConverter

def run_nmap_exact_order(target, xml_file):
    """
    Run exactly:
      nmap -sV --script vuln -oX <xml_file> <target>
    Returns dict with keys: success, returncode, stdout, stderr
    """
    cmd = ['nmap', '-sV', '--script', 'vuln', '-oX', xml_file, target]
    print(f'[*] Executing: {" ".join(shlex.quote(tok) for tok in cmd)}')
    try:
        proc = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return {
            'success': proc.returncode == 0,
            'returncode': proc.returncode,
            'stdout': proc.stdout,
            'stderr': proc.stderr
        }
    except FileNotFoundError:
        return {
            'success': False,
            'returncode': None,
            'stdout': '',
            'stderr': 'nmap: command not found -- please install nmap'
        }
    except Exception as e:
        return {
            'success': False,
            'returncode': None,
            'stdout': '',
            'stderr': str(e)
        }

def main():
    print('=' * 60)
    print('AI Security Scanner - Complete Pipeline')
    print('=' * 60)

    target = input('\nEnter target (IP or hostname): ').strip()
    if not target:
        print('[!] Error: Target required')
        sys.exit(1)

    safe_target = target.replace('.', '_').replace('/', '_')
    os.makedirs('scan_results', exist_ok=True)
    xml_file = f'scan_results/{safe_target}_scan.xml'
    json_file = f'scan_results/{safe_target}_scan.json'

    try:
        # Step 1: EXACT command, fixed order (no sudo)
        print(f'\n[1/4] Scanning {target}...')
        print(f'      Command: nmap -sV --script vuln -oX {xml_file} {target}')

        result = run_nmap_exact_order(target, xml_file)
        if not result['success']:
            print(f'[!] Scan failed (returncode={result.get("returncode")})')
            if result.get('stdout'):
                print('--- nmap stdout ---')
                print(result['stdout'])
            if result.get('stderr'):
                print('--- nmap stderr ---')
                print(result['stderr'])
            sys.exit(1)

        print('      ✓ Scan completed')
        print(f'      ✓ XML saved to {xml_file}')

        # Step 2: Parse XML
        print('\n[2/4] Parsing XML output...')
        parser = NmapXMLParser(xml_file)
        parsed_data = parser.parse_complete()
        print('      ✓ XML parsed successfully')

        # Step 3: Convert to JSON
        print('\n[3/4] Converting to JSON...')
        converter = NmapJSONConverter(parsed_data)
        converter.to_json_file(json_file)
        print(f'      ✓ JSON saved to {json_file}')

        # Step 4: Summary
        print('\n[4/4] Generating summary...')
        summary = converter.get_summary()

        print('\n' + '=' * 60)
        print('SCAN SUMMARY')
        print('=' * 60)
        print(f'Target:        {target}')
        print(f'Total Hosts:   {summary.get("total_hosts", 0)}')
        print(f'Hosts Up:      {summary.get("hosts_up", 0)}')
        print(f'Open Ports:    {summary.get("total_open_ports", 0)}')
        unique_services = summary.get("unique_services", [])
        print(f'Services:      {", ".join(unique_services) if unique_services else "None"}')
        print('=' * 60)

        # Details
        print('\n' + '=' * 60)
        print('DETAILED RESULTS')
        print('=' * 60)
        for host in parsed_data.get('hosts', []):
            ip = host.get('addresses', [{}])[0].get('addr', 'N/A')
            state = host.get('status', {}).get('state', 'unknown')
            print(f'\nHost: {ip}')
            print(f'State: {state}')
            if host.get('hostnames'):
                hn = host['hostnames'][0].get('name')
                if hn: print(f'Hostname: {hn}')

            ports = host.get('ports', [])
            if ports:
                print(f'\nOpen Ports ({len(ports)}):')
                for port in ports:
                    portid = port.get('portid')
                    protocol = port.get('protocol')
                    port_state = port.get('state', {}).get('state')
                    svc = port.get('service', {})
                    service_name = svc.get('name', 'unknown')
                    product = svc.get('product', '')
                    version = svc.get('version', '')
                    service_str = f'{product} {version}'.strip() if product else service_name
                    print(f'  {portid}/{protocol} - {port_state} - {service_str}')
            else:
                print('\nNo open ports found')

        print('\n' + '=' * 60)
        print('[+] Scan complete!')
        print(f'[+] Results saved to:')
        print(f'    - XML:  {xml_file}')
        print(f'    - JSON: {json_file}')
        print('=' * 60)

    except KeyboardInterrupt:
        print('\n\n[!] Scan interrupted by user')
        sys.exit(1)
    except Exception as e:
        print(f'\n[!] Error: {e}')
        import traceback; traceback.print_exc()
        sys.exit(1)

if __name__ == '__main__':
    main()
