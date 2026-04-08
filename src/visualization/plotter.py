'''
Vulnerability Data Visualization
Crea grafici per analisi vulnerabilità
'''

import matplotlib
matplotlib.use('Agg')  # Backend non-interactive
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import os
from datetime import datetime


# Set style
sns.set_style('whitegrid')
plt.rcParams['figure.figsize'] = (10, 6)
plt.rcParams['font.size'] = 10


class VulnerabilityPlotter:
    '''Crea visualizzazioni dati vulnerabilità'''
    
    def __init__(self, output_dir='reports/plots'):
        '''Initialize plotter'''
        self.output_dir = output_dir
        os.makedirs(output_dir, exist_ok=True)
        
        # Color palette
        self.severity_colors = {
            'CRITICAL': '#d32f2f',  # Red
            'HIGH': '#f57c00',      # Orange
            'MEDIUM': '#fbc02d',    # Yellow
            'LOW': '#388e3c'        # Green
        }
        
        self.priority_colors = {
            1: '#d32f2f',  # Urgent - Red
            2: '#f57c00',  # High - Orange
            3: '#fbc02d',  # Medium - Yellow
            4: '#388e3c'   # Low - Green
        }
    
    def _get_ml_data(self, vuln):
        """
        Helper per estrarre dati ML da formato variabile.
        Supporta:
          - formato con 'ml' (normalizzato dal generate_report)
          - formato con 'ml_analysis' (legacy)
          - formato flat (risk_score/priority top-level)
        """
        # 1) formato normalizzato: 'ml'
        ml = vuln.get('ml')
        if isinstance(ml, dict) and (ml.get('risk_score') is not None or ml.get('priority') is not None or ml.get('ml_available')):
            return {
                'ml_available': ml.get('ml_available', True),
                'risk_score': ml.get('risk_score', vuln.get('risk_score', 0)),
                'priority': ml.get('priority', vuln.get('priority', vuln.get('ml_priority', 4)))
            }

        # 2) legacy: 'ml_analysis'
        ml = vuln.get('ml_analysis')
        if isinstance(ml, dict) and (ml.get('ml_available') or ml.get('risk_score') is not None or ml.get('priority') is not None):
            return {
                'ml_available': ml.get('ml_available', True),
                'risk_score': ml.get('risk_score', vuln.get('risk_score', 0)),
                'priority': ml.get('priority', vuln.get('priority', vuln.get('ml_priority', 4)))
            }
        
        # 3) flat
        if any(k in vuln for k in ('ml_available', 'risk_score', 'priority', 'ml_priority')):
            return {
                'ml_available': vuln.get('ml_available', False),
                'risk_score': vuln.get('risk_score', 0),
                'priority': vuln.get('priority', vuln.get('ml_priority', 4))
            }
        
        return None
    
    def plot_severity_distribution(self, vulnerabilities, filename='severity_dist.png'):
        '''
        Grafico a torta distribuzione severity
        '''
        # Count by severity
        severity_counts = {}
        for vuln in vulnerabilities:
            sev = (vuln.get('severity') or 'UNKNOWN').upper()
            severity_counts[sev] = severity_counts.get(sev, 0) + 1
        
        # Ordine preferito; se mancano chiavi, usa quello che c'è
        preferred = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
        present = [k for k in preferred if k in severity_counts]
        others = [k for k in severity_counts.keys() if k not in preferred]
        order = present + sorted(others)

        if not order:
            print('⚠ Nessuna severità disponibile per il grafico')
            return None
        
        sorted_counts = {k: severity_counts.get(k, 0) for k in order}
        
        # Create pie chart
        fig, ax = plt.subplots()
        colors = [self.severity_colors.get(k, 'gray') for k in sorted_counts.keys()]
        
        wedges, texts, autotexts = ax.pie(
            list(sorted_counts.values()),
            labels=list(sorted_counts.keys()),
            colors=colors,
            autopct='%1.1f%%',
            startangle=90
        )
        
        # Style
        for autotext in autotexts:
            autotext.set_color('white')
            autotext.set_fontsize(12)
            autotext.set_weight('bold')
        
        ax.set_title('Vulnerability Distribution by Severity', fontsize=14, weight='bold')
        
        # Save
        filepath = os.path.join(self.output_dir, filename)
        plt.tight_layout()
        plt.savefig(filepath, dpi=150, bbox_inches='tight')
        plt.close()
        
        print(f'✓ Salvato: {filepath}')
        return filepath
    
    def plot_priority_distribution(self, vulnerabilities, filename='priority_dist.png'):
        '''Grafico barre distribuzione priorità ML'''
        # Count by priority
        priority_counts = {}
        for vuln in vulnerabilities:
            ml = self._get_ml_data(vuln)
            if ml:
                p = ml.get('priority', 4)
                try:
                    p = int(p)
                except Exception:
                    p = 4
                priority_counts[p] = priority_counts.get(p, 0) + 1
        
        if not priority_counts:
            print('⚠ Nessun dato ML priority disponibile')
            return None
        
        # Create bar chart
        fig, ax = plt.subplots()
        
        priorities = sorted(priority_counts.keys())
        counts = [priority_counts[p] for p in priorities]
        colors = [self.priority_colors.get(p, 'gray') for p in priorities]
        
        labels = {1: 'Urgent', 2: 'High', 3: 'Medium', 4: 'Low'}
        x_labels = [f'P{p}\n{labels.get(p, "N/A")}' for p in priorities]
        
        bars = ax.bar(range(len(priorities)), counts, color=colors, alpha=0.8)
        
        # Add value labels on bars
        for bar in bars:
            height = bar.get_height()
            ax.text(bar.get_x() + bar.get_width()/2., height,
                   f'{int(height)}',
                   ha='center', va='bottom', fontsize=12, weight='bold')
        
        ax.set_xlabel('Priority Level', fontsize=12, weight='bold')
        ax.set_ylabel('Number of Vulnerabilities', fontsize=12, weight='bold')
        ax.set_title('ML Priority Distribution', fontsize=14, weight='bold')
        ax.set_xticks(range(len(priorities)))
        ax.set_xticklabels(x_labels)
        
        # Save
        filepath = os.path.join(self.output_dir, filename)
        plt.tight_layout()
        plt.savefig(filepath, dpi=150, bbox_inches='tight')
        plt.close()
        
        print(f'✓ Salvato: {filepath}')
        return filepath
    
    def plot_risk_score_distribution(self, vulnerabilities, filename='risk_dist.png'):
        '''Istogramma distribuzione risk score'''
        import re

        PRI_TO_SCORE = {1: 9.5, 2: 8.0, 3: 5.5, 4: 3.0}

        def _first_float(x):
            try:
                return float(x)
            except Exception:
                return None

        def _walk_cvss_scores(obj):
            key_regex = re.compile(r'(base\s*_?score|cvss.*score|^score$)', re.IGNORECASE)
            stack = [obj]
            while stack:
                cur = stack.pop()
                if isinstance(cur, dict):
                    for k in ("baseScore","base_score","score","cvss3_score","cvss_v3","cvss_v31","cvss_v30","cvss_v2","cvss"):
                        if k in cur:
                            val = cur[k]
                            if isinstance(val, dict):
                                for kk in ("baseScore","base_score","score"):
                                    if kk in val:
                                        f = _first_float(val[kk])
                                        if f is not None and 0 <= f <= 10:
                                            return f
                            else:
                                f = _first_float(val)
                                if f is not None and 0 <= f <= 10:
                                    return f
                    for k, v in cur.items():
                        if key_regex.search(str(k)):
                            f = _first_float(v if not isinstance(v, dict) else v.get("baseScore") or v.get("score"))
                            if f is not None and 0 <= f <= 10:
                                return f
                    stack.extend(cur.values())
                elif isinstance(cur, (list, tuple)):
                    stack.extend(cur)
            return None

        def _derive(v):
            ml = self._get_ml_data(v)
            val = ml.get('risk_score') if ml else None
            if val is None:
                val = v.get('risk_score')
            f = _first_float(val)
            if f is not None and 0 <= f <= 10:
                return f
            cv = _walk_cvss_scores(v) or _walk_cvss_scores(v.get('nvd', {}))
            if cv is not None:
                return cv
            # fallback da PRIORITY
            p = None
            if ml:
                p = ml.get('priority')
            if p is None:
                p = v.get('priority') or v.get('ml_priority')
            try:
                p = int(p) if p is not None else None
            except Exception:
                p = None
            if p in PRI_TO_SCORE:
                return PRI_TO_SCORE[p]
            # fallback finale: severity
            sev = (v.get('severity') or (v.get('nvd') or {}).get('severity') or '').upper()
            table = {"CRITICAL": 9.5, "HIGH": 8.0, "MEDIUM": 5.5, "LOW": 3.0, "INFO": 1.0}
            return table.get(sev, 0.0)

        risk_scores = []
        for v in vulnerabilities:
            s = _derive(v)
            s = _first_float(s)
            if s is not None and s > 0:
                risk_scores.append(s)
        
        if not risk_scores:
            print('⚠ Nessun risk score disponibile')
            return None
        
        fig, ax = plt.subplots()
        n, bins, patches = ax.hist(risk_scores, bins=10, edgecolor='black', alpha=0.7)
        for i, patch in enumerate(patches):
            bin_center = (bins[i] + bins[i+1]) / 2
            if bin_center >= 9:   patch.set_facecolor('#d32f2f')
            elif bin_center >= 7: patch.set_facecolor('#f57c00')
            elif bin_center >= 4: patch.set_facecolor('#fbc02d')
            else:                 patch.set_facecolor('#388e3c')
        ax.set_xlabel('Risk Score', fontsize=12, weight='bold')
        ax.set_ylabel('Frequency', fontsize=12, weight='bold')
        ax.set_title('Risk Score Distribution', fontsize=14, weight='bold')
        ax.set_xlim(0, 10)
        avg_risk = sum(risk_scores) / len(risk_scores)
        ax.axvline(avg_risk, color='red', linestyle='--', linewidth=2, label=f'Avg: {avg_risk:.2f}')
        ax.legend()
        filepath = os.path.join(self.output_dir, filename)
        plt.tight_layout()
        plt.savefig(filepath, dpi=150, bbox_inches='tight')
        plt.close()
        print(f'✓ Salvato: {filepath}')
        return filepath
    
    def plot_top_vulnerabilities(self, vulnerabilities, top_n=10, filename='top_vulns.png'):
        """Grafico barre top N vulnerabilità più rischiose (DEDUP per CVE)"""
        # Mappature per fallback risk da severity
        sev_to_score = {"CRITICAL": 9.5, "HIGH": 8.0, "MEDIUM": 5.5, "LOW": 3.0, "INFO": 1.0}

        def _as_float(x):
            try:
                return float(x)
            except Exception:
                return None

        def _risk_for(v):
            # 1) dati ML normalizzati/legacy
            ml = self._get_ml_data(v)
            if ml and _as_float(ml.get('risk_score')) is not None:
                return float(ml['risk_score'])
            # 2) top-level risk_score
            if _as_float(v.get('risk_score')) is not None:
                return float(v.get('risk_score'))
            # 3) fallback da severity
            sev = (v.get('severity') or '').upper()
            return float(sev_to_score.get(sev, 0.0))

        # ---- DEDUP PER CVE: tieni la riga con rischio max per ciascun CVE
        best_by_cve = {}
        # per evitare di collassare tutto ciò che non ha CVE, assegniamo chiavi uniche
        for idx, v in enumerate(vulnerabilities):
            cve = v.get('cve_id')
            key = cve if cve else f'__NO_CVE__#{idx}'
            r = _risk_for(v)

            # salva anche una severità rappresentativa per il colore/etichetta
            sev = (v.get('severity') or 'UNKNOWN').upper()
            if (key not in best_by_cve) or (r > best_by_cve[key]['_risk']):
                best_by_cve[key] = {
                    '_risk': r,
                    '_sev': sev,
                    '_label': cve or (v.get('id') or v.get('name') or f'Unknown #{idx}')
                }

        # lista ordinata per rischio desc, prendi top_n
        rows = sorted(best_by_cve.values(), key=lambda x: x['_risk'], reverse=True)[:top_n]

        if not rows:
            print('⚠ Nessuna vulnerabilità con ML disponibile')
            return None

        # Dati per il grafico
        labels = [str(r['_label'])[:60] for r in rows]
        risk_scores = [r['_risk'] for r in rows]

        # Colori in base al risk
        colors = []
        for score in risk_scores:
            if score >= 9:
                colors.append('#d32f2f')
            elif score >= 7:
                colors.append('#f57c00')
            elif score >= 4:
                colors.append('#fbc02d')
            else:
                colors.append('#388e3c')

        # Grafico barre orizzontali
        import matplotlib.pyplot as plt
        fig, ax = plt.subplots(figsize=(10, max(6, len(labels) * 0.4)))
        bars = ax.barh(range(len(labels)), risk_scores, color=colors, alpha=0.8)

        # Valori sulle barre
        for bar, score in zip(bars, risk_scores):
            ax.text(score + 0.1, bar.get_y() + bar.get_height()/2.,
                    f'{score:.2f}', va='center', fontsize=10, weight='bold')

        ax.set_yticks(range(len(labels)))
        ax.set_yticklabels(labels)
        ax.set_xlabel('Risk Score', fontsize=12, weight='bold')
        ax.set_title(f'Top {len(rows)} Highest Risk Vulnerabilities (dedup by CVE)',
                     fontsize=14, weight='bold')
        ax.set_xlim(0, 10)
        ax.grid(axis='x', alpha=0.3)

        # Salva
        filepath = os.path.join(self.output_dir, filename)
        plt.tight_layout()
        plt.savefig(filepath, dpi=150, bbox_inches='tight')
        plt.close()

        print(f'✓ Salvato: {filepath}')
        return filepath
    
    def create_all_plots(self, vulnerabilities):
        '''Crea tutti i grafici'''
        print('\n=== CREAZIONE GRAFICI ===')
        
        plots = {}
        
        # Severity distribution
        print('\n1. Severity Distribution...')
        plots['severity'] = self.plot_severity_distribution(vulnerabilities)
        
        # Priority distribution
        print('\n2. Priority Distribution...')
        plots['priority'] = self.plot_priority_distribution(vulnerabilities)
        
        # Risk score distribution
        print('\n3. Risk Score Distribution...')
        plots['risk'] = self.plot_risk_score_distribution(vulnerabilities)
        
        # Top vulnerabilities
        print('\n4. Top Vulnerabilities...')
        plots['top'] = self.plot_top_vulnerabilities(vulnerabilities)
        
        print('\n✓ Tutti i grafici creati!')
        return plots


if __name__ == '__main__':
    # Test con dati mock
    print('='*60)
    print('TEST PLOTTER')
    print('='*60)
    
    # Mock data - test both formats
    test_vulns = [
        # Format 1: ml normalizzato
        {
            'cve_id': 'CVE-2024-0001',
            'severity': 'CRITICAL',
            'ml': {'ml_available': True, 'risk_score': 9.5, 'priority': 1}
        },
        # Format 2: ml_analysis legacy
        {
            'cve_id': 'CVE-2024-0002',
            'severity': 'CRITICAL',
            'ml_analysis': {'ml_available': True, 'risk_score': 9.2, 'priority': 1}
        },
        # Format 3: flat fields
        {
            'cve_id': 'CVE-2024-0003',
            'severity': 'HIGH',
            'risk_score': 7.8,
            'priority': 2
        },
    ]
    
    plotter = VulnerabilityPlotter()
    plots = plotter.create_all_plots(test_vulns)
    
    print('\n' + '='*60)
    print('Grafici salvati in: reports/plots/')
    print('='*60)
