package com.langqi.fakegps.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FormatsTest {

    @Test
    public void kmKeepsTwoDecimals() {
        assertEquals("3.09", Formats.km(3090.0));
        assertEquals("3.10", Formats.km(3099.6));
    }

    @Test
    public void distanceSwitchesUnitAtOneKilometer() {
        assertEquals("850 米", Formats.distance(850.4));
        assertEquals("3.09 公里", Formats.distance(3090.0));
    }

    @Test
    public void durationFormatsMinutesAndHours() {
        assertEquals("17:00", Formats.duration(17 * 60 * 1000L));
        assertEquals("1:02:03", Formats.duration(3723 * 1000L));
        assertEquals("00:00", Formats.duration(-5L));
    }

    @Test
    public void paceFormatsSecondsPerKilometer() {
        assertEquals("5'30\"", Formats.paceFromSecondsPerKm(330.0));
        assertEquals("4'00\"", Formats.paceFromSecondsPerKm(240.0));
        assertEquals("--'--\"", Formats.paceFromSecondsPerKm(0.0));
    }

    @Test
    public void paceAndSpeedConversionRoundTrips() {
        assertEquals(3.0303, Formats.speedFromPaceSeconds(330.0), 1e-4);
        assertEquals("5'30\"", Formats.paceFromSpeed(Formats.speedFromPaceSeconds(330.0)));
    }

    @Test
    public void averagePaceMatchesScreenshotRun() {
        // 3.09 公里用时 16:57 -> 5'29"
        assertEquals("5'29\"", Formats.averagePace(16 * 60 * 1000L + 57 * 1000L, 3090.0));
    }

    @Test
    public void percentRoundsAndClamps() {
        assertEquals("46%", Formats.percent(0.456));
        assertEquals("100%", Formats.percent(1.4));
        assertEquals("0%", Formats.percent(-0.2));
    }

    @Test
    public void progressShowsDoneAndTarget() {
        assertEquals("1.20 / 3.10 公里", Formats.progress(1200.0, 3100.0));
    }
}
