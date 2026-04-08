'''
HTML Dashboard Generator
Crea dashboard interattivo vulnerabilità
'''

import os
from datetime import datetime


class DashboardGenerator:
    '''Genera dashboard HTML per scan results'''
    
    def __init__(self, output_dir='reports'):
        '''Initialize generator'''
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)
    
    def generate_dashboard(self, scan_results, plots_dir='reports/plots'):
        '''
        Genera dashboard HTML completo
        '''
        vulnerabilities = scan_results.get('vulnerabilities', [])
        summary = scan_results.get('summary', {})
        
        # Path RELATIVO dei plot rispetto alla cartella output
        rel_plots_dir = os.path.relpath(plots_dir, start=self.output_dir).replace(os.sep, '/')
        
        # Generate HTML
        html = self._generate_html_template(vulnerabilities, summary, rel_plots_dir)
        
        # Save
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        filename = f'dashboard_{timestamp}.html'
        filepath = os.path.join(self.output_dir, filename)
        
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write(html)
        
        print(f'✓ Dashboard salvato: {filepath}')
        return filepath
    
    # -------------------- helpers logici (no stile) --------------------

    @staticmethod
    def _safe_float(x, default=0.0):
        try:
            return float(x)
        except Exception:
            return default

    @staticmethod
    def _safe_int(x, default=0):
        try:
            return int(x)
        except Exception:
            return default

    @staticmethod
    def _priority_from_risk(r):
        if r >= 9.0:  return 1
        if r >= 7.0:  return 2
        if r >= 4.0:  return 3
        return 4

    def _normalized_risk(self, v):
        """
        Usa SEMPRE i campi normalizzati se presenti:
        - risk_score (top-level) -> principale
        - fallback: ml/ml_analysis.risk_score
        - fallback ulteriore: cvss_score (se proprio serve)
        """
        if isinstance(v.get('risk_score'), (int, float)):
            return float(v['risk_score'])
        ml = v.get('ml') or v.get('ml_analysis') or {}
        if isinstance(ml.get('risk_score'), (int, float)):
            return float(ml['risk_score'])
        return self._safe_float(v.get('cvss_score'), 0.0)

    def _normalized_priority(self, v):
        """
        Usa SEMPRE priority normalizzata se c'è; altrimenti derivala dal risk.
        """
        p = v.get('priority')
        if p is not None:
            return self._safe_int(p, 4)
        ml = v.get('ml') or v.get('ml_analysis') or {}
        if ml.get('priority') is not None:
            return self._safe_int(ml.get('priority'), 4)
        return self._priority_from_risk(self._normalized_risk(v))

    def _dedup_top(self, vulnerabilities, top_n=10):
        """
        Deduplica per (cve_id, ip_address, port) tenendo la riga con risk_score più alto.
        """
        best = {}
        for v in vulnerabilities:
            key = (v.get('cve_id') or 'N/A', v.get('ip_address'), v.get('port'))
            r = self._normalized_risk(v)
            cur = best.get(key)
            if cur is None or r > self._normalized_risk(cur):
                best[key] = v
        top = list(best.values())
        top.sort(key=lambda x: (-self._normalized_risk(x), self._normalized_priority(x)))
        return top[:top_n]

    # -------------------- template HTML (stile invariato) --------------------

    def _generate_html_template(self, vulnerabilities, summary, plots_dir):
        '''Generate complete HTML (stile invariato)'''
        
        # ----- Stats di riepilogo -----
        total = summary.get('total', len(vulnerabilities))
        by_severity = summary.get('by_severity', {})
        by_priority = summary.get('by_priority', {})
        critical_count = by_severity.get('CRITICAL', 0)
        urgent_count = by_priority.get(1, by_priority.get('1', 0))

        # Average risk: preferisci summary.average_risk, altrimenti calcola dai risk_score normalizzati
        if isinstance(summary.get('average_risk'), (int, float)):
            avg_risk = float(summary['average_risk'])
        else:
            risks_all = [self._normalized_risk(v) for v in vulnerabilities] if vulnerabilities else []
            avg_risk = (sum(risks_all) / len(risks_all)) if risks_all else 0.0
        
        # ----- HTML head & layout (INVARIATO) -----
        html = f'''<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Security Dashboard - {datetime.now().strftime('%Y-%m-%d')}</title>
    <style>
        * {{
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }}
        
        body {{
            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            color: #333;
            padding: 2rem;
        }}
        
        .container {{
            max-width: 1400px;
            margin: 0 auto;
        }}
        
        header {{
            background: white;
            padding: 2rem;
            border-radius: 15px;
            margin-bottom: 2rem;
            box-shadow: 0 5px 15px rgba(0,0,0,0.1);
        }}
        
        h1 {{
            color: #667eea;
            margin-bottom: 0.5rem;
        }}
        
        .subtitle {{
            color: #666;
            font-size: 1.1rem;
        }}
        
        .stats-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
            gap: 1.5rem;
            margin-bottom: 2rem;
        }}
        
        .stat-card {{
            background: white;
            padding: 1.5rem;
            border-radius: 15px;
            box-shadow: 0 5px 15px rgba(0,0,0,0.1);
        }}
        
        .stat-value {{
            font-size: 2.5rem;
            font-weight: bold;
            margin: 0.5rem 0;
        }}
        
        .stat-label {{
            color: #666;
            font-size: 0.9rem;
            text-transform: uppercase;
        }}
        
        .critical {{ color: #d32f2f; }}
        .high {{ color: #f57c00; }}
        .medium {{ color: #fbc02d; }}
        .low {{ color: #388e3c; }}
        
        .charts-grid {{
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(500px, 1fr));
            gap: 2rem;
            margin-bottom: 2rem;
        }}
        
        .chart-card {{
            background: white;
            padding: 1.5rem;
            border-radius: 15px;
            box-shadow: 0 5px 15px rgba(0,0,0,0.1);
        }}
        
        .chart-card h2 {{
            margin-bottom: 1rem;
            color: #667eea;
        }}
        
        .chart-card img {{
            width: 100%;
            height: auto;
            border-radius: 10px;
        }}
        
        .vulnerability-list {{
            background: white;
            padding: 1.5rem;
            border-radius: 15px;
            box-shadow: 0 5px 15px rgba(0,0,0,0.1);
        }}
        
        .vulnerability-item {{
            padding: 1rem;
            border-left: 4px solid #ddd;
            margin-bottom: 1rem;
            background: #f9f9f9;
            border-radius: 5px;
        }}
        
        .vulnerability-item.critical {{ border-left-color: #d32f2f; }}
        .vulnerability-item.high {{ border-left-color: #f57c00; }}
        .vulnerability-item.medium {{ border-left-color: #fbc02d; }}
        .vulnerability-item.low {{ border-left-color: #388e3c; }}
        
        .vuln-header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 0.5rem;
        }}
        
        .vuln-cve {{
            font-weight: bold;
            font-size: 1.1rem;
        }}
        
        .vuln-score {{
            background: #667eea;
            color: white;
            padding: 0.25rem 0.75rem;
            border-radius: 20px;
            font-size: 0.9rem;
        }}
        
        .recommendation {{
            margin-top: 0.5rem;
            padding: 0.5rem;
            background: #fff3cd;
            border-radius: 5px;
            font-size: 0.9rem;
        }}
        
        footer {{
            text-align: center;
            color: white;
            margin-top: 2rem;
            padding: 1rem;
        }}
        
        @media (max-width: 768px) {{
            .charts-grid {{
                grid-template-columns: 1fr;
            }}
        }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <h1>🛡️ Security Vulnerability Dashboard</h1>
            <p class="subtitle">Generated: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}</p>
        </header>
        
        <div class="stats-grid">
            <div class="stat-card">
                <div class="stat-label">Total Vulnerabilities</div>
                <div class="stat-value">{total}</div>
            </div>
            
            <div class="stat-card">
                <div class="stat-label">Critical Issues</div>
                <div class="stat-value critical">{critical_count}</div>
            </div>
            
            <div class="stat-card">
                <div class="stat-label">Urgent Actions</div>
                <div class="stat-value high">{by_priority.get(1, by_priority.get('1', 0))}</div>
            </div>
            
            <div class="stat-card">
                <div class="stat-label">Average Risk Score</div>
                <div class="stat-value medium">{avg_risk:.2f}</div>
            </div>
        </div>
        
        <div class="charts-grid">
            <div class="chart-card">
                <h2>📊 Severity Distribution</h2>
                <img src="{plots_dir}/severity_dist.png" alt="Severity Distribution">
            </div>
            
            <div class="chart-card">
                <h2>🎯 Priority Distribution</h2>
                <img src="{plots_dir}/priority_dist.png" alt="Priority Distribution">
            </div>
            
            <div class="chart-card">
                <h2>📈 Risk Score Distribution</h2>
                <img src="{plots_dir}/risk_dist.png" alt="Risk Distribution">
            </div>
            
            <div class="chart-card">
                <h2>🔝 Top Vulnerabilities</h2>
                <img src="{plots_dir}/top_vulns.png" alt="Top Vulnerabilities">
            </div>
        </div>
        
        <div class="vulnerability-list">
            <h2>🚨 Top 10 Highest Risk Vulnerabilities</h2>
'''
        # ----- Top 10: dedup per (CVE, IP, Port) e sort su risk/priority (logica invariata nel rendering) -----
        def _rec_from(priority):
            if priority == 1:
                return '🔴 Azione immediata: isolare, patchare, mitigare entro 24h.'
            if priority == 2:
                return '🟠 Mitigazione entro 7 giorni: applicare patch e controlli.'
            if priority == 3:
                return '🟡 Pianificare fix: inserire in sprint/maintenance.'
            return '🟢 Monitorare e documentare: rischio basso.'

        top_10 = self._dedup_top(vulnerabilities, top_n=10)

        if not top_10:
            html += '''
            <div class="vulnerability-item">
                Nessuna vulnerabilità con punteggio disponibile. 
                Assicurati di aver importato correttamente i risultati o abilitato l’analisi ML.
            </div>
            '''
        else:
            for vuln in top_10:
                rk = self._normalized_risk(vuln)
                pr = self._normalized_priority(vuln)
                rec = (vuln.get('ml') or vuln.get('ml_analysis') or {}).get('recommendation') or _rec_from(pr)

                cve_id = vuln.get('cve_id', 'Unknown')
                severity = (vuln.get('severity', 'UNKNOWN') or 'UNKNOWN').lower()
                cvss = vuln.get('cvss_score', '')

                html += f'''
                <div class="vulnerability-item {severity}">
                    <div class="vuln-header">
                        <span class="vuln-cve">{cve_id}</span>
                        <span class="vuln-score">Risk: {rk:.2f}/10</span>
                    </div>
                    <div>
                        <strong>CVSS:</strong> {cvss} | 
                        <strong>Severity:</strong> {severity.upper()} | 
                        <strong>Priority:</strong> {pr}
                    </div>
                    <div class="recommendation">
                        💡 {rec}
                    </div>
                </div>
                '''
        
        # ----- Footer & chiusura -----
        html += '''
        </div>
        
        <footer>
            <p>Generated by AI Security Scanner 🤖</p>
            <p>Machine Learning Enhanced | NVD Integrated</p>
        </footer>
    </div>
</body>
</html>
'''
        return html
