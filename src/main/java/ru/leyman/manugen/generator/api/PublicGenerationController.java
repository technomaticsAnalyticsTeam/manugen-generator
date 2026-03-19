package ru.leyman.manugen.generator.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.leyman.manugen.generator.service.DeviceDocumentService;

import java.util.Map;

/**
 * Public controller for generating device documents via QR codes.
 * No authentication required - for public access.
 *
 * URL format: /m/{deviceCode}?s={serial}&d={date}&m={mac}&//{fullNumber}
 * Example: /m/22?s=556925&d=24.03&m=ED:8E:A0:BE:9E:C9&//403163556925
 */
@Log4j2
@RestController
@RequestMapping
@RequiredArgsConstructor
public class PublicGenerationController {

    private final DeviceDocumentService deviceDocumentService;

    /**
     * Device document generation endpoint (new format).
     * Example: /m/22?s=556925&d=24.03&m=ED:8E:A0:BE:9E:C9&//403163556925
     *
     * @param deviceCode Device type code (e.g., 22 for TD BLE, 20 for TD-500, 76 for TD-150-BLE)
     * @param s Serial number (6 digits)
     * @param d Date in format YY.MM (year.month)
     * @param m MAC address (for wireless devices)
     * @param fullNumber Full device number (after // in URL)
     * @param allParams All query parameters
     */
    @GetMapping("m/{deviceCode}")
    public ResponseEntity<byte[]> generateDeviceDocument(
            @PathVariable String deviceCode,
            @RequestParam(name = "s", required = false) String serial,
            @RequestParam(name = "d", required = false) String date,
            @RequestParam(name = "m", required = false) String mac,
            @RequestParam Map<String, String> allParams) {
        
        try {
            log.debug("Device document generation: deviceCode={}, s={}, d={}, m={}, allParams={}",
                     deviceCode, serial, date, mac, allParams);
            
            // Extract full number from parameters (comes as //{number} in URL)
            String fullNumber = extractFullNumber(allParams);
            
            // Generate device document
            byte[] pdfBytes = deviceDocumentService.generateDeviceDocument(
                deviceCode, serial, date, mac, fullNumber);
            
            // Generate filename based on device parameters
            String filename = generateFilename(deviceCode, serial, date, mac);
            
            // Return PDF as downloadable file
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("Device document generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Legacy static file format support for backward compatibility.
     * Example: /static/20_260266_22.11__.pdf
     * Example: /static/76_105137_22.11_F3:50:6F:0D:72:59__.pdf
     */
    @GetMapping("static/{filename:.+\\.pdf}")
    public ResponseEntity<byte[]> generateLegacyDocument(@PathVariable String filename) {
        try {
            log.debug("Legacy document generation: filename={}", filename);
            
            // Parse filename to extract device parameters
            DeviceParameters params = parseLegacyFilename(filename);
            
            // Generate document
            byte[] pdfBytes = deviceDocumentService.generateDeviceDocument(
                params.deviceCode, params.serial, params.date, params.mac, null);
            
            // Return PDF
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", filename);
            headers.setContentLength(pdfBytes.length);
            
            return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
            
        } catch (Exception e) {
            log.error("Legacy document generation failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Health check endpoint for public access
     */
    @GetMapping("health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Device document generator service is running");
    }

    /**
     * Extract full number from parameters (parameter name is "//" which Spring doesn't support)
     */
    private String extractFullNumber(Map<String, String> allParams) {
        // The full number comes as a parameter with key "//" in the URL
        // Spring doesn't handle "//" as a parameter name well, so we need to parse it differently
        // For now, we'll look for a parameter that starts with //
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith("//")) {
                return entry.getKey().substring(2); // Remove // prefix
            }
            if (entry.getValue() != null && entry.getValue().startsWith("//")) {
                return entry.getValue().substring(2);
            }
        }
        return null;
    }

    /**
     * Generate filename in legacy format for download
     */
    private String generateFilename(String deviceCode, String serial, String date, String mac) {
        StringBuilder filename = new StringBuilder();
        filename.append(deviceCode).append("_");
        
        if (serial != null) {
            // Take last 6 digits if serial is longer
            String shortSerial = serial.length() > 6 ? serial.substring(serial.length() - 6) : serial;
            filename.append(shortSerial).append("_");
        }
        
        if (date != null) {
            filename.append(date).append("_");
        }
        
        if (mac != null) {
            // Format MAC address without colons for filename
            String macFilename = mac.replace(":", "");
            filename.append(macFilename).append("_");
        }
        
        filename.append("_.pdf");
        return filename.toString();
    }

    /**
     * Parse legacy filename to extract device parameters
     */
    private DeviceParameters parseLegacyFilename(String filename) {
        // Remove .pdf extension
        String baseName = filename.replace(".pdf", "");
        String[] parts = baseName.split("_");
        
        DeviceParameters params = new DeviceParameters();
        
        if (parts.length > 0) {
            params.deviceCode = parts[0];
        }
        if (parts.length > 1) {
            params.serial = parts[1];
        }
        if (parts.length > 2) {
            params.date = parts[2];
        }
        if (parts.length > 3 && !parts[3].isEmpty()) {
            // MAC address might be in parts[3]
            String macPart = parts[3];
            if (macPart.length() == 12) { // MAC without colons
                // Format as MAC address with colons
                params.mac = String.format("%s:%s:%s:%s:%s:%s",
                    macPart.substring(0, 2), macPart.substring(2, 4),
                    macPart.substring(4, 6), macPart.substring(6, 8),
                    macPart.substring(8, 10), macPart.substring(10, 12));
            }
        }
        
        return params;
    }

    /**
     * Helper class for device parameters
     */
    private static class DeviceParameters {
        String deviceCode;
        String serial;
        String date;
        String mac;
    }
}