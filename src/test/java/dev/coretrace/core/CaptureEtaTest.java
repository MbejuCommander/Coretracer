package dev.coretrace.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CaptureEtaTest {
    @Test void learnsRealPageIntervalsAndFormatsHours() {
        var e = new CaptureEta(); assertEquals(-1, e.seconds(0, 100));
        e.page(1000); e.page(2000); assertEquals(-1, e.seconds(2, 100));
        e.page(3000); assertEquals(97, e.seconds(3, 100));
        assertEquals("01:23:45", CaptureEta.time(5025));
        assertEquals("100:00:00", CaptureEta.time(360000));
    }
    @Test void recoveryAndPausesDiscardTimingSamplesAndNewTotalsChangeEta() {
        var e = new CaptureEta(); e.page(1000); e.page(2000); e.page(3000);
        assertEquals(10, e.seconds(10, 20)); assertEquals(15, e.seconds(10, 25));
        e.interrupt(); assertEquals(-1, e.seconds(10, 25));
        e.page(100000); e.page(102000); e.page(104000); assertEquals(30, e.seconds(10, 25));
    }
    @Test void onlySuccessCanDisplay100Percent() {
        assertEquals(0, CaptureEta.percent(0, 0, false));
        assertEquals(67, CaptureEta.percent(67, 100, false));
        assertEquals(99, CaptureEta.percent(100, 100, false));
        assertEquals(100, CaptureEta.percent(100, 100, true));
        assertEquals(99, CaptureEta.percent(Integer.MAX_VALUE, 1, false));
    }
}
