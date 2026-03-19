package ru.leyman.manugen.generator.service;

import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating device-specific documents (passports, certificates).
 * Maps device codes to templates and generates PDFs with device parameters.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class DeviceDocumentService {

    private final RestTemplate restTemplate;
    
    @Value("${template.service.url:http://template-service:8080}")
    private String templateServiceUrl;
    
    /**
     * Device code to template ID mapping.
     * In production, this should be stored in database.
     */
    private static final Map<String, Long> DEVICE_TEMPLATE_MAP = new HashMap<>();
    
    static {
        // Initialize device code to template ID mapping
        // These should match the templates in the template service
        DEVICE_TEMPLATE_MAP.put("20", 1L);  // TD-500 template
        DEVICE_TEMPLATE_MAP.put("22", 2L);  // TD BLE template  
        DEVICE_TEMPLATE_MAP.put("76", 3L);  // TD-150-BLE template
        // Add more device codes as needed
    }
    
    /**
     * Generate device document based on device parameters
     */
    public byte[] generateDeviceDocument(String deviceCode, String serial, String date, 
                                         String mac, String fullNumber) {
        try {
            log.debug("Generating device document: deviceCode={}, serial={}, date={}, mac={}, fullNumber={}",
                     deviceCode, serial, date, mac, fullNumber);
            
            // 1. Get template ID for device code
            Long templateId = getTemplateIdForDevice(deviceCode);
            if (templateId == null) {
                throw new RuntimeException("No template found for device code: " + deviceCode);
            }
            
            // 2. Fetch template file from template service
            byte[] templateBytes = fetchTemplateFile(templateId);
            
            // 3. Prepare parameters for template
            Map<String, Object> params = prepareDeviceParameters(deviceCode, serial, date, mac, fullNumber);
            
            // 4. Generate PDF from template
            byte[] pdfBytes = generatePdfFromTemplate(templateBytes, params);
            
            log.debug("Device document generation completed for deviceCode={}, size={} bytes", 
                     deviceCode, pdfBytes.length);
            return pdfBytes;
            
        } catch (Exception e) {
            log.error("Failed to generate device document for deviceCode={}: {}", deviceCode, e.getMessage(), e);
            throw new RuntimeException("Device document generation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get template ID for device code
     */
    private Long getTemplateIdForDevice(String deviceCode) {
        Long templateId = DEVICE_TEMPLATE_MAP.get(deviceCode);
        if (templateId == null) {
            log.warn("No template mapping found for device code: {}, using default", deviceCode);
            // Try to find a default template
            templateId = DEVICE_TEMPLATE_MAP.get("default");
        }
        return templateId;
    }
    
    /**
     * Prepare device parameters for template
     */
    private Map<String, Object> prepareDeviceParameters(String deviceCode, String serial, 
                                                        String date, String mac, String fullNumber) {
        Map<String, Object> params = new HashMap<>();
        
        // Basic device info
        params.put("deviceCode", deviceCode);
        params.put("deviceType", getDeviceTypeName(deviceCode));
        
        // Serial number
        if (serial != null) {
            params.put("serial", serial);
            params.put("serialShort", getShortSerial(serial));
        }
        
        // Date
        if (date != null) {
            params.put("date", date);
            params.put("year", extractYear(date));
            params.put("month", extractMonth(date));
        }
        
        // MAC address (for wireless devices)
        if (mac != null) {
            params.put("mac", mac);
            params.put("macFormatted", formatMacAddress(mac));
            params.put("macWithoutColons", mac.replace(":", ""));
        }
        
        // Full number
        if (fullNumber != null) {
            params.put("fullNumber", fullNumber);
        }
        
        // Additional calculated fields
        params.put("generationDate", java.time.LocalDate.now().toString());
        
        log.debug("Prepared device parameters: {}", params);
        return params;
    }
    
    /**
     * Get human-readable device type name
     */
    private String getDeviceTypeName(String deviceCode) {
        switch (deviceCode) {
            case "20": return "ТД-500";
            case "22": return "TD BLE";
            case "76": return "ТД-150-БЛЕ";
            default: return "Устройство " + deviceCode;
        }
    }
    
    /**
     * Extract short serial (last 6 digits)
     */
    private String getShortSerial(String serial) {
        if (serial == null || serial.length() <= 6) {
            return serial;
        }
        return serial.substring(serial.length() - 6);
    }
    
    /**
     * Extract year from date string (YY.MM)
     */
    private String extractYear(String date) {
        if (date == null || date.length() < 5) return "";
        return date.substring(0, 2);
    }
    
    /**
     * Extract month from date string (YY.MM)
     */
    private String extractMonth(String date) {
        if (date == null || date.length() < 5) return "";
        return date.substring(3, 5);
    }
    
    /**
     * Format MAC address consistently
     */
    private String formatMacAddress(String mac) {
        if (mac == null) return "";
        // Ensure MAC is in standard format XX:XX:XX:XX:XX:XX
        String cleanMac = mac.replace(":", "").replace("-", "").replace(".", "").toUpperCase();
        if (cleanMac.length() != 12) {
            return mac; // Return original if not valid
        }
        return String.format("%s:%s:%s:%s:%s:%s",
            cleanMac.substring(0, 2), cleanMac.substring(2, 4),
            cleanMac.substring(4, 6), cleanMac.substring(6, 8),
            cleanMac.substring(8, 10), cleanMac.substring(10, 12));
    }
    
    /**
     * Fetch template file from template service
     */
    private byte[] fetchTemplateFile(Long templateId) {
        try {
            String url = templateServiceUrl + "/files/" + templateId + "/public";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));
            
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, byte[].class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                log.debug("Fetched template file for templateId={}, size={} bytes", 
                         templateId, response.getBody().length);
                return response.getBody();
            } else {
                throw new RuntimeException("Failed to fetch template file: " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Error fetching template file for templateId={}: {}", templateId, e.getMessage());
            throw new RuntimeException("Could not fetch template file: " + e.getMessage(), e);
        }
    }
    
    /**
     * Generate PDF from template bytes using XDocReport
     */
    private byte[] generatePdfFromTemplate(byte[] templateBytes, Map<String, Object> params) {
        try (ByteArrayInputStream templateStream = new ByteArrayInputStream(templateBytes);
             ByteArrayOutputStream pdfStream = new ByteArrayOutputStream()) {
            
            // Load template from bytes
            IXDocReport report = XDocReportRegistry.getRegistry()
                .loadReport(templateStream, TemplateEngineKind.Freemarker);
            
            // Create context and add parameters
            IContext context = report.createContext();
            context.putMap(params);
            
            // Convert to PDF
            Options options = Options.getTo(ConverterTypeTo.PDF);
            report.convert(context, options, pdfStream);
            
            return pdfStream.toByteArray();
            
        } catch (Exception e) {
            log.error("PDF generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("PDF conversion error: " + e.getMessage(), e);
        }
    }
    
    /**
     * Add or update device template mapping (for admin interface)
     */
    public void updateDeviceTemplateMapping(String deviceCode, Long templateId) {
        DEVICE_TEMPLATE_MAP.put(deviceCode, templateId);
        log.info("Updated device template mapping: {} -> {}", deviceCode, templateId);
    }
    
    /**
     * Get all device template mappings (for admin interface)
     */
    public Map<String, Long> getAllDeviceTemplateMappings() {
        return new HashMap<>(DEVICE_TEMPLATE_MAP);
    }
}