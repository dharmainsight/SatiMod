package jp.flowview.android;
import org.junit.Test;
import static org.junit.Assert.*;

public class PresentationStateTest {
    @Test public void browserExitDuringPipDoesNotEraseFullscreenOrRestore(){
        PresentationState s=new PresentationState();s.pageFullscreen(true);s.beginPip(true);
        s.pageFullscreen(false);assertTrue(s.fullscreen());assertEquals("pip",s.pageMode());
        s.enteredPip();s.pageFullscreen(false);assertTrue(s.videoOnly());
        s.leftPip();s.pageFullscreen(false);assertTrue(s.fullscreen());assertEquals("full",s.pageMode());
        s.restored("full");s.pageFullscreen(false);assertFalse(s.fullscreen());
    }
    @Test public void manualPipFromPortraitReturnsToPortrait(){
        PresentationState s=new PresentationState();s.beginPip(false);s.pageFullscreen(true);s.enteredPip();
        s.leftPip();assertFalse(s.fullscreen());assertEquals("inline",s.pageMode());assertFalse(s.videoOnly());
    }
    @Test public void duplicateCallbacksPreserveOriginalReturnMode(){
        PresentationState s=new PresentationState();s.pageFullscreen(true);s.beginPip(true);s.beginPip(false);
        s.enteredPip();s.beginPip(false);s.leftPip();assertTrue(s.fullscreen());
        s.restored("full");s.pageFullscreen(true);s.pageFullscreen(true);assertTrue(s.fullscreen());
    }
    @Test public void explicitExitOverridesPendingRestoration(){
        PresentationState s=new PresentationState();s.pageFullscreen(true);s.beginPip(true);s.enteredPip();s.leftPip();
        s.requestFullscreen(false);assertEquals("inline",s.pageMode());assertFalse(s.protectedTransition());
    }
    @Test public void duplicatePipExitCannotUndoAUserExitOrNewFullscreen(){
        PresentationState s=new PresentationState();s.requestFullscreen(true);s.beginPip(true);s.enteredPip();s.leftPip();
        s.requestFullscreen(false);s.leftPip();assertFalse(s.fullscreen());
        s.requestFullscreen(true);s.leftPip();assertTrue(s.fullscreen());
    }
    @Test public void explicitPortraitCancelsAnUnfinishedPipPreparation(){
        PresentationState s=new PresentationState();s.requestFullscreen(true);s.beginPip(true);
        s.requestFullscreen(false);assertFalse(s.inPipOrEntering());assertEquals("inline",s.pageMode());
        s.leftPip();assertFalse(s.fullscreen());
    }
    @Test public void cancelledManualPipReturnsToPortraitDespiteBrowserNotifications(){
        PresentationState s=new PresentationState();s.beginPip(false);s.pageFullscreen(true);s.leftPip();
        assertEquals("inline",s.pageMode());assertFalse(s.fullscreen());assertFalse(s.protectedTransition());
    }
}
