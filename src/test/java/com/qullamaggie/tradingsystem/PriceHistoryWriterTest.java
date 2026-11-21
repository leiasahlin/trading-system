package com.qullamaggie.tradingsystem;

import com.qullamaggie.tradingsystem.data.entity.DailyPrice;
import com.qullamaggie.tradingsystem.data.entity.Stock;
import com.qullamaggie.tradingsystem.data.repository.DailyPriceRepository;
import com.qullamaggie.tradingsystem.data.service.PriceHistoryWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PriceHistoryWriterTest {

    @Mock
    private DailyPriceRepository dailyPriceRepository;

    @Test
    public void shouldDeleteOldHistoryBeforeSavingNew() {
        PriceHistoryWriter writer = new PriceHistoryWriter(dailyPriceRepository);
        Stock stock = new Stock();
        List<DailyPrice> history = List.of(new DailyPrice(), new DailyPrice());
        when(dailyPriceRepository.deleteByStock(stock)).thenReturn(250);

        int deleted = writer.replaceHistory(stock, history);

        // Order matters: saving first would collide with the old rows
        // on the unique (stock_id, date) constraint
        InOrder order = inOrder(dailyPriceRepository);
        order.verify(dailyPriceRepository).deleteByStock(stock);
        order.verify(dailyPriceRepository).saveAll(history);
        assertEquals(250, deleted);
    }
}