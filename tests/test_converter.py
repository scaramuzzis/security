'''
Unit Tests for JSON Converter
'''

import pytest
import json
import os
from src.parser.json_converter import NmapJSONConverter
from src.parser.xml_parser import NmapXMLParser
from src.scanner.nmap_wrapper import NmapScanner


class TestNmapJSONConverter:
    '''Test suite for JSON Converter'''
    
    @pytest.fixture
    def sample_parsed_data(self):
        '''Create sample parsed data'''
        scanner = NmapScanner()
        xml_file = 'test_conv_sample.xml'
        scanner.scan_to_xml('127.0.0.1', xml_file, '-F')
        
        parser = NmapXMLParser(xml_file)
        data = parser.parse_complete()
        
        yield data
        
        # Cleanup
        if os.path.exists(xml_file):
            os.remove(xml_file)
    
    def test_converter_initialization(self, sample_parsed_data):
        '''Test converter initializes'''
        converter = NmapJSONConverter(sample_parsed_data)
        assert converter is not None
        assert converter.parsed_data is not None
    
    def test_to_json_string(self, sample_parsed_data):
        '''Test JSON string generation'''
        converter = NmapJSONConverter(sample_parsed_data)
        json_str = converter.to_json_string()
        
        assert isinstance(json_str, str)
        assert len(json_str) > 0
        
        # Verify valid JSON
        parsed = json.loads(json_str)
        assert isinstance(parsed, dict)
    
    def test_to_json_file(self, sample_parsed_data):
        '''Test JSON file creation'''
        converter = NmapJSONConverter(sample_parsed_data)
        json_file = 'test_output.json'
        
        # Clean if exists
        if os.path.exists(json_file):
            os.remove(json_file)
        
        converter.to_json_file(json_file)
        
        assert os.path.exists(json_file)
        
        # Verify content
        with open(json_file, 'r') as f:
            data = json.load(f)
            assert isinstance(data, dict)
        
        # Cleanup
        os.remove(json_file)
    
    def test_get_summary(self, sample_parsed_data):
        '''Test summary generation'''
        converter = NmapJSONConverter(sample_parsed_data)
        summary = converter.get_summary()
        
        assert isinstance(summary, dict)
        assert 'total_hosts' in summary
        assert 'hosts_up' in summary
        assert 'total_open_ports' in summary
        assert 'unique_services' in summary
        
        assert isinstance(summary['total_hosts'], int)
        assert isinstance(summary['unique_services'], list)


if __name__ == '__main__':
    pytest.main([__file__, '-v'])
