package com.bowon.cpm.market.service;

import com.bowon.cpm.market.domain.StockIndicatorDaily;
import com.bowon.cpm.market.domain.StockPriceDaily;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class TechnicalIndicatorCalculator {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public StockIndicatorDaily calculateLatest(String stockCode, List<StockPriceDaily> prices) {
        if (prices == null || prices.isEmpty()) {
            return null;
        }

        List<StockPriceDaily> ordered = prices.stream()
                .filter(p -> p.getClosePrice() != null && p.getTradeDate() != null)
                .sorted(Comparator.comparing(StockPriceDaily::getTradeDate))
                .toList();
        if (ordered.isEmpty()) {
            return null;
        }

        int last = ordered.size() - 1;
        StockPriceDaily latest = ordered.get(last);
        List<BigDecimal> closes = ordered.stream().map(StockPriceDaily::getClosePrice).toList();

        MacdValues macd = calculateMacd(closes);
        BollingerValues bollinger = calculateBollinger(closes, 20);

        return StockIndicatorDaily.builder()
                .stockCode(stockCode)
                .tradeDate(latest.getTradeDate())
                .ma5(movingAverage(closes, 5))
                .ma20(movingAverage(closes, 20))
                .ma60(movingAverage(closes, 60))
                .ma120(movingAverage(closes, 120))
                .rsi14(calculateRsi(closes, 14))
                .macd(macd.macd())
                .macdSignal(macd.signal())
                .macdHistogram(macd.histogram())
                .bollingerUpper(bollinger.upper())
                .bollingerMiddle(bollinger.middle())
                .bollingerLower(bollinger.lower())
                .volatility(calculateReturnVolatility(closes, 20))
                .volumeChangeRate(calculateVolumeChangeRate(ordered, 20))
                .build();
    }

    private BigDecimal movingAverage(List<BigDecimal> values, int period) {
        if (values.size() < period) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = values.size() - period; i < values.size(); i++) {
            sum = sum.add(values.get(i));
        }
        return scalePrice(sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP));
    }

    private BigDecimal calculateRsi(List<BigDecimal> closes, int period) {
        if (closes.size() <= period) {
            return null;
        }
        BigDecimal gain = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            BigDecimal diff = closes.get(i).subtract(closes.get(i - 1));
            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                gain = gain.add(diff);
            } else {
                loss = loss.add(diff.abs());
            }
        }
        if (loss.compareTo(BigDecimal.ZERO) == 0) {
            return gain.compareTo(BigDecimal.ZERO) == 0 ? new BigDecimal("50.0000") : new BigDecimal("100.0000");
        }
        if (gain.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal rs = gain.divide(loss, 8, RoundingMode.HALF_UP);
        return HUNDRED.subtract(HUNDRED.divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private MacdValues calculateMacd(List<BigDecimal> closes) {
        if (closes.size() < 35) {
            return MacdValues.empty();
        }
        List<BigDecimal> ema12 = emaSeries(closes, 12);
        List<BigDecimal> ema26 = emaSeries(closes, 26);
        List<BigDecimal> macdSeries = new ArrayList<>();
        for (int i = 0; i < closes.size(); i++) {
            if (ema12.get(i) != null && ema26.get(i) != null) {
                macdSeries.add(ema12.get(i).subtract(ema26.get(i)));
            }
        }
        if (macdSeries.size() < 9) {
            return MacdValues.empty();
        }
        List<BigDecimal> signalSeries = emaSeries(macdSeries, 9);
        BigDecimal macd = macdSeries.get(macdSeries.size() - 1);
        BigDecimal signal = signalSeries.get(signalSeries.size() - 1);
        if (signal == null) {
            return MacdValues.empty();
        }
        return new MacdValues(
                macd.setScale(6, RoundingMode.HALF_UP),
                signal.setScale(6, RoundingMode.HALF_UP),
                macd.subtract(signal).setScale(6, RoundingMode.HALF_UP)
        );
    }

    private List<BigDecimal> emaSeries(List<BigDecimal> values, int period) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal multiplier = BigDecimal.valueOf(2)
                .divide(BigDecimal.valueOf(period + 1L), 12, RoundingMode.HALF_UP);
        BigDecimal ema = null;
        BigDecimal seedSum = BigDecimal.ZERO;
        for (int i = 0; i < values.size(); i++) {
            BigDecimal value = values.get(i);
            if (i < period - 1) {
                seedSum = seedSum.add(value);
                result.add(null);
                continue;
            }
            if (i == period - 1) {
                seedSum = seedSum.add(value);
                ema = seedSum.divide(BigDecimal.valueOf(period), 12, RoundingMode.HALF_UP);
            } else {
                ema = value.subtract(ema).multiply(multiplier).add(ema);
            }
            result.add(ema);
        }
        return result;
    }

    private BollingerValues calculateBollinger(List<BigDecimal> closes, int period) {
        BigDecimal middle = movingAverage(closes, period);
        if (middle == null) {
            return BollingerValues.empty();
        }
        BigDecimal variance = BigDecimal.ZERO;
        for (int i = closes.size() - period; i < closes.size(); i++) {
            BigDecimal diff = closes.get(i).subtract(middle);
            variance = variance.add(diff.multiply(diff));
        }
        BigDecimal stddev = sqrt(variance.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP));
        BigDecimal band = stddev.multiply(BigDecimal.valueOf(2));
        return new BollingerValues(
                scalePrice(middle.add(band)),
                middle,
                scalePrice(middle.subtract(band))
        );
    }

    private BigDecimal calculateReturnVolatility(List<BigDecimal> closes, int period) {
        if (closes.size() <= period) {
            return null;
        }
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = closes.size() - period; i < closes.size(); i++) {
            BigDecimal prev = closes.get(i - 1);
            if (prev.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            returns.add(closes.get(i).subtract(prev)
                    .divide(prev, 10, RoundingMode.HALF_UP));
        }
        if (returns.isEmpty()) {
            return null;
        }
        BigDecimal mean = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size()), 10, RoundingMode.HALF_UP);
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal r : returns) {
            BigDecimal diff = r.subtract(mean);
            variance = variance.add(diff.multiply(diff));
        }
        BigDecimal stddev = sqrt(variance.divide(BigDecimal.valueOf(returns.size()), 10, RoundingMode.HALF_UP));
        return stddev.multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVolumeChangeRate(List<StockPriceDaily> prices, int period) {
        if (prices.size() <= period || prices.get(prices.size() - 1).getVolume() == null) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        int count = 0;
        for (int i = prices.size() - period - 1; i < prices.size() - 1; i++) {
            Long volume = prices.get(i).getVolume();
            if (volume != null) {
                sum = sum.add(BigDecimal.valueOf(volume));
                count++;
            }
        }
        if (count == 0) {
            return null;
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(count), 8, RoundingMode.HALF_UP);
        if (avg.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        BigDecimal current = BigDecimal.valueOf(prices.get(prices.size() - 1).getVolume());
        return current.subtract(avg).divide(avg, 8, RoundingMode.HALF_UP)
                .multiply(HUNDRED)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal scalePrice(BigDecimal value) {
        return value != null ? value.setScale(2, RoundingMode.HALF_UP) : null;
    }

    private BigDecimal sqrt(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(Math.sqrt(value.doubleValue()));
    }

    private record MacdValues(BigDecimal macd, BigDecimal signal, BigDecimal histogram) {
        static MacdValues empty() {
            return new MacdValues(null, null, null);
        }
    }

    private record BollingerValues(BigDecimal upper, BigDecimal middle, BigDecimal lower) {
        static BollingerValues empty() {
            return new BollingerValues(null, null, null);
        }
    }
}
