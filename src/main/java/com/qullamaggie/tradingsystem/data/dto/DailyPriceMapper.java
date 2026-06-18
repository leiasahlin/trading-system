package com.qullamaggie.tradingsystem.data.dto;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps DailyPrice entities to DailyPriceDto objects.
 */
@Component
public class DailyPriceMapper {
    /**
     * Converts a DailyPrice entity into a DailyPriceDto,
     * exposing only the fields needed by API consumers.
     *
     * @param price the entity to convert
     * @return a DTO containing the OHCLV data and date
     */
    public DailyPriceDto toDto(DailyPrice price) {

        return new DailyPriceDto(price.getDate(),price.getOpen(), price.getHigh()
        ,price.getLow(), price.getClose(), price.getVolume());
    }
}
