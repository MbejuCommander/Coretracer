package dev.coretrace.core;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ResumePolicyTest {
    @Test void stopsAfterFiveAttemptsUntilExplicitReset() {
        var policy = new ResumePolicy();
        for (int n = 0; n < 5; n++) assertTrue(policy.attempt());
        assertTrue(policy.exhausted()); assertFalse(policy.attempt());
        policy.reset(); assertFalse(policy.exhausted()); assertTrue(policy.attempt());
    }
    @Test void delayedNoticeWaitsTwoSecondsAndDoesNotRepeatOrSurviveCancellation() {
        var notice = new RecoveryNotice(); notice.schedule(1000);
        assertFalse(notice.poll(2999)); assertTrue(notice.poll(3000)); assertFalse(notice.poll(4000));
        notice.schedule(5000); notice.cancel(); assertFalse(notice.poll(9000));
        notice.schedule(10000); notice.schedule(11000);
        assertFalse(notice.poll(12000)); assertTrue(notice.poll(13000));
    }
    @Test void unverifiedResultsStopAutomaticAttemptsUntilManualReset() {
        var policy = new ResumePolicy(); assertTrue(policy.attempt()); policy.stop();
        assertFalse(policy.attempt()); policy.reset(); assertTrue(policy.attempt());
    }
    @Test void differentAddressRequiresEnabledOptionAndVerifiableSavedCapture() {
        var config = new Config();
        var capture = new RecoveryStore.Capture("saved", "co l a:sign t:7d", 1, 1737, 6320, config);
        var state = new RecoveryStore.State("old.example", capture, null, "");
        assertTrue(ResumePolicy.allows(state, "new.example", true));
        assertFalse(ResumePolicy.allows(state, "new.example", false));
        assertTrue(ResumePolicy.allows(state, "old.example", false));
        assertFalse(ResumePolicy.allows(state, "", true));
        config.csvSmart = false;
        assertFalse(ResumePolicy.allows(state, "new.example", true));
        config.csvSmart = true;
        assertFalse(ResumePolicy.allows(state.withCapture(capture.at(1, 6320)), "new.example", true));
        assertFalse(ResumePolicy.allows(state.withCapture(null), "new.example", true));
    }
    @Test void olderConfigurationEnablesCrossAddressRecoveryByDefault() {
        assertTrue(new Gson().fromJson("{}", Config.class).resumeAcrossServers);
        assertFalse(new Gson().fromJson("{\"resumeAcrossServers\":false}", Config.class).resumeAcrossServers);
    }
}
