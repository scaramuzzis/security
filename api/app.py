#!/usr/bin/env python3
"""
AI Security Scanner - REST API
Flask API per scanning e analisi vulnerabilità
"""

import sys
import os

# Add project root to Python path
project_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, project_root)

from flask import Flask, request, jsonify
from flask_cors import CORS
import json
from datetime import datetime
import tempfile
import uuid

# Import esatti da generate_report.py
from src.parser.xml_parser import parse_with_ml
from src.security.attack_surface import AttackSurfaceAnalyzer
from src.security.threat_model import ThreatModeler
from src.security.recommendations import SecurityRecommendations

# Initialize Flask app
app = Flask(__name__)
CORS(app)

# Configuration
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB
app.config['UPLOAD_FOLDER'] = tempfile.gettempdir()


@app.route('/')
def index():
    """API root endpoint"""
    return jsonify({
        'name': 'AI Security Scanner API',
        'version': '1.0.0',
        'status': 'running',
        'endpoints': {
            'health': 'GET /api/health',
            'scan': 'POST /api/scan',
            'analyze': 'POST /api/analyze',
            'stats': 'GET /api/stats'
        }
    })


@app.route('/api/health')
def health():
    """Health check"""
    return jsonify({
        'status': 'healthy',
        'timestamp': datetime.now().isoformat()
    })


@app.route('/api/scan', methods=['POST'])
def scan():
    """
    Upload and analyze Nmap XML
    
    POST /api/scan
    Form data: file (XML)
    Query: ?enrich_nvd=true
    """
    
    if 'file' not in request.files:
        return jsonify({'error': 'No file uploaded'}), 400
    
    file = request.files['file']
    
    if not file.filename or not file.filename.endswith('.xml'):
        return jsonify({'error': 'Invalid file'}), 400
    
    try:
        # Save temp file
        scan_id = str(uuid.uuid4())
        filepath = os.path.join(app.config['UPLOAD_FOLDER'], f'{scan_id}.xml')
        file.save(filepath)
        
        print(f"📁 Processing: {scan_id}")
        
        # Parse with ML
        enrich_nvd = request.args.get('enrich_nvd', 'false').lower() == 'true'
        result = parse_with_ml(filepath, use_nvd=enrich_nvd)
        
        # Extract vulnerabilities from dict
        # parse_with_ml returns: {'vulnerabilities': [...], 'summary': {...}, ...}
        if isinstance(result, dict):
            vulnerabilities = result.get('vulnerabilities', [])
            parse_summary = result.get('summary', {})
        else:
            # Fallback if returns list directly
            vulnerabilities = result if isinstance(result, list) else []
            parse_summary = {}
        
        print(f"  Found {len(vulnerabilities)} vulnerabilities")
        
        # Security Analysis
        attack_surface = AttackSurfaceAnalyzer()
        as_analysis = attack_surface.analyze_surface(vulnerabilities)
        
        threat_modeler = ThreatModeler()
        threat_analysis = threat_modeler.generate_threat_report(vulnerabilities)
        
        rec_generator = SecurityRecommendations()
        recommendations = rec_generator.generate_recommendations({
            'vulnerabilities': vulnerabilities
        })
        
        # Build summary
        summary = {
            'scan_id': scan_id,
            'timestamp': datetime.now().isoformat(),
            'total_vulnerabilities': len(vulnerabilities),
            'by_severity': {},
            'by_priority': {},
            'attack_surface_score': as_analysis.get('total_score', 0),
            'risk_level': as_analysis.get('risk_level', 'UNKNOWN'),
            'total_threats': len(threat_analysis.get('threats', [])),
            'recommendations': len(recommendations.get('recommendations', [])),
            'immediate_actions': len(recommendations.get('action_items', []))
        }
        
        # Count severities and priorities
        for vuln in vulnerabilities:
            sev = vuln.get('severity', 'UNKNOWN')
            summary['by_severity'][sev] = summary['by_severity'].get(sev, 0) + 1
            
            priority = vuln.get('priority', 4)
            summary['by_priority'][priority] = summary['by_priority'].get(priority, 0) + 1
        
        # Cleanup
        os.remove(filepath)
        
        print(f"✓ Completed: {scan_id}")
        
        return jsonify({
            'scan_id': scan_id,
            'summary': summary,
            'vulnerabilities': vulnerabilities[:20],  # Preview
            'attack_surface': as_analysis,
            'threat_model': {
                'summary': threat_analysis.get('summary', ''),
                'total': len(threat_analysis.get('threats', [])),
                'by_category': {k: len(v) for k, v in threat_analysis.get('by_category', {}).items()},
                'top_threats': threat_analysis.get('threats', [])[:10]
            },
            'recommendations': {
                'summary': recommendations.get('summary', ''),
                'actions': recommendations.get('action_items', [])[:10],
                'recommendations': recommendations.get('recommendations', [])[:10]
            }
        })
    
    except Exception as e:
        import traceback
        print(f"❌ Error: {e}")
        traceback.print_exc()
        
        if os.path.exists(filepath):
            os.remove(filepath)
        
        return jsonify({
            'error': str(e),
            'trace': traceback.format_exc()
        }), 500


@app.route('/api/analyze', methods=['POST'])
def analyze():
    """
    Analyze vulnerabilities from JSON
    
    POST /api/analyze
    Body: {"vulnerabilities": [...]}
    """
    
    try:
        data = request.get_json()
        
        if not data or 'vulnerabilities' not in data:
            return jsonify({'error': 'Missing vulnerabilities'}), 400
        
        vulnerabilities = data['vulnerabilities']
        
        print(f"📊 Analyzing {len(vulnerabilities)} vulnerabilities")
        
        # Security Analysis
        attack_surface = AttackSurfaceAnalyzer()
        as_analysis = attack_surface.analyze_surface(vulnerabilities)
        
        threat_modeler = ThreatModeler()
        threat_analysis = threat_modeler.generate_threat_report(vulnerabilities)
        
        rec_generator = SecurityRecommendations()
        recommendations = rec_generator.generate_recommendations({
            'vulnerabilities': vulnerabilities
        })
        
        print(f"✓ Analysis completed")
        
        return jsonify({
            'summary': {
                'total': len(vulnerabilities),
                'attack_surface_score': as_analysis.get('total_score', 0),
                'risk_level': as_analysis.get('risk_level', 'UNKNOWN'),
                'threats': len(threat_analysis.get('threats', [])),
                'recommendations': len(recommendations.get('recommendations', []))
            },
            'attack_surface': as_analysis,
            'threat_model': threat_analysis,
            'recommendations': recommendations
        })
    
    except Exception as e:
        import traceback
        return jsonify({
            'error': str(e),
            'trace': traceback.format_exc()
        }), 500


@app.route('/api/stats')
def stats():
    """API statistics"""
    return jsonify({
        'version': '1.0.0',
        'modules': ['parser', 'ml', 'security', 'recommendations'],
        'max_file_size': '16MB'
    })


# Error handlers
@app.errorhandler(404)
def not_found(e):
    return jsonify({'error': 'Not found'}), 404


@app.errorhandler(500)
def server_error(e):
    return jsonify({'error': 'Server error'}), 500


if __name__ == '__main__':
    print("="*70)
    print("🛡️  AI SECURITY SCANNER - REST API")
    print("="*70)
    print("\n✅ Modules loaded:")
    print("  ✓ XML Parser + ML")
    print("  ✓ Attack Surface Analyzer")
    print("  ✓ STRIDE Threat Model")
    print("  ✓ Security Recommendations")
    
    print("\n📡 Endpoints:")
    print("  GET  /              → Info")
    print("  GET  /api/health    → Health")
    print("  POST /api/scan      → Upload XML")
    print("  POST /api/analyze   → Analyze JSON")
    print("  GET  /api/stats     → Stats")
    
    print("\n🚀 Running on http://0.0.0.0:5000")
    print("="*70 + "\n")
    
    app.run(host='0.0.0.0', port=5000, debug=True)
