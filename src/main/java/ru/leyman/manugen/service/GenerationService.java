package ru.leyman.manugen.service;

import fr.opensagres.xdocreport.converter.ConverterTypeTo;
import fr.opensagres.xdocreport.converter.Options;
import fr.opensagres.xdocreport.document.IXDocReport;
import fr.opensagres.xdocreport.document.registry.XDocReportRegistry;
import fr.opensagres.xdocreport.template.IContext;
import fr.opensagres.xdocreport.template.TemplateEngineKind;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.leyman.manugen.dto.GenerationRequest;

import java.io.*;
import java.util.concurrent.ExecutorService;

@Log4j2
@Service
@RequiredArgsConstructor
public class GenerationService {

    private final ExecutorService executorService;
    private final FileService fileService;

    public void generate(GenerationRequest generationRequest) {
        log.debug("Generating document: template={}, out={}, params={}",
                generationRequest.template(), generationRequest.output(), generationRequest.params());
        executorService.submit(() -> {
            try (var in = fileService.getFile(generationRequest.template());
                 var out = fileService.createFile(generationRequest.output())) {
                IXDocReport report = XDocReportRegistry.getRegistry().loadReport(in, TemplateEngineKind.Freemarker);
                IContext context = report.createContext();
                context.putMap(generationRequest.params());
                Options options = Options.getTo(ConverterTypeTo.PDF);
                report.convert(context, options, out);
                log.debug("Generation completed");
            } catch (Exception e) {
                log.error("Something wrong: {}", e.getMessage());
            }
        });
    }

}
