package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.List;

/** Immutable world-generation rules loaded from the default datapack profile. */
public record CityGenerationSettings(int regionChanceDenominator, List<WeightedSize> citySizes,
                                     int roadSampleSpacing, int roadReliefLimit, int subgradeDepth) {
    private static volatile CityGenerationSettings current = defaults();

    public record WeightedSize(int chunks, int weight) {
        public WeightedSize {
            if (chunks < CityPlan.SIZE || chunks > CityPlan.MAX_SIZE || (chunks & 1) != 0)
                throw new IllegalArgumentException("City size must be even and between 8 and 28 chunks");
            if (weight < 1 || weight > 1000)
                throw new IllegalArgumentException("City size weight must be between 1 and 1000");
        }
    }

    public CityGenerationSettings {
        citySizes = List.copyOf(citySizes);
        if (regionChanceDenominator < 1 || regionChanceDenominator > 128)
            throw new IllegalArgumentException("Region chance denominator must be between 1 and 128");
        if (citySizes.isEmpty() || citySizes.size() > 16
                || citySizes.stream().mapToInt(WeightedSize::weight).sum() > 10000)
            throw new IllegalArgumentException("Provide 1 to 16 weighted city sizes with total weight <= 10000");
        if (roadSampleSpacing < 2 || roadSampleSpacing > 32
                || (roadSampleSpacing & (roadSampleSpacing - 1)) != 0)
            throw new IllegalArgumentException("Road sample spacing must be a power of two from 2 to 32");
        if (roadReliefLimit < 0 || roadReliefLimit > 32)
            throw new IllegalArgumentException("Road relief limit must be between 0 and 32");
        if (subgradeDepth < 1 || subgradeDepth > 32)
            throw new IllegalArgumentException("Subgrade depth must be between 1 and 32");
    }

    public int selectSize(long value) {
        int total = citySizes.stream().mapToInt(WeightedSize::weight).sum();
        long choice = Long.remainderUnsigned(value, total);
        for (WeightedSize size : citySizes) {
            choice -= size.weight();
            if (choice < 0) return size.chunks();
        }
        throw new IllegalStateException("Invalid city size weights");
    }

    public static CityGenerationSettings defaults() {
        return new CityGenerationSettings(6, List.of(
                new WeightedSize(12, 1), new WeightedSize(16, 2),
                new WeightedSize(20, 3), new WeightedSize(24, 2),
                new WeightedSize(28, 1)), 4, 12, 8);
    }

    public static CityGenerationSettings current() { return current; }

    static void install(CityGenerationSettings settings) { current = settings; }
}
