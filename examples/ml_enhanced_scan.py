'''
ML-Enhanced Scan with NVD Integration
'''

import sys
import os
import json
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from src.parser.xml_parser import parse_with_ml


def main():
    print("="*70)
    print("ML-ENHANCED VULNERABILITY SCANNER + NVD")
    print("="*70)
    
    if len(sys.argv) < 2:
        print("\nUsage: python3 ml_enhanced_scan.py <nmap_xml_file> [--nvd]")
        print("  --nvd: Enable NVD enrichment (requires API key)")
        return
    
    xml_file = sys.argv[1]
    use_nvd = '--nvd' in sys.argv
    
    if not os.path.exists(xml_file):
        print(f"✗ File non trovato: {xml_file}")
        return
    
    print(f"\nAnalyzing: {xml_file}")
    if use_nvd:
        print("  NVD Enrichment: ENABLED")
    print("-"*70)
    
    results = parse_with_ml(
        xml_file,
        use_ml=True,
        use_risk_scorer=True,
        use_nvd=use_nvd
    )
    
    # Summary
    print("\n" + "="*70)
    print("SCAN SUMMARY")
    print("="*70)
    
    summary = results['summary']
    print(f"\nTotal Vulnerabilities: {summary['total']}")
    
    if use_nvd:
        print(f"NVD Enriched: {summary['nvd_enriched_count']}")
    
    print("\nBy Severity:")
    for sev, count in sorted(summary['by_severity'].items()):
        print(f"  {sev:12s}: {count:3d}")
    
    if summary.get('by_priority'):
        print("\nBy ML Priority:")
        labels = {1: 'URGENT', 2: 'HIGH', 3: 'MEDIUM', 4: 'LOW'}
        for priority in sorted(summary['by_priority'].keys()):
            count = summary['by_priority'][priority]
            label = labels.get(priority, 'UNKNOWN')
            print(f"  Priority {priority} ({label:7s}): {count:3d}")
    
    if summary.get('top_risks'):
        print("\nTop 5 Highest Risk:")
        for i, risk in enumerate(summary['top_risks'], 1):
            print(f"  {i}. {risk['cve_id']:20s} | "
                  f"CVSS: {risk['cvss']:4.1f} | "
                  f"Risk: {risk['risk_score']:5.2f} | "
                  f"Priority: {risk['priority']}")
    
    # Save
    suffix = '_nvd' if use_nvd else ''
    output_file = xml_file.replace('.xml', f'_ml_enhanced{suffix}.json')
    with open(output_file, 'w') as f:
        json.dump(results, f, indent=2, default=str)
    
    print(f"\n✓ Enhanced results saved: {output_file}")
    print("="*70)


if __name__ == '__main__':
    main()
