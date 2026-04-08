
'''
Unit Tests for Nmap Scanner
'''

import pytest
import os
from src.scanner.nmap_wrapper import NmapScanner


class TestNmapScanner:
    '''Test suite for NmapScanner'''
    
    def setup_method(self):
        '''Setup before each test'''
        self.scanner = NmapScanner()
    
    def test_initialization(self):
        '''Test scanner initializes correctly'''
        assert self.scanner is not None
        assert self.scanner.nmap_path == 'nmap'
    
    def test_scan_localhost(self):
        '''Test scan of localhost'''
        result = self.scanner.scan('127.0.0.1', '-F')
        
        assert result is not None
        assert isinstance(result, dict)
        assert 'success' in result
        assert 'target' in result
        assert result['target'] == '127.0.0.1'
    
    def test_scan_success(self):
        '''Test successful scan returns expected keys'''
        result = self.scanner.scan('127.0.0.1', '-F')
        
        assert result['success'] is True
        assert 'stdout' in result
        assert 'stderr' in result
        assert 'returncode' in result
    
    def test_scan_to_xml(self):
        '''Test XML output generation'''
        output_file = 'test_output.xml'
        
        # Clean up if exists
        if os.path.exists(output_file):
            os.remove(output_file)
        
        result = self.scanner.scan_to_xml('127.0.0.1', output_file, '-F')
        
        assert result['success'] is True
        assert result['file_exists'] is True
        assert os.path.exists(output_file)
        
        # Cleanup
        os.remove(output_file)
    
    def test_invalid_target(self):
        '''Test scan with invalid target'''
        result = self.scanner.scan('999.999.999.999', '-F')
        
        # Should still return dict but may not be success
        assert isinstance(result, dict)
        assert 'success' in result
        assert 'target' in result


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
