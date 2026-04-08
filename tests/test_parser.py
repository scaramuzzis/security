'''
Unit Tests for XML Parser
'''

import pytest
import os
from src.parser.xml_parser import NmapXMLParser
from src.scanner.nmap_wrapper import NmapScanner


class TestNmapXMLParser:
    '''Test suite for XML Parser'''
    
    @pytest.fixture
    def sample_xml_file(self):
        '''Create sample XML file for testing'''
        scanner = NmapScanner()
        xml_file = 'test_parser_sample.xml'
        scanner.scan_to_xml('127.0.0.1', xml_file, '-F')
        yield xml_file
        # Cleanup
        if os.path.exists(xml_file):
            os.remove(xml_file)
    
    def test_parser_initialization(self, sample_xml_file):
        '''Test parser initializes correctly'''
        parser = NmapXMLParser(sample_xml_file)
        assert parser is not None
        assert parser.xml_file == sample_xml_file
        assert parser.root is not None
    
    def test_get_scan_info(self, sample_xml_file):
        '''Test scan info extraction'''
        parser = NmapXMLParser(sample_xml_file)
        info = parser.get_scan_info()
        
        assert isinstance(info, dict)
        assert 'scanner' in info
        assert 'args' in info
        assert info['scanner'] == 'nmap'
    
    def test_parse_hosts(self, sample_xml_file):
        '''Test host parsing'''
        parser = NmapXMLParser(sample_xml_file)
        hosts = parser.parse_all_hosts()
        
        assert isinstance(hosts, list)
        assert len(hosts) > 0
        
        # Check first host structure
        host = hosts[0]
        assert 'status' in host
        assert 'addresses' in host
        assert 'ports' in host
    
    def test_parse_complete(self, sample_xml_file):
        '''Test complete parsing'''
        parser = NmapXMLParser(sample_xml_file)
        data = parser.parse_complete()
        
        assert isinstance(data, dict)
        assert 'scan_info' in data
        assert 'hosts' in data
    
    def test_invalid_xml_file(self):
        '''Test with non-existent file'''
        with pytest.raises(Exception):
            NmapXMLParser('nonexistent.xml')


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
