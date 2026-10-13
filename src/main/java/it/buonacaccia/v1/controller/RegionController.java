package it.buonacaccia.v1.controller;

import java.util.Arrays;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.buonacaccia.model.Region;
import it.buonacaccia.v1.dto.ApiResponse;
import it.buonacaccia.v1.dto.RegionDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/regions")
@Tag(name = "regions", description = "Lista fissa delle regioni AGESCI")
public class RegionController {

    @GetMapping
    @Operation(summary = "Regioni AGESCI con sigla e nome (FR-022)")
    public ApiResponse<List<RegionDTO>> findAll() {
        return ApiResponse.ok(Arrays.stream(Region.values())
                .map(region -> new RegionDTO(region.name(), region.label()))
                .toList());
    }
}
