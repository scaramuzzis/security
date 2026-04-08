import json
import random
from datetime import datetime

# Database CVE simulato - nella realtà useremmo API NVD
CVE_DATABASE = [
    # CRITICAL - Punteggio 9.0+
    {
        "cve_id": "CVE-2024-0001",
        "description": "Remote Code Execution in Apache Struts",
        "cvss_score": 10.0,
        "severity": "CRITICAL",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "NONE",
        "user_interaction": "NONE",
        "scope": "CHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "HIGH",
        "availability_impact": "HIGH",
        "published_date": "2024-01-15"
    },
    {
        "cve_id": "CVE-2024-0002",
        "description": "SQL Injection in WordPress Core",
        "cvss_score": 9.8,
        "severity": "CRITICAL",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "NONE",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "HIGH",
        "availability_impact": "HIGH",
        "published_date": "2024-01-20"
    },
    {
        "cve_id": "CVE-2024-0003",
        "description": "Authentication Bypass in OpenSSH",
        "cvss_score": 9.1,
        "severity": "CRITICAL",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "NONE",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "HIGH",
        "availability_impact": "NONE",
        "published_date": "2024-02-01"
    },
    
    # HIGH - Punteggio 7.0-8.9
    {
        "cve_id": "CVE-2024-0010",
        "description": "XSS Vulnerability in React Application",
        "cvss_score": 8.8,
        "severity": "HIGH",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "LOW",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "HIGH",
        "availability_impact": "HIGH",
        "published_date": "2024-02-10"
    },
    {
        "cve_id": "CVE-2024-0011",
        "description": "Buffer Overflow in Linux Kernel",
        "cvss_score": 7.8,
        "severity": "HIGH",
        "attack_vector": "LOCAL",
        "attack_complexity": "LOW",
        "privileges_required": "LOW",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "HIGH",
        "availability_impact": "HIGH",
        "published_date": "2024-02-15"
    },
    {
        "cve_id": "CVE-2024-0012",
        "description": "Directory Traversal in File Upload",
        "cvss_score": 7.5,
        "severity": "HIGH",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "NONE",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "NONE",
        "availability_impact": "NONE",
        "published_date": "2024-02-20"
    },
    
    # MEDIUM - Punteggio 4.0-6.9
    {
        "cve_id": "CVE-2024-0020",
        "description": "Information Disclosure in API Endpoint",
        "cvss_score": 6.5,
        "severity": "MEDIUM",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "LOW",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "HIGH",
        "integrity_impact": "NONE",
        "availability_impact": "NONE",
        "published_date": "2024-03-01"
    },
    {
        "cve_id": "CVE-2024-0021",
        "description": "CSRF in Admin Panel",
        "cvss_score": 5.4,
        "severity": "MEDIUM",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "NONE",
        "user_interaction": "REQUIRED",
        "scope": "UNCHANGED",
        "confidentiality_impact": "LOW",
        "integrity_impact": "LOW",
        "availability_impact": "NONE",
        "published_date": "2024-03-05"
    },
    {
        "cve_id": "CVE-2024-0022",
        "description": "Weak Password Requirements",
        "cvss_score": 4.3,
        "severity": "MEDIUM",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "LOW",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "LOW",
        "integrity_impact": "NONE",
        "availability_impact": "NONE",
        "published_date": "2024-03-10"
    },
    
    # LOW - Punteggio <4.0
    {
        "cve_id": "CVE-2024-0030",
        "description": "Missing Security Headers",
        "cvss_score": 3.7,
        "severity": "LOW",
        "attack_vector": "NETWORK",
        "attack_complexity": "HIGH",
        "privileges_required": "NONE",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "LOW",
        "integrity_impact": "NONE",
        "availability_impact": "NONE",
        "published_date": "2024-03-15"
    },
    {
        "cve_id": "CVE-2024-0031",
        "description": "Verbose Error Messages",
        "cvss_score": 2.7,
        "severity": "LOW",
        "attack_vector": "NETWORK",
        "attack_complexity": "LOW",
        "privileges_required": "HIGH",
        "user_interaction": "NONE",
        "scope": "UNCHANGED",
        "confidentiality_impact": "LOW",
        "integrity_impact": "NONE",
        "availability_impact": "NONE",
        "published_date": "2024-03-20"
    }
]

def collect_vulnerabilities(num_samples=100):
    """
    Raccoglie vulnerabilità dal database CVE simulato
    
    Args:
        num_samples: Numero di campioni da generare
    
    Returns:
        Lista di vulnerabilità
    """
    vulnerabilities = []
    
    # Distribuiamo i campioni in modo realistico
    # 20% CRITICAL, 30% HIGH, 35% MEDIUM, 15% LOW
    num_critical = int(num_samples * 0.20)
    num_high = int(num_samples * 0.30)
    num_medium = int(num_samples * 0.35)
    num_low = num_samples - num_critical - num_high - num_medium
    
    print(f"Generazione dataset con distribuzione:")
    print(f"  CRITICAL: {num_critical}")
    print(f"  HIGH: {num_high}")
    print(f"  MEDIUM: {num_medium}")
    print(f"  LOW: {num_low}")
    
    # Prendi template dal database
    critical_templates = [v for v in CVE_DATABASE if v["severity"] == "CRITICAL"]
    high_templates = [v for v in CVE_DATABASE if v["severity"] == "HIGH"]
    medium_templates = [v for v in CVE_DATABASE if v["severity"] == "MEDIUM"]
    low_templates = [v for v in CVE_DATABASE if v["severity"] == "LOW"]
    
    # Genera variazioni
    counter = 1000
    
    for _ in range(num_critical):
        template = random.choice(critical_templates)
        vuln = template.copy()
        vuln["cve_id"] = f"CVE-2024-{counter}"
        # Aggiungi piccole variazioni al punteggio
        vuln["cvss_score"] = round(random.uniform(9.0, 10.0), 1)
        counter += 1
        vulnerabilities.append(vuln)
    
    for _ in range(num_high):
        template = random.choice(high_templates)
        vuln = template.copy()
        vuln["cve_id"] = f"CVE-2024-{counter}"
        vuln["cvss_score"] = round(random.uniform(7.0, 8.9), 1)
        counter += 1
        vulnerabilities.append(vuln)
    
    for _ in range(num_medium):
        template = random.choice(medium_templates)
        vuln = template.copy()
        vuln["cve_id"] = f"CVE-2024-{counter}"
        vuln["cvss_score"] = round(random.uniform(4.0, 6.9), 1)
        counter += 1
        vulnerabilities.append(vuln)
    
    for _ in range(num_low):
        template = random.choice(low_templates)
        vuln = template.copy()
        vuln["cve_id"] = f"CVE-2024-{counter}"
        vuln["cvss_score"] = round(random.uniform(0.1, 3.9), 1)
        counter += 1
        vulnerabilities.append(vuln)
    
    # Mescola per evitare ordinamento per gravità
    random.shuffle(vulnerabilities)
    
    return vulnerabilities

def save_dataset(vulnerabilities, filename="data/cve_dataset.json"):
    """
    Salva il dataset in formato JSON
    
    Args:
        vulnerabilities: Lista di vulnerabilità
        filename: Nome file di destinazione
    """
    with open(filename, 'w') as f:
        json.dump(vulnerabilities, f, indent=2)
    
    print(f"\n✅ Dataset salvato in {filename}")
    print(f"   Total vulnerabilities: {len(vulnerabilities)}")

if __name__ == "__main__":
    # Genera dataset di 100 vulnerabilità
    print("📊 Raccolta vulnerabilità CVE...")
    vulnerabilities = collect_vulnerabilities(num_samples=100)
    
    # Salva il dataset
    save_dataset(vulnerabilities)
    
    print("\n📈 Statistiche dataset:")
    severity_counts = {}
    for vuln in vulnerabilities:
        sev = vuln["severity"]
        severity_counts[sev] = severity_counts.get(sev, 0) + 1
    
    for severity, count in sorted(severity_counts.items()):
        print(f"  {severity}: {count}")

