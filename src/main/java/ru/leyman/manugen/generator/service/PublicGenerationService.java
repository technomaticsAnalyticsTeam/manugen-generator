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
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for public document generation.
 * Handles fetching templates from template service and generating PDFs.
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PublicGenerationService {

    private final RestTemplate restTemplate;
    
    @Value("${template.service.url:http://template-service:8080}")
    private String templateServiceUrl;
    
    @Value("${public.generation.timeout:30000}")
    private int generationTimeout;
    
    /**
     * Generate PDF from template ID and parameters
     */
    public byte[] generatePdf(Long templateId, Map<String, Object> params) {
        try {
            log.debug("Starting public PDF generation for templateId={}", templateId);
            
            // 1. Fetch template file from template service
            byte[] templateBytes = fetchTemplateFile(templateId);
            
            // 2. Generate PDF from template
            byte[] pdfBytes = generatePdfFromTemplate(templateBytes, params);
            
            log.debug("Public PDF generation completed for templateId={}, size={} bytes", 
                     templateId, pdfBytes.length);
            return pdfBytes;
            
        } catch (Exception e) {
            log.error("Failed to generate PDF for templateId={}: {}", templateId, e.getMessage(), e);
            throw new RuntimeException("PDF generation failed: " + e.getMessage(), e);
        }
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
     * Validate template parameters against template schema
     * (To be implemented when template metadata is available)
     */
    public void validateParameters(Long templateId, Map<String, Object> params) {
        // TODO: Fetch template metadata from template service and validate required fields
        log.debug("Parameter validation for templateId={}, params={}", templateId, params);
    }
}