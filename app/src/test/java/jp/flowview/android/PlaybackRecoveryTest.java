package jp.flowview.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackRecoveryTest {
    @Test public void pausedBeforeTransitionDoesNotAutoplay(){
        PlaybackRecovery r=new PlaybackRecovery();int t=r.begin(1000,false);assertFalse(r.takeAttempt(t,1200,false));
    }
    @Test public void recoveryIsBoundedAndDoesNothingWhilePlaying(){
        PlaybackRecovery r=new PlaybackRecovery();int t=r.begin(1000,true);
        assertFalse(r.takeAttempt(t,1200,true));assertTrue(r.takeAttempt(t,1800,false));assertTrue(r.takeAttempt(t,2600,false));assertFalse(r.takeAttempt(t,2700,false));
        t=r.begin(3000,true);assertFalse(r.takeAttempt(t,5300,false));
    }
    @Test public void manualPauseOrFocusLossCancelsRecovery(){
        PlaybackRecovery r=new PlaybackRecovery();int t=r.begin(1000,true);r.cancel();assertFalse(r.takeAttempt(t,1300,false));
    }
    @Test public void callbacksFromAnOlderTransitionCannotResumePlayback(){
        PlaybackRecovery r=new PlaybackRecovery();int old=r.begin(1000,true),current=r.begin(1200,true);
        assertFalse(r.takeAttempt(old,1400,false));assertTrue(r.takeAttempt(current,1400,false));
    }
}
