package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.List;

/** Immutable world-generation rules loaded from the default datapack profile. */
public record CityGenerationSettings(int regionChanceDenominator, List<WeightedSize> citySizes,
                                     int roadSampleSpacing, int roadReliefLimit, int subgradeDepth,
                                     CityPlanning planning) {
    private static volatile CityGenerationSettings current = defaults();

    /** Controls the city-wide street graph and the scale of district clusters. */
    public record CityPlanning(int branchSpacing, int branchLength, int roadCoveragePercent,
                               int civicRadiusPercent, int industrialRadiusPercent) {
        public CityPlanning {
            if (branchSpacing < 3 || branchSpacing > 16 || branchLength < 2 || branchLength > 24)
                throw new IllegalArgumentException("Branch spacing/length out of range");
            if (roadCoveragePercent < 15 || roadCoveragePercent > 40
                    || civicRadiusPercent < 10 || civicRadiusPercent > 45
                    || industrialRadiusPercent < 10 || industrialRadiusPercent > 45)
                throw new IllegalArgumentException("Road coverage or district radius out of range");
        }
    }

    public CityGenerationSettings(int regionChanceDenominator, List<WeightedSize> citySizes,
                                  int roadSampleSpacing, int roadReliefLimit, int subgradeDepth) {
        this(regionChanceDenominator, citySizes, roadSampleSpacing, roadReliefLimit, subgradeDepth,
                new CityPlanning(6, 7, 28, 23, 29));
    }

    public record WeightedSize(int chunks, int weight) {
        public WeightedSize {
            if (chunks < CityPlan.SIZE || chunks > CityPlan.MAX_SIZE || (chunks & 1) != 0)
                throw new IllegalArgumentException("City size must be even and between 8 and 30 chunks");
            if (weight < 1 || weight > 1000)
                throw new IllegalArgumentException("City size weight must be between 1 and 1000");
        }
    }

    public CityGenerationSettings {
        citySizes = List.copyOf(citySizes);
        if (planning == null) throw new IllegalArgumentException("Missing city planning settings");
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
                new WeightedSize(16, 1), new WeightedSize(20, 2),
                new WeightedSize(24, 3), new WeightedSize(28, 2),
                new WeightedSize(30, 1)), 4, 12, 8);
    }

    public static CityGenerationSettings current() { return current; }

    static void install(CityGenerationSettings settings) { current = settings; }
}
