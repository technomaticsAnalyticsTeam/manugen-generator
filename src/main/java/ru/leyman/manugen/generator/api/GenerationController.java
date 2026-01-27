package ru.leyman.manugen.generator.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ru.leyman.manugen.generator.dto.GenerationRequest;
import ru.leyman.manugen.generator.service.GenerationService;

@RestController
@RequestMapping("gen")
@RequiredArgsConstructor
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping
    public void generate(@RequestBody GenerationRequest request) {
        generationService.generate(request);
    }

}
