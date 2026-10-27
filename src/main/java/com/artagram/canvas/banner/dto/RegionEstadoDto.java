package com.artagram.canvas.banner.dto;

import java.util.List;

public record RegionEstadoDto(
        int regionIndex,
        String snapshotPngUrl,
        String snapshotPngBase64,
        List<BannerTrazoEventoDto> trazosDesdeSnapshot
) {
}
