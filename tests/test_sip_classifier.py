from backend.sip_classifier import clean_operator_name, is_sip_provider, is_b2b_sip_pool

def test_clean_operator_name():
    assert clean_operator_name('ООО "Манго Телеком"') == "Манго Телеком"
    assert clean_operator_name('ПАО "Ростелеком"') == "Ростелеком"
    assert clean_operator_name('АО "МТТ"') == "МТТ"
    assert clean_operator_name('ЗАО «Телфин»') == "Телфин"

def test_is_sip_provider():
    assert is_sip_provider("ООО Манго Телеком") is True
    assert is_sip_provider('АО "Межрегиональный ТранзитТелеком"') is True
    assert is_sip_provider("АО МТТ") is True
    assert is_sip_provider("Телфин") is True
    assert is_sip_provider("ООО Сипнет") is True
    assert is_sip_provider("ПАО МТС") is False
    assert is_sip_provider("ПАО МегаФон") is False

def test_is_b2b_sip_pool():
    # SIP provider with small pool (<= 10,000)
    assert is_b2b_sip_pool("ООО Манго Телеком", 100) is True
    assert is_b2b_sip_pool("АО МТТ", 1000) is True
    # Standard federal operator with small pool -> not tagged as SIP
    assert is_b2b_sip_pool("ПАО МТС", 100) is False
    # SIP provider with giant pool (> 10,000) -> not classified as small B2B pool
    assert is_b2b_sip_pool("АО МТТ", 500_000) is False
